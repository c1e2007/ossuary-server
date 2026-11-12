package org.rsmod.content.raids.tfa.raid.reward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TfaUniqueClaimTest {
    private val winner = 7L
    private val other = 8L

    private fun claim() = TfaUniqueClaim(winner, "obj.rondache")

    @Test
    fun `revealing hands the unique over once`() {
        val claim = claim()
        assertEquals("obj.rondache", claim.beginReveal())
        assertEquals("obj.rondache", claim.finishReveal())
        assertNull(claim.finishReveal())
        assertNull(claim.beginReveal())
        assertNull(claim.settle(winner))
    }

    @Test
    fun `settling hands the unique over once and blocks a later reveal`() {
        val claim = claim()
        assertEquals("obj.rondache", claim.settle(winner))
        assertNull(claim.settle(winner))
        assertNull(claim.beginReveal())
        assertNull(claim.finishReveal())
    }

    @Test
    fun `settling mid reveal gives nothing and the reveal still pays out`() {
        val claim = claim()
        claim.beginReveal()
        assertNull(claim.settle(winner))
        assertEquals("obj.rondache", claim.finishReveal())
    }

    @Test
    fun `a second click during the reveal is refused`() {
        val claim = claim()
        assertEquals("obj.rondache", claim.beginReveal())
        assertNull(claim.beginReveal())
    }

    @Test
    fun `finishing without beginning pays nothing`() {
        assertNull(claim().finishReveal())
    }

    @Test
    fun `someone else leaving neither claims nor spends it`() {
        val claim = claim()
        assertNull(claim.settle(other))
        assertNull(claim.settle(null))
        assertEquals("obj.rondache", claim.settle(winner))
    }

    @Test
    fun `no unique means nothing to hand over`() {
        val claim = TfaUniqueClaim(winner = null, obj = null)
        assertFalse(claim.hasUnique)
        assertFalse(claim.isWinner(winner))
        assertNull(claim.beginReveal())
        assertNull(claim.finishReveal())
        assertNull(claim.settle(winner))
    }

    @Test
    fun `a missing key is never the winner`() {
        assertFalse(TfaUniqueClaim(winner = null, obj = "obj.rondache").isWinner(null))
        assertTrue(claim().isWinner(winner))
        assertFalse(claim().isWinner(null))
    }

    @Test
    fun `no order of reveal and leave steps pays out more than once`() {
        val steps: List<(TfaUniqueClaim) -> String?> =
            listOf(
                { it.beginReveal() },
                { it.finishReveal() },
                { it.settle(winner) },
                { it.settle(other) },
            )
        var sequences = 0

        fun explore(claim: List<Int>) {
            if (claim.size == SEQUENCE_LENGTH) {
                val fresh = claim()
                val payouts = claim.count { steps[it](fresh) != null && it != BEGIN }
                assertTrue(payouts <= 1, "sequence $claim paid out $payouts times")
                sequences++
                return
            }
            for (step in steps.indices) explore(claim + step)
        }

        explore(emptyList())
        assertEquals(Math.pow(steps.size.toDouble(), SEQUENCE_LENGTH.toDouble()).toInt(), sequences)
    }

    private companion object {
        const val SEQUENCE_LENGTH = 6
        const val BEGIN = 0
    }
}
