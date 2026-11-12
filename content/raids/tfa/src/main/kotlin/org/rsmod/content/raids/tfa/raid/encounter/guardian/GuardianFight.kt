package org.rsmod.content.raids.tfa.raid.encounter.guardian

import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.adoptOwnedNpc
import org.rsmod.api.bosses.runtime.runAbility
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.table.TfaGuardianScalingRow
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.TfaRoomCleared
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.BATTERY_HIT
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.BATTERY_HP
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.ENRAGED
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.GROUND_MAX
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.HEALTH
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.LANDINGS
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.SHIELD_MAX
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * One Archive Guardian room: the boss, its four batteries and everything the fight keeps between
 * ticks. Its handlers register on a [BossExtensionRegistry] bound to the boss, so the shared spec
 * reaches this room's state and no other's.
 */
internal class GuardianFight(
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

    val batteries = mutableListOf<Npc>()
    val breaking = HashSet<Npc>()

    private val barOpened = HashSet<Player>()
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun contains(tile: CoordGrid): Boolean = GuardianArena.contains(origin, tile)

    fun fighters(): List<Player> = GuardianArena.players(origin, deps.playerList)

    fun open(): List<Npc> {
        scope.register("guardian.bar") { refreshBar() }
        scope.register("guardian.runlock") { it.target.runEnergy = 0 }
        scope.register("guardian.complete") { complete() }
        scope.register("guardian.dead") {
            boss.mode = NpcMode.None
            boss.clearInteraction()
        }

        val npc = Npc(START, origin)
        npc.respawnDir = Direction.South
        npc.moveRestrict = MoveRestrict.NoMove
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        registry.bind(npc, scope)
        boss = npc

        for ((dx, dz) in GuardianArena.BATTERY_OFFSETS) {
            val battery = Npc(BATTERY_INACTIVE, origin.translate(dx, dz))
            battery.moveRestrict = MoveRestrict.NoMove
            battery.ignoreCombatInteractions = true
            deps.npcRepo.add(battery, Int.MAX_VALUE)
            deps.adoptOwnedNpc(npc, battery)
            batteries += battery
        }
        scheduleIdleSpot()
        return listOf(npc) + batteries
    }

    fun dispose() {
        registry.unbind(boss)
        if (boss.isSlotAssigned) deps.npcRepo.del(boss, Int.MAX_VALUE)
        for (battery in batteries) if (battery.isSlotAssigned) deps.npcRepo.del(battery, Int.MAX_VALUE)
    }

    private fun scheduleIdleSpot() {
        deps.worldQueues.add(IDLE_SPOT_INTERVAL) {
            if (started || !boss.isSlotAssigned) return@add
            boss.spotanim("spotanim.vfx_guardian_spawn_idle_spotanim01")
            scheduleIdleSpot()
        }
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        started = true
        startedAt = deps.mapClock.cycle
        val size = session.occupants.size.coerceIn(MIN_SCALING, MAX_SCALING)
        val scaling = TfaGuardianScalingRow.getRow("dbrow.tfa_guardian_scaling_$size")
        boss.vars[SHIELD_MAX] = scaling.shield
        boss.vars[GROUND_MAX] = scaling.ground
        boss.vars[HEALTH] = scaling.ground
        boss.vars[BATTERY_HP] = scaling.battery
        boss.vars[BATTERY_HIT] = scaling.shield * BATTERY_SHIELD_PERCENT / 100
        boss.vars[LANDINGS] = 0
        boss.vars[ENRAGED] = 0

        boss.apRangeOverride = GuardianArena.REACH
        boss.apRequiresLineOfSight = false
        boss.apPlayer2(player, interactions)
        deps.runAbility(boss, player, "wake")
        player.mes("Challenge started: Archive Guardian.")
    }

    fun refreshBar() {
        for (player in fighters()) {
            if (barOpened.add(player)) hpBar.onOpen(player, boss) else hpBar.onRecolour(player, boss)
            pushHp(player)
        }
    }

    fun updateBar() {
        for (player in fighters()) pushHp(player)
    }

    private fun pushHp(player: Player) {
        val depleted = boss.hitpoints <= 1
        hpBar.onUpdate(player, boss, if (depleted) 0 else boss.hitpoints, boss.baseHitpointsLvl)
    }

    fun forceSpecial(player: Player): Boolean {
        if (!started || !boss.isSlotAssigned) return false
        deps.runAbility(boss, player, "special")
        return true
    }

    fun setHealthPercent(percent: Int): Boolean {
        if (!started || !boss.isSlotAssigned) return false
        boss.hitpoints = maxOf(1, (boss.baseHitpointsLvl.toLong() * percent / 100).toInt())
        updateBar()
        return true
    }

    private fun complete() {
        if (finished) return
        val now = deps.mapClock.cycle
        endedAt = now
        val fighters = fighters()
        for (fighter in fighters) {
            val precise = fighter.vars["varbit.option_precise_timing"] != 0
            val duration = formatInstanceKillTime(now - startedAt, precise)
            fighter.mes("Challenge complete: Archive Guardian. Duration: <col=ef1020>$duration</col>")
        }
        playVictorySong(VICTORY_SONG_TICKS)
        deps.worldQueues.add(HUD_FADE_DELAY) {
            for (fighter in fighters) hpBar.fadeOut(fighter)
            deps.worldQueues.add(HUD_HIDE_DELAY) { fighters.forEach(hpBar::onDeathClose) }
        }
        events.publish(TfaRoomCleared(session.id, "guardian"))
        deps.worldQueues.add(ALLIANCE_DELAY) { spawnAlliance() }
    }

    private fun playVictorySong(remaining: Int) {
        for (fighter in fighters()) fighter.midiSong(VICTORY_SONG, fadeOutSpeed = VICTORY_FADE_OUT)
        if (remaining > 1) deps.worldQueues.add(1) { playVictorySong(remaining - 1) }
    }

    private fun spawnAlliance() {
        if (!boss.isSlotAssigned) return
        for ((index, type) in ALLIANCE.withIndex()) {
            val dx = ALLIANCE_DX + index % ALLIANCE_COLUMNS
            val dz = ALLIANCE_DZ + index / ALLIANCE_COLUMNS
            val npc = Npc(type, origin.translate(dx, dz))
            deps.npcRepo.add(npc, ALLIANCE_LIFETIME)
            npc.walk(origin.translate(dx, ALLIANCE_WALK_DZ))
        }
    }

    private companion object {
        const val START = "npc.tfa_guardian_start"
        const val BATTERY_INACTIVE = "npc.tfa_guardian_battery_inactive"
        const val MIN_SCALING = 2
        const val MAX_SCALING = 8
        const val BATTERY_SHIELD_PERCENT = 40
        const val IDLE_SPOT_INTERVAL = 3
        const val VICTORY_SONG = 147
        const val VICTORY_SONG_TICKS = 18
        const val VICTORY_FADE_OUT = 50
        val ALLIANCE =
            listOf(
                "npc.armacus_selles_postguardian",
                "npc.alliance_guardian_of_armadyl_3",
                "npc.alliance_guardian_of_armadyl_4",
                "npc.alliance_guardian_of_armadyl_5",
                "npc.alliance_guardian_of_armadyl_6",
            )
        const val ALLIANCE_DELAY = 8
        const val ALLIANCE_LIFETIME = 600
        const val ALLIANCE_DX = 1
        const val ALLIANCE_DZ = -24
        const val ALLIANCE_COLUMNS = 3
        const val ALLIANCE_WALK_DZ = -10
        const val HUD_FADE_DELAY = 11
        const val HUD_HIDE_DELAY = 4
    }
}
