package org.rsmod.content.drops.clue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClueRewardTableTest {
    @Test
    fun `per-roll chances sum to one for every tier`() {
        for (t in ClueRewardTable.loadAll()) {
            val sum = t.rewards.sumOf { it.chance }
            assertEquals(1.0, sum, 0.002, "tier ${t.tier}")
            assertTrue(t.rewards.all { it.min in 1..it.max }, "tier ${t.tier}")
        }
    }
}
