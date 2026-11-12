package org.rsmod.content.raids.tfa.raid.reward

/**
 * A run's unique and who may take it. It leaves here at most once, either through the chest's
 * opening animation ([beginReveal] then [finishReveal]) or, if its owner leaves first, [settle].
 */
internal class TfaUniqueClaim(private val winner: Long?, val obj: String?) {
    private enum class State {
        Unclaimed,
        Opening,
        Claimed,
    }

    private var state = State.Unclaimed

    val hasUnique: Boolean
        get() = obj != null

    fun isWinner(key: Long?): Boolean = winner != null && winner == key

    fun beginReveal(): String? {
        if (obj == null || state != State.Unclaimed) return null
        state = State.Opening
        return obj
    }

    fun finishReveal(): String? {
        if (state != State.Opening) return null
        state = State.Claimed
        return obj
    }

    fun settle(key: Long?): String? {
        if (!isWinner(key) || state != State.Unclaimed) return null
        state = State.Claimed
        return obj
    }
}
