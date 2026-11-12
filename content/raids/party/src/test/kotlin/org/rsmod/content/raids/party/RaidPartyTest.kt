package org.rsmod.content.raids.party

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player

class RaidPartyTest {
    private val leader = Player()
    private val a = Player()
    private val b = Player()
    private val c = Player()

    private fun party(capacity: Int = 3) = RaidParty(leader, settings = Unit, createdAt = 0, capacity)

    @Test
    fun `the creator leads`() {
        val party = party()
        assertSame(leader, party.leader)
        assertTrue(party.isLeader(leader))
        assertEquals(1, party.size)
    }

    @Test
    fun `an applicant is accepted into the party once`() {
        val party = party()
        assertTrue(party.apply(a))
        assertTrue(party.accept(a))
        assertFalse(party.accept(a))
        assertEquals(listOf(leader, a), party.members)
        assertTrue(party.applicants.isEmpty())
    }

    @Test
    fun `nobody applies twice or applies to a party they are in`() {
        val party = party()
        assertTrue(party.apply(a))
        assertFalse(party.apply(a))
        assertFalse(party.apply(leader))
        assertEquals(listOf(a), party.applicants)
    }

    @Test
    fun `accepting into a full party keeps the applicant waiting`() {
        val party = party(capacity = 2)
        party.apply(a)
        party.apply(b)
        assertTrue(party.accept(a))
        assertFalse(party.accept(b))
        assertTrue(party.isApplicant(b))
        assertEquals(2, party.size)
    }

    @Test
    fun `a declined player is blocked until unblocked`() {
        val party = party()
        party.apply(a)
        assertTrue(party.decline(a))
        assertTrue(party.isBlocked(a))
        assertFalse(party.apply(a))
        assertTrue(party.unblock(a))
        assertTrue(party.apply(a))
    }

    @Test
    fun `a locked party takes no applicants or acceptances`() {
        val party = party()
        party.apply(a)
        party.lock()
        assertFalse(party.apply(b))
        assertFalse(party.accept(a))
        assertEquals(listOf(a), party.applicants)
    }

    @Test
    fun `leadership passes to the next member when the leader leaves`() {
        val party = party()
        party.apply(a)
        party.accept(a)
        party.apply(b)
        party.accept(b)
        assertTrue(party.remove(leader))
        assertSame(a, party.leader)
        assertFalse(party.remove(leader))
    }

    @Test
    fun `releasing applicants empties the waiting list`() {
        val party = party()
        party.apply(a)
        party.apply(b)
        assertEquals(listOf(a, b), party.releaseApplicants())
        assertTrue(party.applicants.isEmpty())
        assertFalse(party.isApplicant(c))
    }
}
