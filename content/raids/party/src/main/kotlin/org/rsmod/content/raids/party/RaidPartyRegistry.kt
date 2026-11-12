package org.rsmod.content.raids.party

import java.util.IdentityHashMap
import org.rsmod.game.entity.Player

/**
 * Every party a lobby lists, and which party each player is in or applying to. A player is in at
 * most one party and applying to at most one, and never both at once, whatever order the calls
 * come in.
 */
open class RaidPartyRegistry<S>(private val maxListed: Int, private val capacity: Int) {
    private val listed = mutableListOf<RaidParty<S>>()
    private val membership = IdentityHashMap<Player, RaidParty<S>>()
    private val applications = IdentityHashMap<Player, RaidParty<S>>()

    enum class ApplyResult {
        Applied,
        AlreadyInParty,
        NotListed,
        Refused,
    }

    class Left<S>(val party: RaidParty<S>, val disbanded: Boolean, val released: List<Player>)

    val parties: List<RaidParty<S>>
        get() = listed

    val isFull: Boolean
        get() = listed.size >= maxListed

    fun partyOf(player: Player): RaidParty<S>? = membership[player]

    fun appliedTo(player: Player): RaidParty<S>? = applications[player]

    fun create(leader: Player, settings: S, now: Int): RaidParty<S>? {
        if (isFull || membership.containsKey(leader)) return null
        withdraw(leader)
        val party = RaidParty(leader, settings, now, capacity)
        listed += party
        membership[leader] = party
        return party
    }

    fun apply(player: Player, party: RaidParty<S>): ApplyResult {
        if (membership.containsKey(player)) return ApplyResult.AlreadyInParty
        if (party !in listed) return ApplyResult.NotListed
        withdraw(player)
        if (!party.apply(player)) return ApplyResult.Refused
        applications[player] = party
        return ApplyResult.Applied
    }

    fun withdraw(player: Player): RaidParty<S>? {
        val party = applications.remove(player) ?: return null
        party.withdraw(player)
        return party
    }

    fun accept(party: RaidParty<S>, applicant: Player): Boolean {
        if (applications[applicant] !== party || !party.accept(applicant)) return false
        applications.remove(applicant)
        membership[applicant] = party
        return true
    }

    fun decline(party: RaidParty<S>, applicant: Player): Boolean {
        if (applications[applicant] !== party || !party.decline(applicant)) return false
        applications.remove(applicant)
        return true
    }

    fun unblock(party: RaidParty<S>, player: Player): Boolean = party.unblock(player)

    fun unblockAll(party: RaidParty<S>): Int = party.unblockAll()

    /** Takes [player] out of their party; the party disbands, releasing applicants, once empty. */
    fun leave(player: Player): Left<S>? {
        val party = membership.remove(player) ?: return null
        party.remove(player)
        if (party.members.isNotEmpty()) return Left(party, disbanded = false, released = emptyList())
        listed.remove(party)
        val released = releaseApplicants(party)
        return Left(party, disbanded = true, released = released)
    }

    fun kick(party: RaidParty<S>, target: Player): Left<S>? {
        if (membership[target] !== party || party.isLeader(target)) return null
        return leave(target)
    }

    /** Removes every member and applicant from [party]; returns who was in it. */
    fun disband(party: RaidParty<S>): List<Player> {
        val former = party.members.toList()
        for (member in former) membership.remove(member)
        releaseApplicants(party)
        party.clear()
        listed.remove(party)
        return former
    }

    /** Stops [party] taking applicants and drops it from the list; its members stay in it. */
    fun lock(party: RaidParty<S>): List<Player> {
        party.lock()
        listed.remove(party)
        return releaseApplicants(party)
    }

    fun onLogout(player: Player): Left<S>? {
        withdraw(player)
        for (party in listed) party.unblock(player)
        return leave(player)
    }

    private fun releaseApplicants(party: RaidParty<S>): List<Player> {
        val released = party.releaseApplicants()
        for (applicant in released) applications.remove(applicant)
        return released
    }
}
