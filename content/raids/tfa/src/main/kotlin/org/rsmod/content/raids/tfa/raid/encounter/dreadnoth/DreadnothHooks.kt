package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

private val ROOM_NPCS =
    listOf("npc.tfa_jelly_boss", "npc.tfa_jelly_minion", "npc.tfa_invisible_npc")
        .map { it.asRSCM(RSCMType.NPC) }
        .toSet()

/**
 * Keeps the boss, its jellies and the invisible holder it becomes at 1 hp so the spec, not the hit,
 * decides when they die. The holder keeps the encounter alive while the last jellies are killed.
 */
internal class DreadnothHpFloor : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int = if (npc.visType.id in ROOM_NPCS) 1 else 0
}

internal class DreadnothAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.visType.id in ROOM_NPCS && npc.ignoreCombatInteractions) {
            NpcAttackValidateResult.Deny("It's already dead.")
        } else {
            NpcAttackValidateResult.Pass
        }
}
