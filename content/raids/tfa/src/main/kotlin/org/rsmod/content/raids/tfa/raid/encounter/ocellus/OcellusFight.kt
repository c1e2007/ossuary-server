package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.reward.TfaVault
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * One Ocellus room: everything the fight keeps between ticks. Its handlers register on a
 * [BossExtensionRegistry] bound to the boss, so the shared spec reaches this room's state and no
 * other's.
 */
internal class OcellusFight(
    override val session: InstanceSession,
    val origin: CoordGrid,
    private val sharedDeps: BossDeps,
    private val meter: OcellusMeter,
    private val hpBar: BossHpBarScript,
    private val interactions: AiPlayerInteractions,
    energy: SpecialAttackEnergy,
    private val vault: TfaVault,
) : TfaFight {
    private val scope = BossExtensionRegistry()
    private val deps = sharedDeps.withExtensions(scope)

    val centre: CoordGrid = origin.translate(CENTRE_DX, CENTRE_DZ)
    val phaseThreeSpot: CoordGrid = centre.translate(-PHASE_THREE_OFFSET, -PHASE_THREE_OFFSET)

    val phaseTwo = OcellusPhaseTwo(deps, hpBar, meter, interactions, energy) { partySize }

    lateinit var boss: Npc
        private set

    var dying = false
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null
    private var partySize = 1

    fun contains(tile: CoordGrid): Boolean = OcellusArena.contains(origin, tile)

    fun open(): Npc {
        phaseTwo.register()
        val npc = Npc("npc.tfa_ocellus_intro", origin)
        npc.respawnDir = Direction.North
        npc.moveRestrict = MoveRestrict.NoMove
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        sharedDeps.extensionRegistry.bind(npc, scope)
        boss = npc
        phaseTwo.placeCensers(centre)
        return npc
    }

    fun dispose() {
        phaseTwo.clear()
        sharedDeps.extensionRegistry.unbind(boss)
        if (boss.isSlotAssigned) deps.npcRepo.del(boss, Int.MAX_VALUE)
    }

    fun lightCenser(at: BoundLocInfo): String? {
        if (!started || !boss.isSlotAssigned) return OcellusCensers.NOTHING
        return phaseTwo.lightCenser(boss, at)
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        start(boss, player, partySize = session.occupants.size.coerceAtLeast(1))
        player.mes("Challenge started: Ocellus.")
    }

    fun startAtPhaseThree(player: Player): Boolean {
        ready(player)
        if (!started || !boss.isSlotAssigned) return false
        boss.vars[OcellusVarns.THRESHOLD] = OcellusVarns.SKIP_TO_PHASE_THREE
        return true
    }

    fun setHealthPercent(percent: Int): Boolean {
        if (!started || !boss.isSlotAssigned) return false
        boss.hitpoints = maxOf(1, (boss.baseHitpointsLvl.toLong() * percent / 100).toInt())
        for (fighter in OcellusArena.players(boss, deps.playerList)) hpBar.onUpdate(fighter, boss)
        return true
    }

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun complete(npc: Npc) {
        endedAt = deps.mapClock.cycle
        val ticks = deps.mapClock.cycle - startedAt
        phaseTwo.stopHazards()
        phaseTwo.playVictorySong(npc)
        val fighters = OcellusArena.players(npc, deps.playerList)
        vault.begin(fighters, npc.spawnCoords.translate(BLADE_DX, BLADE_DZ))
        for (fighter in fighters) {
            val precise = fighter.vars["varbit.option_precise_timing"] != 0
            val duration = formatInstanceKillTime(ticks, precise)
            fighter.mes("Challenge complete: Ocellus. Duration: <col=ef1020>$duration</col>")
        }
        deps.worldQueues.add(HUD_FADE_DELAY) {
            for (fighter in fighters) hpBar.fadeOut(fighter)
            deps.worldQueues.add(HUD_HIDE_DELAY) { fighters.forEach(hpBar::onDeathClose) }
        }
    }

    private fun start(npc: Npc, player: Player, partySize: Int) {
        started = true
        startedAt = deps.mapClock.cycle
        this.partySize = partySize
        phaseTwo.resetCharges(OcellusArena.players(npc, deps.playerList))
        val shield = ServerCacheManager.getNpc("npc.tfa_ocellus_p1".asRSCM(RSCMType.NPC))!!
        npc.transmog(shield, Int.MAX_VALUE)
        npc.copyStats(shield)
        npc.baseHitpointsLvl = SHIELD_HP_BASE + SHIELD_HP_PER_PLAYER * partySize
        npc.hitpoints = npc.baseHitpointsLvl
        npc.moveRestrict = shield.moveRestrict
        npc.say("Now... ascension...")
        for (fighter in OcellusArena.players(npc, deps.playerList)) hpBar.onOpen(fighter, npc)
        meter.show(npc)
        npc.apPlayer2(player, interactions)
        npc.setHuntMode(checkNotNull(ServerCacheManager.getHunt(HUNT_CONSTANT_RANGED)))
        npc.setHunt(shield.huntRange)
    }

    private companion object {
        const val CENTRE_DX = 1
        const val CENTRE_DZ = 4
        const val PHASE_THREE_OFFSET = 4
        const val BLADE_DX = -6
        const val BLADE_DZ = 9
        const val HUD_FADE_DELAY = 10
        const val HUD_HIDE_DELAY = 4
        const val HUNT_CONSTANT_RANGED = 2
        const val SHIELD_HP_BASE = 480
        const val SHIELD_HP_PER_PLAYER = 1050
    }
}
