package org.rsmod.content.raids.party

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.raids.party.RaidPartyRegistry.ApplyResult
import org.rsmod.game.entity.Player

class RaidPartyRegistryTest {
    private val registry = RaidPartyRegistry<Unit>(maxListed = 2, capacity = 3)
    private val leader = Player()
    private val other = Player()
    private val a = Player()
    private val b = Player()

    @Test
    fun `a player leads at most one party`() {
        assertNotNull(registry.create(leader, Unit, now = 0))
        assertNull(registry.create(leader, Unit, now = 1))
        assertEquals(1, registry.parties.size)
    }

    @Test
    fun `the list holds only so many parties`() {
        registry.create(leader, Unit, 0)
        registry.create(other, Unit, 0)
        assertTrue(registry.isFull)
        assertNull(registry.create(a, Unit, 0))
    }

    @Test
    fun `starting a party drops a pending application`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        val own = registry.create(a, Unit, 1)!!
        assertNull(registry.appliedTo(a))
        assertFalse(party.isApplicant(a))
        assertSame(own, registry.partyOf(a))
    }

    @Test
    fun `applying elsewhere withdraws the earlier application`() {
        val first = registry.create(leader, Unit, 0)!!
        val second = registry.create(other, Unit, 0)!!
        assertEquals(ApplyResult.Applied, registry.apply(a, first))
        assertEquals(ApplyResult.Applied, registry.apply(a, second))
        assertFalse(first.isApplicant(a))
        assertTrue(second.isApplicant(a))
        assertSame(second, registry.appliedTo(a))
    }

    @Test
    fun `a member cannot apply, and an unlisted party cannot be applied to`() {
        val party = registry.create(leader, Unit, 0)!!
        assertEquals(ApplyResult.AlreadyInParty, registry.apply(leader, party))
        registry.disband(party)
        assertEquals(ApplyResult.NotListed, registry.apply(a, party))
    }

    @Test
    fun `accepting moves an applicant from waiting to member`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        assertTrue(registry.accept(party, a))
        assertSame(party, registry.partyOf(a))
        assertNull(registry.appliedTo(a))
        assertFalse(registry.accept(party, a))
    }

    @Test
    fun `a declined applicant is blocked and has no application left`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        assertTrue(registry.decline(party, a))
        assertNull(registry.appliedTo(a))
        assertEquals(ApplyResult.Refused, registry.apply(a, party))
    }

    @Test
    fun `the last member leaving disbands the party and frees its applicants`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        val left = registry.leave(leader)!!
        assertTrue(left.disbanded)
        assertEquals(listOf(a), left.released)
        assertNull(registry.appliedTo(a))
        assertTrue(registry.parties.isEmpty())
    }

    @Test
    fun `a member leaving keeps the party and hands over the lead`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        registry.accept(party, a)
        val left = registry.leave(leader)!!
        assertFalse(left.disbanded)
        assertSame(a, party.leader)
        assertNull(registry.partyOf(leader))
    }

    @Test
    fun `the leader cannot be kicked`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        registry.accept(party, a)
        assertNull(registry.kick(party, leader))
        assertNotNull(registry.kick(party, a))
        assertNull(registry.partyOf(a))
    }

    @Test
    fun `locking lists the party no more and frees its applicants`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        assertEquals(listOf(a), registry.lock(party))
        assertTrue(registry.parties.isEmpty())
        assertNull(registry.appliedTo(a))
        assertSame(party, registry.partyOf(leader))
        assertEquals(ApplyResult.NotListed, registry.apply(b, party))
    }

    @Test
    fun `disbanding clears every member and applicant`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        registry.accept(party, a)
        registry.apply(b, party)
        assertEquals(listOf(leader, a), registry.disband(party))
        assertNull(registry.partyOf(leader))
        assertNull(registry.partyOf(a))
        assertNull(registry.appliedTo(b))
    }

    @Test
    fun `logging out leaves no trace`() {
        val party = registry.create(leader, Unit, 0)!!
        registry.apply(a, party)
        registry.onLogout(a)
        assertNull(registry.appliedTo(a))
        assertFalse(party.isApplicant(a))
        registry.onLogout(leader)
        assertTrue(registry.parties.isEmpty())
    }

    @Test
    fun `no mix of operations breaks the membership rules`() {
        val players = List(5) { Player() }
        repeat(300) { seed ->
            val registry = RaidPartyRegistry<Unit>(maxListed = 3, capacity = 3)
            val random = Random(seed)
            repeat(80) {
                val player = players.random(random)
                val party = registry.parties.randomOrNull(random)
                when (random.nextInt(9)) {
                    0 -> registry.create(player, Unit, it)
                    1 -> party?.let { p -> registry.apply(player, p) }
                    2 -> party?.let { p -> registry.accept(p, player) }
                    3 -> party?.let { p -> registry.decline(p, player) }
                    4 -> party?.let { p -> registry.unblock(p, player) }
                    5 -> registry.leave(player)
                    6 -> party?.let { p -> registry.kick(p, player) }
                    7 -> party?.let { p -> registry.lock(p) }
                    else -> registry.onLogout(player)
                }
                assertConsistent(registry, players)
            }
        }
    }

    private fun assertConsistent(registry: RaidPartyRegistry<Unit>, players: List<Player>) {
        for (player in players) {
            val parties = registry.parties
            val joined = parties.count { it.isMember(player) }
            val waiting = parties.count { it.isApplicant(player) }
            assertTrue(joined <= 1, "member of $joined parties")
            assertTrue(waiting <= 1, "waiting on $waiting parties")
            assertFalse(joined == 1 && waiting == 1, "member and applicant at once")
            registry.partyOf(player)?.let { assertTrue(it.isMember(player)) }
            registry.appliedTo(player)?.let { assertTrue(it.isApplicant(player)) }
        }
        for (party in registry.parties) {
            assertTrue(party.size in 1..party.capacity, "size ${party.size}")
            assertEquals(party.members.size, party.members.toSet().size)
            for (member in party.members) assertSame(party, registry.partyOf(member))
            for (applicant in party.applicants) assertSame(party, registry.appliedTo(applicant))
        }
    }
}
