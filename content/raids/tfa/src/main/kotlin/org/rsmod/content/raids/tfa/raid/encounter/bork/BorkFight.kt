package org.rsmod.content.raids.tfa.raid.encounter.bork

import dev.openrune.types.MoveRestrict
import java.awt.Color
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
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.setColour
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.table.TfaBorkScalingRow
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.TfaRoomCleared
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.ARMOUR
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.MAGIC_ARMOUR
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.SMORK_DEAD
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.SMORK_MAX
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.SMORK_PCT
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.STOMP_SIDE
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.TEMPO
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * One Bork room: Bork, the Smorks (owned by his encounter) and the state the fight keeps between
 * ticks. Handlers register on a scoped [BossExtensionRegistry] so the shared spec reaches this
 * room only.
 */
internal class BorkFight(
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

    lateinit var boss: Npc
        private set

    lateinit var smork: Npc
        private set

    private lateinit var handler: Npc

    private val barOpened = HashSet<Player>()
    private var barClosed = false
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun contains(tile: CoordGrid): Boolean = BorkArena.inRoom(origin, tile)

    fun fighters(): List<Player> = BorkArena.players(origin, deps.playerList)

    fun arenaEntry(): CoordGrid = origin.translate(ARENA_ENTRY_DX, ARENA_ENTRY_DZ)

    fun isSmork(npc: Npc): Boolean = ::smork.isInitialized && smork === npc

    fun smorkDead(): Boolean = boss.vars[SMORK_DEAD] == 1

    fun open(): List<Npc> {
        scope.register("bork.bar") { refreshBar() }
        scope.register("bork.hp") { updateBar() }
        scope.register("bork.complete") { complete() }
        scope.register("bork.ignite") { ignite(it.target) }
        scope.register("bork.stomp_side") { stompSide() }
        scope.register("bork.smash") { boss.say("Bork smash ${it.target.displayName} now.") }

        val bork = Npc(BOSS, origin)
        bork.respawnDir = Direction.West
        bork.moveRestrict = MoveRestrict.NoMove
        deps.npcRepo.add(bork, Int.MAX_VALUE)
        registry.bind(bork, scope)
        boss = bork

        val tower = Npc(SMORK, origin.translate(BorkArena.SMORK_DX, BorkArena.SMORK_DZ))
        tower.respawnDir = Direction.East
        tower.moveRestrict = MoveRestrict.NoMove
        tower.ignoreCombatInteractions = true
        deps.npcRepo.add(tower, Int.MAX_VALUE)
        deps.adoptOwnedNpc(bork, tower)
        smork = tower

        val holder = Npc(HANDLER, origin.translate(BorkArena.HANDLER_DX, BorkArena.HANDLER_DZ))
        holder.moveRestrict = MoveRestrict.NoMove
        holder.ignoreCombatInteractions = true
        deps.npcRepo.add(holder, Int.MAX_VALUE)
        deps.adoptOwnedNpc(bork, holder)
        handler = holder

        for (dx in 0 until BorkArena.TOWER_SIZE) {
            for (dz in 0 until BorkArena.TOWER_SIZE) {
                val tile =
                    origin.translate(BorkArena.TOWER_SW_DX + dx, BorkArena.TOWER_SW_DZ + dz)
                deps.spawnOwnedLoc(bork, tile, BLOCKER, 0, false)
            }
        }
        return listOf(bork, tower, holder)
    }

    fun dispose() {
        registry.unbind(boss)
        for (npc in listOf(boss, smork, handler)) {
            if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        started = true
        startedAt = deps.mapClock.cycle
        val size = session.occupants.size.coerceIn(MIN_SCALING, MAX_SCALING)
        val scaling = TfaBorkScalingRow.getRow("dbrow.tfa_bork_scaling_$size")
        boss.vars[HP_MAX] = scaling.bork
        boss.vars[SMORK_MAX] = scaling.smork
        boss.vars[SMORK_PCT] = FULL_PERCENT
        boss.moveRestrict = boss.type.moveRestrict
        boss.apRangeOverride = BorkArena.REACH
        boss.apRequiresLineOfSight = false
        boss.apPlayer2(player, interactions)
        deps.runAbility(boss, player, "begin")
        player.mes("Challenge started: Bork.")
    }

    fun ballTarget(): CoordGrid? {
        val focus = deps.encounterRegistry.of(boss).focus
        val others = fighters().filter { it !== focus }
        return (others.ifEmpty { fighters() }).randomOrNull()?.coords
    }

    private fun stompSide() {
        val nearest = fighters().minByOrNull { BorkArena.distance(boss, it.coords) } ?: return
        boss.vars[STOMP_SIDE] = BorkArena.side(boss, nearest.coords)
    }

    fun onSmorkHit() {
        val max = boss.vars[SMORK_MAX].coerceAtLeast(1)
        val pct = if (smork.hitpoints <= 1) 0 else (smork.hitpoints * FULL_PERCENT + max - 1) / max
        boss.vars[SMORK_PCT] = pct
        boss.vars[ARMOUR] = BorkArena.armour(pct)
        boss.vars[MAGIC_ARMOUR] = BorkArena.magicArmour(pct)
        updateBar()
    }

    fun refreshBar() {
        for (player in fighters()) {
            if (barOpened.add(player)) hpBar.onOpen(player, handler)
            player.hudExtraBase = smork.baseHitpointsLvl
            player.hudExtraCurrent = smorkHp()
            player.hudShowExtraBar = 1
            hpBar.onRefresh(player, handler) { applyExtraColours() }
            pushHp(player)
        }
    }

    fun updateBar() {
        if (barClosed) return
        for (player in fighters()) {
            player.hudExtraCurrent = smorkHp()
            hpBar.onRefresh(player, handler) { applyExtraColours() }
            pushHp(player)
        }
    }

    private fun smorkHp(): Int = if (smork.hitpoints <= 1) 0 else smork.hitpoints

    private fun pushHp(player: Player) {
        val hp = if (boss.hitpoints <= 1) 0 else boss.hitpoints
        hpBar.onUpdate(player, handler, hp, boss.baseHitpointsLvl)
    }

    private fun closeBar() {
        barClosed = true
        for (player in fighters()) {
            player.hudShowExtraBar = 0
            player.hudExtraBase = 0
            player.hudExtraCurrent = 0
            hpBar.onDeathClose(player)
        }
    }

    fun setSmorkPercent(percent: Int): Boolean {
        if (!started || !smork.isSlotAssigned) return false
        smork.hitpoints = maxOf(1, (smork.baseHitpointsLvl.toLong() * percent / FULL_PERCENT).toInt())
        onSmorkHit()
        return true
    }

    fun setBorkPercent(percent: Int): Boolean {
        if (!started || !boss.isSlotAssigned) return false
        boss.hitpoints = maxOf(1, (boss.baseHitpointsLvl.toLong() * percent / FULL_PERCENT).toInt())
        updateBar()
        return true
    }

    fun debugState(): List<String> {
        val encounter = deps.encounterRegistry.of(boss)
        return listOf(
            "[debug] started=$started bork=${boss.coords} hp=${boss.hitpoints}/${boss.baseHitpointsLvl} " +
                "phase=${encounter.currentPhaseName}",
            "[debug] smork hp=${smork.hitpoints}/${smork.baseHitpointsLvl} " +
                "pct=${boss.vars[SMORK_PCT]} tempo=${boss.vars[TEMPO]} armour=${boss.vars[ARMOUR]}",
        )
    }

    fun setTempo(tempo: Int) {
        boss.vars[TEMPO] = tempo.coerceIn(0, MAX_TEMPO)
    }

    fun run(player: Player, ability: String) {
        deps.runAbility(boss, player, ability)
    }

    private fun ignite(player: Player) {
        val wasBurning = player.burn > 0
        player.burn = BURN_HITS
        if (!wasBurning) scheduleBurn(player)
    }

    private fun scheduleBurn(player: Player) {
        deps.worldQueues.add(BURN_PERIOD) {
            val left = player.burn
            if (left <= 0 || finished || !boss.isSlotAssigned || !contains(player.coords)) {
                player.burn = 0
                return@add
            }
            val damage = if (left == 1) BURN_LAST else BorkArena.BURN_HIT
            player.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
            player.burn = left - 1
            if (left > 1) scheduleBurn(player) else player.burn = 0
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
            fighter.mes("Challenge complete: Bork. Duration: <col=ef1020>$duration</col>")
        }
        closeBar()
        playVictorySong()
        events.publish(TfaRoomCleared(session.id, "bork"))
    }

    private fun playVictorySong() {
        if (!boss.isSlotAssigned) return
        for (fighter in fighters()) fighter.midiSong(VICTORY_SONG, fadeOutSpeed = VICTORY_FADE_OUT)
        deps.worldQueues.add(1) { playVictorySong() }
    }

    private fun Player.applyExtraColours() {
        setColour("component.hpbar_hud:health_bar_back_extra", EXTRA_BACK)
        setColour("component.hpbar_hud:health_bar_sliding_extra", EXTRA_SLIDING)
        setColour("component.hpbar_hud:health_bar_remaining_extra", EXTRA_REMAINING)
    }

    private companion object {
        const val BOSS = "npc.tfa_bork"
        const val SMORK = "npc.tfa_smork"
        const val HANDLER = "npc.tfa_bork_smork_hp_ui_handler"
        const val BLOCKER = "loc.invisible_type8_blockrange"
        const val MIN_SCALING = 2
        const val MAX_SCALING = 8
        const val MAX_TEMPO = 3
        const val FULL_PERCENT = 100
        const val ARENA_ENTRY_DX = -10
        const val ARENA_ENTRY_DZ = -4
        const val BURN_HITS = 5
        const val BURN_PERIOD = 4
        const val BURN_LAST = 1
        const val VICTORY_SONG = 147
        const val VICTORY_FADE_OUT = 50

        val EXTRA_BACK = Color(72, 24, 0)
        val EXTRA_SLIDING = Color(176, 64, 0)
        val EXTRA_REMAINING = Color(240, 96, 0)

        var Player.hudExtraBase by intVarBit("varbit.hpbar_hud_extra_base")
        var Player.hudExtraCurrent by intVarBit("varbit.hpbar_hud_extra_current")
        var Player.hudShowExtraBar by intVarp("varp.hpbar_hud_show_extra_bar")
        var Player.burn by intVarp("varp.bork_burn")
    }
}
