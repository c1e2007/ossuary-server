package org.rsmod.content.raids.tfa.raid.encounter.bork

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

private val ROOM_NPCS =
    listOf("npc.tfa_bork", "npc.tfa_smork", "npc.tfa_invisible_npc")
        .map { it.asRSCM(RSCMType.NPC) }
        .toSet()

/** Keeps Bork and the Smorks at 1 hp so the spec, not the hit, decides when each dies. */
internal class BorkHpFloor : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int = if (npc.visType.id in ROOM_NPCS) 1 else 0
}

internal class BorkAttackHook @Inject constructor(private val fights: BorkEncounter) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.visType.id in ROOM_NPCS && isDead(npc)) {
            NpcAttackValidateResult.Deny("It's already dead.")
        } else {
            NpcAttackValidateResult.Pass
        }

    private fun isDead(npc: Npc): Boolean =
        fights.ofSmork(npc)?.smorkDead() ?: npc.ignoreCombatInteractions
}
