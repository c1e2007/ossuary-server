package org.rsmod.content.raids.party

import org.rsmod.game.entity.Player

class RaidParty<S>(
    leader: Player,
    var settings: S,
    val createdAt: Int,
    val capacity: Int,
) {
    private val memberList = mutableListOf(leader)
    private val applicantList = mutableListOf<Player>()
    private val blockedList = mutableListOf<Player>()

    val members: List<Player>
        get() = memberList

    val applicants: List<Player>
        get() = applicantList

    val blocked: List<Player>
        get() = blockedList

    val leader: Player?
        get() = memberList.firstOrNull()

    val size: Int
        get() = memberList.size

    var locked: Boolean = false
        private set

    val isFull: Boolean
        get() = memberList.size >= capacity

    fun isLeader(player: Player): Boolean = leader === player

    fun isMember(player: Player): Boolean = memberList.any { it === player }

    fun isApplicant(player: Player): Boolean = applicantList.any { it === player }

    fun isBlocked(player: Player): Boolean = blockedList.any { it === player }

    fun apply(player: Player): Boolean {
        if (locked || isMember(player) || isApplicant(player) || isBlocked(player)) return false
        applicantList += player
        return true
    }

    fun withdraw(player: Player): Boolean = applicantList.removeAll { it === player }

    fun accept(player: Player): Boolean {
        if (locked || isFull || !isApplicant(player)) return false
        applicantList.removeAll { it === player }
        memberList += player
        return true
    }

    fun decline(player: Player): Boolean {
        if (!applicantList.removeAll { it === player }) return false
        blockedList += player
        return true
    }

    fun unblock(player: Player): Boolean = blockedList.removeAll { it === player }

    fun unblockAll(): Int {
        val count = blockedList.size
        blockedList.clear()
        return count
    }

    /** Removes a member; the next member in line becomes leader if the leader was the one removed. */
    fun remove(player: Player): Boolean = memberList.removeAll { it === player }

    fun lock() {
        locked = true
    }

    fun releaseApplicants(): List<Player> {
        val released = applicantList.toList()
        applicantList.clear()
        return released
    }

    fun clear() {
        memberList.clear()
        applicantList.clear()
        blockedList.clear()
    }
}
