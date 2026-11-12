package org.rsmod.content.raids.tfa.raid.reward

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invClear
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.uncert

data class TfaLootItem(val obj: String, val count: Int)

@Singleton
class TfaVaultLoot
@Inject
constructor(private val registry: DropTableRegistry, private val random: GameRandom) {
    fun rewards(player: Player): Inventory = player.invMap.getOrPut(REWARD_INV)

    fun rollStandard(player: Player): List<TfaLootItem> {
        val table = registry.forNpc(TfaChestKeys.STANDARD) ?: return emptyList()
        return drops(table.roll(player, ArgMap()).flatten()).mapNotNull { drop ->
            if (drop.isNothing || !drop.condition(player)) return@mapNotNull null
            val base = drop.rollCount(random)
            val rare = drop.obj !in TfaChestKeys.TERTIARY_OBJS && random.of(RARE_ONE_IN) == 0
            TfaLootItem(drop.transformObj(player) ?: drop.obj, scale(base, rare))
        }
    }

    fun rollUnique(player: Player): String? {
        val table = registry.forNpc(TfaChestKeys.UNIQUE) ?: return null
        return drops(table.roll(player, ArgMap()).flatten())
            .firstOrNull { !it.isNothing && it.condition(player) }
            ?.let { it.transformObj(player) ?: it.obj }
    }

    /** Replaces whatever was left unclaimed from an earlier run. */
    fun stock(player: Player, items: List<TfaLootItem>) {
        val inv = rewards(player)
        player.invClear(inv)
        for (item in items) player.invAdd(inv, item.obj, item.count)
    }

    fun add(player: Player, obj: String, count: Int): Boolean =
        player.invAdd(rewards(player), obj, count).success

    fun isEmpty(player: Player): Boolean = rewards(player).objs.all { it == null }

    /** Moves as much as fits, leaving the rest in the chest. Banked notes are un-noted. */
    fun moveAll(player: Player, into: Inventory, banking: Boolean) {
        for (slot in rewards(player).objs.indices) move(player, slot, Int.MAX_VALUE, into, banking)
    }

    fun move(player: Player, slot: Int, amount: Int, into: Inventory, banking: Boolean) {
        val pile = rewards(player)
        val obj = pile.objs.getOrNull(slot) ?: return
        val before = obj.count
        player.invTransfer(pile, slot, minOf(amount, before), into, strict = false, uncert = banking)
        val moved = before - (pile.objs.getOrNull(slot)?.count ?: 0)
        if (moved > 0) log(player, obj.id, moved)
    }

    fun discardAll(player: Player) {
        player.invClear(rewards(player))
    }

    private fun log(player: Player, id: Int, count: Int) {
        val type = ServerCacheManager.getItem(id) ?: return
        CollectionLog.grant(player, uncert(type).id, count)
    }

    private fun drops(result: RollResult<DropRollItem>): List<DropRollItem> =
        when (result) {
            is RollResult.Single -> listOf(result.result)
            is RollResult.ListOf -> result.results
            else -> emptyList()
        }

    companion object {
        const val REWARD_INV = "inv.tfa_chests"
        private const val RARE_ONE_IN = TfaChestKeys.RARE_QUANTITY_ONE_IN

        fun scale(base: Int, rare: Boolean): Int {
            if (!rare) return base
            return (base.toLong() * TfaChestKeys.RARE_QUANTITY_MULTIPLIER)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }
}
