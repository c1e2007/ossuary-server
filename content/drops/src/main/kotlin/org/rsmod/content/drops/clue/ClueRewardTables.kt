package org.rsmod.content.drops.clue

import org.rsmod.api.random.GameRandom

/** One reward line: [obj] in quantity [min]..[max], with per-roll probability [chance]. */
public data class ClueReward(val obj: String, val min: Int, val max: Int, val chance: Double)

/**
 * Reward table for one treasure trail tier, from the OSRS Wiki "Reward casket" pages. Each roll
 * picks exactly one line (the per-roll rarities in a tier sum to 1); [minRolls]..[maxRolls] rolls
 * are made per casket. A tertiary item (e.g. master clue scroll) is rolled once, independently.
 */
public class ClueRewardTable(
    public val tier: String,
    public val casket: String,
    public val minRolls: Int,
    public val maxRolls: Int,
    public val rewards: List<ClueReward>,
    public val tertiaryObj: String? = null,
    public val tertiaryOneIn: Int = 0,
) {
    private val cumulative: DoubleArray =
        DoubleArray(rewards.size).also {
            var sum = 0.0
            rewards.forEachIndexed { i, r ->
                sum += r.chance
                it[i] = sum
            }
        }

    public fun rollOne(random: GameRandom): Pair<String, Int> {
        val u = random.randomDouble() * cumulative.last()
        var idx = cumulative.indexOfFirst { u < it }
        if (idx < 0) idx = rewards.lastIndex
        val r = rewards[idx]
        return r.obj to random.of(r.min, r.max)
    }

    /** Rolls a full casket; identical objs are merged. Tertiary comes last. */
    public fun open(random: GameRandom): List<Pair<String, Int>> {
        val rolls = random.of(minRolls, maxRolls)
        val out = LinkedHashMap<String, Int>()
        repeat(rolls) {
            val (obj, qty) = rollOne(random)
            out.merge(obj, qty, Int::plus)
        }
        if (tertiaryObj != null && random.of(tertiaryOneIn) == 0) {
            out.merge(tertiaryObj, 1, Int::plus)
        }
        return out.map { it.key to it.value }
    }

    public companion object {
        // tier, casket obj, min rolls, max rolls, tertiary obj, tertiary 1-in-N (wiki values)
        private val defs =
            listOf(
                Def("beginner", "obj.trail_reward_casket_beginner", 1, 3, null, 0),
                Def("easy", "obj.trail_reward_casket_easy", 2, 4, "obj.trail_clue_master", 50),
                Def("medium", "obj.trail_reward_casket_medium", 3, 5, "obj.trail_clue_master", 30),
                Def("hard", "obj.trail_reward_casket_hard", 4, 6, "obj.trail_clue_master", 15),
                Def("elite", "obj.trail_reward_casket_elite", 4, 6, "obj.trail_clue_master", 5),
                Def("master", "obj.trail_reward_casket_master", 5, 7, null, 0),
            )

        private class Def(
            val tier: String,
            val casket: String,
            val min: Int,
            val max: Int,
            val tertiary: String?,
            val oneIn: Int,
        )

        public fun load(tier: String): ClueRewardTable {
            val def = defs.first { it.tier == tier }
            val stream =
                ClueRewardTable::class.java.getResourceAsStream("/clue_rewards/$tier.tsv")
                    ?: error("Missing clue reward resource for tier '$tier'.")
            val rewards =
                stream.bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map {
                    val p = it.split('\t')
                    val (num, den) = p[3].split('/')
                    ClueReward(p[0], p[1].toInt(), p[2].toInt(), num.toDouble() / den.toDouble())
                }
            return ClueRewardTable(
                tier, def.casket, def.min, def.max, rewards, def.tertiary, def.oneIn,
            )
        }

        public fun loadAll(): List<ClueRewardTable> = defs.map { load(it.tier) }
    }
}
