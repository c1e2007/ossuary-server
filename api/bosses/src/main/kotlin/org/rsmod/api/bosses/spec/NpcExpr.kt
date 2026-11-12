package org.rsmod.api.bosses.spec

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Which npc(s) [Effect.AsNpc] acts as, among the ones the encounter owns. */
sealed interface NpcExpr {
    /** Every owned npc of type [npcType], or every owned npc when null. */
    data class Owned(val npcType: String? = null) : NpcExpr

    /** The owned npc of type [npcType] nearest to [of], if within [radius] tiles of it. */
    data class NearestOwned(
        val npcType: String,
        val of: TargetExpr.Single,
        val radius: Int,
    ) : NpcExpr

    /** The npcs [select] picks from the encounter's owned npcs, given the boss and current target. */
    data class Custom(
        val select: (boss: Npc, target: Player, owned: List<Npc>) -> List<Npc>,
    ) : NpcExpr
}
