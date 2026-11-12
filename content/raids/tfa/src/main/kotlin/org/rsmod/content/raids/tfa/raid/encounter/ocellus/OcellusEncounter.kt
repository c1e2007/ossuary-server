package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.content.raids.tfa.raid.reward.TfaVault
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@Singleton
class OcellusEncounter
@Inject
constructor(
    private val deps: BossDeps,
    private val manager: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val hpBar: BossHpBarScript,
    private val vault: TfaVault,
    private val energy: SpecialAttackEnergy,
) {
    internal val meter = OcellusMeter(deps, hpBar)

    private val fights = HashMap<InstanceId, OcellusFight>()

    sealed interface Opened {
        class Room internal constructor(internal val fight: OcellusFight, val enter: CoordGrid) :
            Opened

        data class Failed(val reason: String) : Opened
    }

    internal fun of(npc: Npc): OcellusFight? = fights.values.firstOrNull { it.boss === npc }

    internal fun of(id: InstanceId): OcellusFight? = fights[id]

    internal fun at(tile: CoordGrid): OcellusFight? = fights.values.firstOrNull { it.contains(tile) }

    internal fun open(owner: Player): Opened =
        when (val result = manager.create(owner, KEY, SPEC, InstanceAccess.Private, deps.mapClock.cycle)) {
            is InstanceManager.Result.Failed -> Opened.Failed(result.reason)
            is InstanceManager.Result.Joined -> Opened.Failed("Ocellus rooms can't be joined yet.")
            is InstanceManager.Result.Created -> {
                val origin = checkNotNull(manager.resolveCoord(result.session, INTRO_SPAWN))
                val fight =
                    OcellusFight(result.session, origin, deps, meter, hpBar, interactions, energy, vault)
                manager.attachNpc(result.session.id, fight.open())
                fights[result.session.id] = fight
                Opened.Room(fight, result.enter)
            }
        }

    internal fun close(id: InstanceId) {
        fights.remove(id)?.dispose()
    }

    companion object {
        const val KEY = "tfa_ocellus"

        private val ENTRANCE = CoordGrid(4128, 5658, 1)
        private val INTRO_SPAWN = CoordGrid(4126, 5669, 1)
        private const val MAP_SQUARE_SHIFT = 6
        private const val REGION_X_SHIFT = 8
        private const val MAX_PARTY = 8

        private val SPEC =
            InstanceSpec(
                fee = 0,
                maxPlayers = MAX_PARTY,
                reclaimTicks = 0,
                graceTicks = 0,
                destroyWhenEmpty = true,
                area =
                    InstanceArea.copyRegions(
                        regionIds = listOf(regionId(INTRO_SPAWN)),
                        level = INTRO_SPAWN.level,
                        enterCoord = local(ENTRANCE),
                        exitCoord = TfaVault.LOBBY,
                    ),
                settingsRowId = 0,
            )

        private fun regionId(tile: CoordGrid): Int =
            ((tile.x shr MAP_SQUARE_SHIFT) shl REGION_X_SHIFT) or (tile.z shr MAP_SQUARE_SHIFT)

        private fun local(tile: CoordGrid) =
            RegionLocal(tile.level, tile.mx, tile.mz, tile.lx, tile.lz)
    }
}
