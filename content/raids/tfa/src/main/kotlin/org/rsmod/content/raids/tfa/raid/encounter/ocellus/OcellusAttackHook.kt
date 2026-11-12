package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class OcellusAttackHook @Inject constructor(private val encounter: OcellusEncounter) :
    NpcAttackValidateHook {
    private val phaseThree = "npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.visType.id != phaseThree) return NpcAttackValidateResult.Pass
        val phaseTwo = encounter.of(npc)?.phaseTwo ?: return NpcAttackValidateResult.Pass
        if (!phaseTwo.isOutsideQuadrant(npc, player.coords)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("You can't reach Ocellus from across the gap.")
    }
}
