package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hit

/**
 * Keeps the phase 1 shield at 1 hp when hits land, so overlapping queued hits (e.g. a scythe) can't
 * kill it; reaching 1 hp is what breaks the shield.
 */
internal class OcellusShieldFloor : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int = if (npc.visType.id == SHIELD_ID) 1 else 0

    private companion object {
        val SHIELD_ID = "npc.tfa_ocellus_p1".asRSCM(RSCMType.NPC)
    }
}
