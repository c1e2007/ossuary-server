package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.map.CoordGrid

@Singleton
internal class ColosseumRuns
@Inject
constructor(
    private val manager: InstanceManager,
    private val clock: MapClock,
    private val regions: RegionRegistry,
    private val npcRepo: NpcRepository,
    private val deps: BossDeps,
    private val aiInteractions: AiPlayerInteractions,
) {
    private val active = HashMap<InstanceId, ColosseumRun>()

    fun runFor(player: Player): ColosseumRun? =
        manager.sessionForPlayer(player)?.takeIf { it.key == KEY }?.let { active[it.id] }

    fun runFor(npc: Npc): ColosseumRun? = manager.instanceForNpc(npc)?.let { active[it] }

    suspend fun ProtectedAccess.enterArena(onEntered: ProtectedAccess.(ColosseumRun) -> Unit) {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return
        }
        val result = manager.create(player, KEY, spec(), InstanceAccess.Private, clock.cycle)
        when (result) {
            is InstanceManager.Result.Failed -> mes(result.reason)
            is InstanceManager.Result.Joined -> Unit
            is InstanceManager.Result.Created ->
                withInstanceEnterTransition(InstanceEnterTransition()) {
                    val region = regions[result.enter] ?: return@withInstanceEnterTransition
                    active.keys.removeIf { manager.sessionForId(it) == null }
                    val run = ColosseumRun(player, result.session, region.southWest)
                    active[result.session.id] = run
                    telejump(result.enter, TeleportType.Exempt)
                    manager.finalizeEntry(player, result.session, clock.cycle)
                    onEntered(run)
                }
        }
    }

    suspend fun ProtectedAccess.leaveArena() {
        val session = manager.sessionForPlayer(player) ?: return
        val run = active.remove(session.id)
        run?.stage = RunStage.ENDED
        withInstanceLeaveTransition(InstanceEnterTransition()) {
            manager.leave(player, session, clock.cycle)
            telejump(Colosseum.LOBBY_RESPAWN, TeleportType.Exempt)
        }
    }

    fun end(run: ColosseumRun) {
        if (run.stage == RunStage.ENDED) return
        run.stage = RunStage.ENDED
        active.remove(run.session.id)
        run.cleanup.forEach { it() }
        run.cleanup.clear()
        for (hazard in run.hazards.toList()) npcRepo.del(hazard, 0)
        run.hazards.clear()
        for (npc in run.npcs.toList()) npcRepo.del(npc, 0)
        run.npcs.clear()
        run.minimus?.let { npcRepo.del(it, 0) }
        run.sol?.let { npcRepo.del(it, 0) }
        run.player.colosseumSelected = 0
    }

    fun spawn(run: ColosseumRun, type: String, tile: CoordGrid, pinned: Boolean = false): Npc? {
        val npcType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return null
        val npc = Npc(npcType, tile)
        npcRepo.add(npc, Int.MAX_VALUE)
        // add() treats Int.MAX_VALUE as a permanent spawn and turns respawning on; arena npcs
        // must stay dead once killed.
        npc.respawns = false
        manager.attachNpc(run.session.id, npc)
        if (pinned) pin(npc)
        return npc
    }

    /** Runtime equivalent of the `moveRestrict = "NoMove"` cache config that ToA's static npcs use. */
    fun pin(npc: Npc) {
        npc.moveRestrict = MoveRestrict.NoMove
    }

    fun remove(npc: Npc) {
        npcRepo.del(npc, 0)
    }

    /**
     * Makes [npc] attack the run's player. The boss DSL runs a combat tick from the npc's ap-player2
     * trigger, so this is the call that produces a real attack.
     */
    fun engage(run: ColosseumRun, npc: Npc) {
        npc.apPlayer2(run.player, aiInteractions)
    }

    /** True when [npc] has lost its target, so it must be re-engaged. */
    fun needsTarget(run: ColosseumRun, npc: Npc): Boolean {
        val target = (npc.interaction as? InteractionPlayer)?.target
        return npc.mode != NpcMode.ApPlayer2 || target !== run.player || !run.player.isValidTarget()
    }

    fun tickLoop(run: ColosseumRun, onTick: (ColosseumRun) -> Unit) {
        if (run.ticking) return
        run.ticking = true
        deps.repeatTick(
            ticks = Int.MAX_VALUE,
            onTick = {
                if (run.stage == RunStage.ENDED) {
                    false
                } else {
                    onTick(run)
                    true
                }
            },
            onStop = { run.ticking = false },
        )
    }

    private fun spec(): InstanceSpec =
        InstanceSpec(
            fee = 0,
            maxPlayers = 1,
            reclaimTicks = 0,
            graceTicks = 0,
            destroyWhenEmpty = true,
            area =
                InstanceArea.copyRegions(
                    regionIds = listOf(Colosseum.ARENA_REGION),
                    enterCoord = regionLocal(Colosseum.ARENA_START),
                    exitCoord = Colosseum.LOBBY_RESPAWN,
                ),
            settingsRowId = 0,
        )

    private fun regionLocal(coord: CoordGrid): RegionLocal =
        RegionLocal(
            level = coord.level,
            regionZoneX = coord.mx,
            regionZoneZ = coord.mz,
            localX = coord.lx,
            localZ = coord.lz,
        )

    companion object {
        const val KEY = "colosseum"
    }
}
