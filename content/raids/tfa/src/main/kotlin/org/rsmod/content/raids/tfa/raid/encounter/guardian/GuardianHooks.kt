package org.rsmod.content.raids.tfa.raid.encounter.guardian

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.type.getOrNull

/** Keeps the shield, the health pool and charged batteries at 1 hp so overlapping hits can't kill them. */
internal class GuardianHpFloor : NpcHitpointsFloorHook {
    private val floored =
        listOf("npc.tfa_guardian", "npc.tfa_guardian_unshielded", "npc.tfa_guardian_battery_active")
            .map { it.asRSCM(RSCMType.NPC) }
            .toSet()

    override fun floor(npc: Npc, hit: Hit): Int = if (npc.visType.id in floored) 1 else 0
}

internal class GuardianAttackHook @Inject constructor(private val types: AttackTypes) :
    NpcAttackValidateHook {
    private val flying = "npc.tfa_guardian".asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.visType.id != flying) return NpcAttackValidateResult.Pass
        val type = types.get(player)
        if (type != null && !type.isMelee) return NpcAttackValidateResult.Pass
        val weapon = getOrNull(player.righthand)
        if (weapon != null && weapon.isCategoryType("category.halberd")) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("${npc.name} is flying too high for you to reach with melee.")
    }
}
