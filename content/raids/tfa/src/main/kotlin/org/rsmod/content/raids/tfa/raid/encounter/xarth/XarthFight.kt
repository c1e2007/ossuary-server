package org.rsmod.content.raids.tfa.raid.encounter.xarth

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.adoptOwnedNpc
import org.rsmod.api.bosses.runtime.runAbility
import org.rsmod.api.bosses.runtime.spawnOwnedLoc
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.table.TfaXarthScalingRow
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.TfaRoomCleared
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.FIRST_HEAP
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.HEALED
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.HEAPS
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.MAGICS
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.SLOT
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.WAVE
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * One Xarth room: Xarth, the three rubble heaps (owned by his encounter) and the state the fight
 * keeps between ticks. Handlers register on a scoped [BossExtensionRegistry] so the shared spec
 * reaches this room only.
 */
internal class XarthFight(
    override val session: InstanceSession,
    val origin: CoordGrid,
    sharedDeps: BossDeps,
    private val hpBar: BossHpBarScript,
    private val interactions: AiPlayerInteractions,
    private val events: EventBus,
) : TfaFight {
    private val scope = BossExtensionRegistry()
    private val deps = sharedDeps.withExtensions(scope)
    private val registry = sharedDeps.extensionRegistry
    private val status = XarthStatus(deps, { boss }, ::inFight)

    lateinit var boss: Npc
        private set

    private val heaps = ArrayList<Npc>()
    private val barOpened = HashSet<Player>()
    private var barClosed = false
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun contains(tile: CoordGrid): Boolean = XarthArena.inRoom(origin, tile)

    fun fighters(): List<Player> = XarthArena.players(origin, deps.playerList)

    fun arenaEntry(): CoordGrid = origin.translate(ARENA_ENTRY_DX, ARENA_ENTRY_DZ)

    private fun inFight(player: Player): Boolean =
        !finished && boss.isSlotAssigned && contains(player.coords)

    fun open(): List<Npc> {
        scope.register("xarth.bar") { refreshBar() }
        scope.register("xarth.bar_close") { closeBar() }
        scope.register("xarth.complete") { complete() }
        scope.register("xarth.ignite") { status.ignite(it.target, 1) }
        scope.register("xarth.ignite_wave") { status.ignite(it.target, XarthArena.BURN_WAVE_STACK) }
        scope.register("xarth.bleed") { status.bleed(it.target) }
        scope.register("xarth.bind") {
            it.target.spotanim(BIND_SPOTANIM)
            status.bind(it.target)
        }
        scope.register("xarth.piles") { burnPiles() }

        val xarth = Npc(BOSS, origin)
        xarth.respawnDir = Direction.West
        xarth.moveRestrict = MoveRestrict.NoMove
        deps.npcRepo.add(xarth, Int.MAX_VALUE)
        registry.bind(xarth, scope)
        boss = xarth

        for (tile in XarthArena.footprintTiles(origin)) {
            deps.spawnOwnedLoc(xarth, tile, BLOCKER, 0, false)
        }
        val npcs = ArrayList<Npc>()
        npcs += xarth
        for (heap in 0 until XarthArena.HEAP_COUNT) {
            val sw = XarthArena.heapSw(origin, heap)
            val rubble = Npc(RUBBLE, sw)
            rubble.respawnDir = Direction.South
            rubble.moveRestrict = MoveRestrict.NoMove
            rubble.ignoreCombatInteractions = true
            deps.npcRepo.add(rubble, Int.MAX_VALUE)
            deps.adoptOwnedNpc(xarth, rubble)
            heaps += rubble
            npcs += rubble
            deps.spawnOwnedLoc(xarth, sw, HEAP_LOCS[heap], 0, false)
        }
        return npcs
    }

    fun dispose() {
        registry.unbind(boss)
        for (npc in heaps + boss) {
            if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        started = true
        startedAt = deps.mapClock.cycle
        val size = session.occupants.size.coerceIn(MIN_SCALING, MAX_SCALING)
        boss.vars[HP_MAX] = TfaXarthScalingRow.getRow("dbrow.tfa_xarth_scaling_$size").xarth
        boss.vars[MAGICS] = XarthArena.magicsFor(size)
        boss.moveRestrict = boss.type.moveRestrict
        boss.apRangeOverride = XarthArena.REACH
        boss.apRequiresLineOfSight = false
        boss.apPlayer2(player, interactions)
        deps.runAbility(boss, player, "begin")
        player.mes("Challenge started: Xarth.")
        heartbeat()
    }

    /** Spec timers and the attack selector only tick against a valid engaged player. */
    private fun heartbeat() {
        deps.worldQueues.add(1) {
            if (finished || !boss.isSlotAssigned) return@add
            val engaged = (boss.interaction as? InteractionPlayer)?.target
            val usable = engaged != null && engaged.isValidTarget() && contains(engaged.coords)
            if (!usable) {
                fighters()
                    .minByOrNull { XarthArena.footprintDistance(origin, it.coords) }
                    ?.let { boss.apPlayer2(it, interactions) }
            }
            heartbeat()
        }
    }

    fun run(player: Player, ability: String) {
        deps.runAbility(boss, player, ability)
    }

    fun refreshBar() {
        if (barClosed) return
        for (player in fighters()) {
            if (barOpened.add(player)) hpBar.onOpen(player, boss)
            pushHp(player)
        }
    }

    fun updateBar() {
        if (barClosed) return
        for (player in fighters()) pushHp(player)
    }

    private fun pushHp(player: Player) {
        val hp = if (boss.hitpoints <= 1) 0 else boss.hitpoints
        hpBar.onUpdate(player, boss, hp, boss.baseHitpointsLvl)
    }

    fun closeBar() {
        if (barClosed) return
        barClosed = true
        fighters().forEach(hpBar::onDeathClose)
    }

    fun setHealthPercent(percent: Int): Boolean {
        if (!started || !boss.isSlotAssigned) return false
        boss.hitpoints = maxOf(1, (boss.baseHitpointsLvl.toLong() * percent / FULL_PERCENT).toInt())
        updateBar()
        return true
    }

    fun forceWave(wave: Int): Boolean {
        if (!started || !boss.isSlotAssigned || wave !in 1..WAVES) return false
        val first = boss.vars[FIRST_HEAP]
        boss.vars[WAVE] = wave - 1
        boss.vars[HEAPS] =
            (0 until wave - 1).fold(0) { mask, done ->
                mask or (1 shl XarthArena.heapForWave(done, first))
            }
        return true
    }

    fun addHealed(amount: Int) {
        boss.vars[HEALED] = boss.vars[HEALED] + amount
    }

    fun debugState(): List<String> {
        val encounter = deps.encounterRegistry.of(boss)
        return listOf(
            "[debug] started=$started origin=$origin phase=${encounter.currentPhaseName} " +
                "hp=${boss.hitpoints}/${boss.baseHitpointsLvl}",
            "[debug] wave=${boss.vars[WAVE]} heaps=${boss.vars[HEAPS]} slot=${boss.vars[SLOT]} " +
                "magics=${boss.vars[MAGICS]} healed=${boss.vars[HEALED]}",
        )
    }

    private fun burnPiles() {
        for ((index, offset) in PILE_OFFSETS.withIndex()) {
            val tile = origin.translate(offset.first, offset.second)
            val pile = "loc.tfa_xarth01_corpse_pile0${index + 1}".asRSCM(RSCMType.LOC)
            val fire = "loc.tfa_xarth01_corpse_pile_fire0${index + 1}".asRSCM(RSCMType.LOC)
            val info = deps.locRepo.findAll(tile).firstOrNull { it.id == pile } ?: continue
            val fireType = ServerCacheManager.getObject(fire) ?: continue
            deps.locRepo.change(info, fireType, Int.MAX_VALUE)
        }
    }

    private fun complete() {
        if (finished) return
        val now = deps.mapClock.cycle
        endedAt = now
        boss.vars[DONE] = 1
        for (fighter in fighters()) {
            val precise = fighter.vars["varbit.option_precise_timing"] != 0
            val duration = formatInstanceKillTime(now - startedAt, precise)
            fighter.mes("Challenge complete: Xarth. Duration: <col=ef1020>$duration</col>")
        }
        closeBar()
        playVictorySong()
        events.publish(TfaRoomCleared(session.id, "xarth"))
    }

    private fun playVictorySong() {
        if (!boss.isSlotAssigned) return
        for (fighter in fighters()) fighter.midiSong(VICTORY_SONG, fadeOutSpeed = VICTORY_FADE_OUT)
        deps.worldQueues.add(1) { playVictorySong() }
    }

    private companion object {
        const val BOSS = "npc.tfa_xarth"
        const val RUBBLE = "npc.tfa_xarth_rubble_npc"
        const val BLOCKER = "loc.invisible_type8_blocking"
        const val BIND_SPOTANIM = "spotanim.vfx_xarth_aoe_stunanim_01"
        const val MIN_SCALING = 2
        const val MAX_SCALING = 8
        const val WAVES = 3
        const val FULL_PERCENT = 100
        const val ARENA_ENTRY_DX = -6
        const val ARENA_ENTRY_DZ = 1
        const val VICTORY_SONG = 147
        const val VICTORY_FADE_OUT = 50

        val HEAP_LOCS =
            arrayOf("loc.tfa_xarth_rubble_1", "loc.tfa_xarth_rubble_2", "loc.tfa_xarth_rubble_1")

        val PILE_OFFSETS =
            listOf(
                1 to 12,
                10 to 2,
                1 to -9,
                -10 to 2,
                -10 to 9,
                10 to 9,
                10 to -11,
                -11 to -10,
            )
    }
}
