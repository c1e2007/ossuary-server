package org.rsmod.content.raids.tfa.raid.reward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TfaVaultLootScaleTest {
    @Test
    fun `an ordinary roll keeps its count`() {
        assertEquals(301, TfaVaultLoot.scale(301, rare = false))
    }

    @Test
    fun `a rare roll multiplies its count`() {
        assertEquals(301 * TfaChestKeys.RARE_QUANTITY_MULTIPLIER, TfaVaultLoot.scale(301, rare = true))
    }

    @Test
    fun `a rare roll on a huge stack stops at the int ceiling instead of wrapping`() {
        assertEquals(Int.MAX_VALUE, TfaVaultLoot.scale(Int.MAX_VALUE / 2, rare = true))
        assertEquals(Int.MAX_VALUE, TfaVaultLoot.scale(Int.MAX_VALUE, rare = true))
    }

    @Test
    fun `scaling nothing gives nothing`() {
        assertEquals(0, TfaVaultLoot.scale(0, rare = true))
    }
}
