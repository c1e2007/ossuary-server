package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape

internal class ItemStack(val item: String, var amount: Int)

internal class RunLoot(
    val rewards: MutableList<ItemStack> = mutableListOf(),
    var future: List<ItemStack> = emptyList(),
    var previous: List<ItemStack> = emptyList(),
)

private val FANATIC_PIECES_KEY = AttributeKey<MutableList<Int>>(persistenceKey = "colosseum_fanatic")

@Singleton
internal class ColosseumRewards
@Inject
constructor(private val locRepo: LocRepository, private val runs: ColosseumRuns) {
    fun start(run: ColosseumRun) {
        run.loot = RunLoot(future = roll(1, run.player, run.random))
        run.glory = 0
        run.player.colosseumCurrentGlory = 0
    }

    fun roll(wave: Int, player: Player, random: Random): List<ItemStack> {
        if (wave <= 1) return listOf(ItemStack(SPLINTERS, 80))
        val table = ColosseumLootTables.TABLES.getValue(minOf(wave, Colosseum.FINAL_WAVE))
        var pick = random.nextInt(table.sumOf { it.weight })
        val entry = table.first { pick -= it.weight; pick < 0 }
        val amount = if (entry.min == entry.max) entry.min else random.nextInt(entry.min, entry.max + 1)
        val item = entry.item ?: fanaticPiece(player, random)
        val items = mutableListOf(ItemStack(item, amount))
        if (wave >= Colosseum.FINAL_WAVE) items += ItemStack(QUIVER, 1)
        return items
    }

    private fun fanaticPiece(player: Player, random: Random): String {
        val had = player.attr[FANATIC_PIECES_KEY] ?: mutableListOf(0, 0, 0)
        val fewest = had.min()
        return FANATIC_PIECES[had.indices.filter { had[it] == fewest }.random(random)]
    }

    private fun received(player: Player, items: List<ItemStack>) {
        val had = (player.attr[FANATIC_PIECES_KEY] ?: mutableListOf(0, 0, 0)).toMutableList()
        for (stack in items) {
            val index = FANATIC_PIECES.indexOf(stack.item)
            if (index >= 0) had[index] = had[index] + 1
        }
        player.attr[FANATIC_PIECES_KEY] = had
    }

    fun valueOf(items: List<ItemStack>): Int =
        items.sumOf { stack ->
            val cost = ServerCacheManager.getItem(stack.item.asRSCM(RSCMType.OBJ))?.cost ?: 0
            (cost.toLong() * stack.amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    fun waveCompleted(run: ColosseumRun) {
        val loot = run.loot ?: return
        val wave = run.wave
        val earned = loot.future
        for (stack in earned) {
            val existing = loot.rewards.firstOrNull { it.item == stack.item }
            if (existing != null) existing.amount += stack.amount else loot.rewards += ItemStack(stack.item, stack.amount)
        }
        received(run.player, earned)
        loot.previous = earned
        loot.future = if (wave < Colosseum.FINAL_WAVE) roll(wave + 1, run.player, run.random) else emptyList()
        if (wave >= Colosseum.FINAL_WAVE && run.random.nextInt(PET_CHANCE) == 0) {
            loot.rewards += ItemStack(PET, 1)
        }
        run.glory += gloryFor(run, wave)
        run.player.colosseumCurrentGlory = run.glory
    }

    fun gloryFor(run: ColosseumRun, wave: Int): Int {
        val time = maxOf(0, (GLORY_TIME_START - run.waveTicks) * wave)
        return GLORY_COMPLETION * wave +
            (if (run.hitByEnemy) 0 else GLORY_NO_DAMAGE * wave) +
            run.modifiers.gloryPerWave() +
            time - (time % 2)
    }

    fun keepGlory(run: ColosseumRun) {
        if (run.glory > run.player.colosseumGlory) {
            run.player.colosseumGlory = run.glory
        }
    }

    fun lost(run: ColosseumRun) {
        run.loot = RunLoot()
    }

    fun finishRun(run: ColosseumRun) {
        if (run.stage == RunStage.ENDED || run.stage == RunStage.FINISHED) return
        run.stage = RunStage.FINISHED
        keepGlory(run)
        bringOutChest(run)
        run.minimus?.let { runs.remove(it) }
        run.minimus = runs.spawn(run, ColosseumNpcs.MINIMUS_ARENA, run.tile(Colosseum.MINIMUS_BY_CHEST))
    }

    private fun bringOutChest(run: ColosseumRun) {
        val chest =
            locRepo.add(
                run.tile(Colosseum.CHEST),
                "loc.colosseum_reward",
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        run.chest = chest
        run.cleanup += { removeChest(run) }
    }

    fun removeChest(run: ColosseumRun) {
        run.chest?.let { locRepo.del(it, 0) }
        run.chest = null
    }

    fun claim(run: ColosseumRun, discard: Boolean) {
        val loot = run.loot ?: return
        val player = run.player
        if (discard) {
            loot.rewards.clear()
            player.mes("The chest empties before your eyes...")
            return
        }
        val iterator = loot.rewards.iterator()
        while (iterator.hasNext()) {
            val stack = iterator.next()
            val before = player.inv.count(stack.item)
            player.invAdd(player.inv, stack.item, stack.amount, strict = false)
            val moved = player.inv.count(stack.item) - before
            stack.amount -= moved.coerceAtLeast(0)
            if (stack.amount <= 0) iterator.remove()
        }
        if (loot.rewards.isNotEmpty()) player.mes("You don't have enough inventory space for everything.")
    }

    private companion object {
        const val SPLINTERS = "obj.sunfiresplinter"
        const val QUIVER = "obj.dizanas_quiver_uncharged"
        const val PET = "obj.solhereditpet"
        const val PET_CHANCE = 200
        const val GLORY_COMPLETION = 100
        const val GLORY_NO_DAMAGE = 100
        const val GLORY_TIME_START = 500
        val FANATIC_PIECES = listOf("obj.sunfire_helm", "obj.sunfire_body", "obj.sunfire_legs")
    }
}
