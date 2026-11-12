package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import kotlin.random.Random

internal enum class ColosseumModifier(val key: Int, val struct: Int, val varbit: String?) {
    MANTIMAYHEM(0, 915, "varbit.colosseum_modifier_mantimayhem_stacks_client"),
    REENTRY(1, 891, "varbit.colosseum_modifier_reentry_stacks_client"),
    BEES(2, 892, "varbit.colosseum_modifier_toxicity_stacks_client"),
    VOLATILITY(3, 893, "varbit.colosseum_modifier_volatility_stacks_client"),
    BLASPHEMY(4, 894, "varbit.colosseum_modifier_blasphemy_stacks_client"),
    RELENTLESS(5, 897, "varbit.colosseum_modifier_relentless_stacks_client"),
    QUARTET(6, 898, null),
    TOTEMIC(7, 899, null),
    DOOM(8, 900, "varbit.colosseum_modifier_doom_stacks_client"),
    DYNAMIC_DUO(9, 901, null),
    SOLARFLARE(10, 902, "varbit.colosseum_modifier_solarflare_stacks_client"),
    MYOPIA(11, 903, "varbit.colosseum_modifier_myopia_stacks_client"),
    FRAILTY(12, 906, "varbit.colosseum_modifier_frailty_stacks_client"),
    RED_FLAG(13, 907, null);

    val tiered: Boolean
        get() = varbit != null

    val gloryPerWave: Int
        get() = (ServerCacheManager.getStruct(struct)?.params?.get(GLORY_PARAM) as? Int) ?: 0

    companion object {
        private const val GLORY_PARAM = 1903
        const val MAX_TIER = 3

        fun byKey(key: Int): ColosseumModifier? = entries.firstOrNull { it.key == key }
    }
}

internal class ModifierSet {
    val tiers: MutableMap<ColosseumModifier, Int> = linkedMapOf()

    fun tier(modifier: ColosseumModifier): Int = tiers[modifier] ?: 0

    fun has(modifier: ColosseumModifier): Boolean = tier(modifier) > 0

    fun maxed(modifier: ColosseumModifier): Boolean =
        tier(modifier) >= if (modifier.tiered) ColosseumModifier.MAX_TIER else 1

    fun add(modifier: ColosseumModifier) {
        tiers[modifier] = minOf(ColosseumModifier.MAX_TIER, tier(modifier) + 1)
    }

    fun gloryPerWave(): Int = tiers.entries.sumOf { (modifier, tier) -> modifier.gloryPerWave * tier }

    fun offer(wave: Int, random: Random): List<ColosseumModifier> {
        if (wave == 1) {
            return FIRST_OFFER
        }
        val pool = mutableListOf<Pair<ColosseumModifier, Int>>()
        for (modifier in ColosseumModifier.entries) {
            if (maxed(modifier)) continue
            if (modifier in LATE && wave < LATE_FROM_WAVE) continue
            if (modifier == ColosseumModifier.DYNAMIC_DUO && wave > DYNAMIC_DUO_LAST_WAVE) continue
            pool += modifier to if (has(modifier)) UPGRADE_WEIGHT else 1
        }
        val picked = mutableListOf<ColosseumModifier>()
        while (picked.size < OFFER_SIZE && pool.isNotEmpty()) {
            var roll = random.nextDouble() * pool.sumOf { it.second }
            val index = pool.indexOfFirst { roll -= it.second; roll < 0 }
            picked += pool.removeAt(if (index < 0) pool.lastIndex else index).first
        }
        return picked.sortedBy { it.key }
    }

    private companion object {
        val FIRST_OFFER =
            listOf(
                ColosseumModifier.BLASPHEMY,
                ColosseumModifier.RELENTLESS,
                ColosseumModifier.FRAILTY,
            )
        val LATE = setOf(ColosseumModifier.RED_FLAG, ColosseumModifier.DYNAMIC_DUO)
        const val LATE_FROM_WAVE = 7
        const val DYNAMIC_DUO_LAST_WAVE = 11
        const val UPGRADE_WEIGHT = 2
        const val OFFER_SIZE = 3
    }
}
