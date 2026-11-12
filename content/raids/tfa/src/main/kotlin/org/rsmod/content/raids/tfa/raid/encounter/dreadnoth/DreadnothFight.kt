package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.runAbility
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.table.TfaDreadnothScalingRow
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.TfaRoomCleared
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.WAVES_DONE
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid

/**
 * One Dreadnoth room: the boss and everything the fight keeps between ticks. Its handlers register
 * on a [BossExtensionRegistry] bound to the boss, so the shared spec reaches this room's state and
 * no other's.
 */
internal class DreadnothFight(
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

    private val barOpened = HashSet<Player>()
    private var barClosed = false
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun contains(tile: CoordGrid): Boolean = DreadnothArena.inRoom(origin, tile)

    fun fighters(): List<Player> = DreadnothArena.players(origin, deps.playerList)

    fun arenaEntry(): CoordGrid = origin.translate(ARENA_ENTRY_DX, ARENA_ENTRY_DZ)

    fun open(): List<Npc> {
        scope.register("dreadnoth.bar") { refreshBar() }
        scope.register("dreadnoth.bar_fade") { fighters().forEach(hpBar::fadeOut) }
        scope.register("dreadnoth.bar_close") { closeBar() }
        scope.register("dreadnoth.complete") { complete() }

        val npc = Npc(BOSS, origin)
        npc.respawnDir = Direction.West
        npc.moveRestrict = MoveRestrict.NoMove
        npc.setHunt(0)
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        registry.bind(npc, scope)
        boss = npc
        return listOf(npc)
    }

    fun dispose() {
        registry.unbind(boss)
        if (boss.isSlotAssigned) deps.npcRepo.del(boss, Int.MAX_VALUE)
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        started = true
        startedAt = deps.mapClock.cycle
        val size = session.occupants.size.coerceIn(MIN_SCALING, MAX_SCALING)
        boss.vars[HP_MAX] = TfaDreadnothScalingRow.getRow("dbrow.tfa_dreadnoth_scaling_$size").hp
        boss.moveRestrict = boss.type.moveRestrict
        boss.defaultMoveSpeed = MoveSpeed.Crawl
        deps.runAbility(boss, player, "begin")
        player.mes("Challenge started: Dreadnoth the Jellied.")
        deps.worldQueues.add(ENGAGE_DELAY) { engage(player) }
    }

    private fun engage(player: Player) {
        if (!boss.isSlotAssigned) return
        boss.setHunt(boss.type.huntRange)
        boss.apRangeOverride = DreadnothArena.REACH
        boss.apRequiresLineOfSight = false
        boss.apPlayer2(player, interactions)
    }

    fun debugState(): List<String> {
        val encounter = deps.encounterRegistry.of(boss)
        return listOf(
            "[debug] started=$started coords=${boss.coords} origin=$origin phase=${encounter.currentPhaseName}",
            "[debug] moveRestrict=${boss.moveRestrict} movementLocked=${boss.movementLocked} " +
                "scripted=${boss.scriptedMovement} approach=${boss.approach} speed=${boss.defaultMoveSpeed}",
            "[debug] mode=${boss.mode} interaction=${boss.interaction != null} " +
                "huntMode=${boss.huntMode} huntRange=${boss.huntRange} hp=${boss.hitpoints}/${boss.baseHitpointsLvl}",
        )
    }

    fun hunt(jelly: Npc, delay: Int) {
        if (delay == SPLIT_HUNT_DELAY) {
            fighters().forEach {
                it.mes("[debug] split jelly at ${jelly.coords}, boss at ${boss.coords}")
            }
        }
        deps.worldQueues.add(maxOf(delay, 1)) {
            if (!jelly.isSlotAssigned || jelly.ignoreCombatInteractions) return@add
            jelly.setHuntMode(checkNotNull(ServerCacheManager.getHunt("stalk.tfa_jelly".asRSCM())))
            jelly.setHunt(JELLY_HUNT_RANGE)
        }
    }

    fun refreshBar() {
        for (player in fighters()) {
            if (barOpened.add(player)) hpBar.onOpen(player, boss)
            pushHp(player)
        }
    }

    fun updateBar() {
        if (barClosed) return
        for (player in fighters()) pushHp(player)
    }

    private fun closeBar() {
        barClosed = true
        fighters().forEach(hpBar::onDeathClose)
    }

    private fun pushHp(player: Player) {
        val depleted = boss.hitpoints <= 1
        hpBar.onUpdate(player, boss, if (depleted) 0 else boss.hitpoints, boss.baseHitpointsLvl)
    }

    fun setHealthPercent(percent: Int): Boolean {
        if (!started || !boss.isSlotAssigned) return false
        boss.hitpoints = maxOf(1, (boss.baseHitpointsLvl.toLong() * percent / 100).toInt())
        updateBar()
        return true
    }

    fun forceWave(wave: Int): Boolean {
        boss.vars[WAVES_DONE] = wave - 1
        return setHealthPercent(WAVE_PERCENTS[wave - 1] - 1)
    }

    fun forceEnrage(): Boolean {
        boss.vars[WAVES_DONE] = WAVE_PERCENTS.size
        return setHealthPercent(ENRAGE_PERCENT)
    }

    private fun complete() {
        if (finished) return
        val now = deps.mapClock.cycle
        endedAt = now
        boss.vars[DONE] = 1
        for (fighter in fighters()) {
            val precise = fighter.vars["varbit.option_precise_timing"] != 0
            val duration = formatInstanceKillTime(now - startedAt, precise)
            fighter.mes("Challenge complete: Dreadnoth the Jellied. Duration: <col=ef1020>$duration</col>")
        }
        playVictorySong()
        events.publish(TfaRoomCleared(session.id, "dreadnoth"))
    }

    private fun playVictorySong() {
        if (!boss.isSlotAssigned) return
        for (fighter in fighters()) fighter.midiSong(VICTORY_SONG, fadeOutSpeed = VICTORY_FADE_OUT)
        deps.worldQueues.add(1) { playVictorySong() }
    }

    private companion object {
        const val BOSS = "npc.tfa_jelly_boss"
        const val MIN_SCALING = 2
        const val MAX_SCALING = 8
        const val ENGAGE_DELAY = 3
        const val SPLIT_HUNT_DELAY = 1
        const val ARENA_ENTRY_DX = -5
        const val ARENA_ENTRY_DZ = 2
        const val JELLY_HUNT_RANGE = 40
        val WAVE_PERCENTS = listOf(85, 65, 45)
        const val ENRAGE_PERCENT = 25
        const val VICTORY_SONG = 147
        const val VICTORY_FADE_OUT = 50
    }
}
