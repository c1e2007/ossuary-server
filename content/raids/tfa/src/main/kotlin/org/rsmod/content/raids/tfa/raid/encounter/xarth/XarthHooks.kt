package org.rsmod.content.raids.tfa.raid.encounter.xarth

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

internal val XARTH_ID by lazy { "npc.tfa_xarth".asRSCM(RSCMType.NPC) }

internal val CORPSE_IDS by lazy {
    listOf("npc.tfa_xarth_corpse", "npc.tfa_xarth_corpse_doused")
        .map { it.asRSCM(RSCMType.NPC) }
        .toSet()
}

/** Keeps Xarth and his corpses at 1 hp so the spec, not the hit, decides when each dies. */
internal class XarthHpFloor : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int =
        if (npc.visType.id == XARTH_ID || npc.visType.id in CORPSE_IDS) 1 else 0
}

/**
 * Corpses are born with [Npc.ignoreCombatInteractions] so they never retaliate; a locked one is
 * absorbing or dying, and Xarth is retired the same way once he is dead.
 */
internal class XarthAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val id = npc.visType.id
        val dead =
            when {
                id == XARTH_ID -> npc.ignoreCombatInteractions
                id in CORPSE_IDS -> npc.movementLocked
                else -> false
            }
        return if (dead) {
            NpcAttackValidateResult.Deny("It's already dead.")
        } else {
            NpcAttackValidateResult.Pass
        }
    }
}
