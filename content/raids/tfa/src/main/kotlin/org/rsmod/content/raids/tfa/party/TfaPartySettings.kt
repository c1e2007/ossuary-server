package org.rsmod.content.raids.tfa.party

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.raids.party.RaidPartyRegistry

class TfaPartySettings(
    var preferredSize: Int = DEFAULT_SIZE,
    var preferredCompletions: Int = 0,
    var mode: Int = NORMAL,
    var spectatingDisabled: Boolean = false,
) {
    val modeName: String
        get() = if (mode == PRACTICE) "Practice" else "Normal"

    companion object {
        const val NORMAL = 0
        const val PRACTICE = 1
        const val DEFAULT_SIZE = 4
        const val MAX_SIZE = 8
        const val MAX_COMPLETIONS = 100
    }
}

@Singleton
class TfaParties @Inject constructor() :
    RaidPartyRegistry<TfaPartySettings>(MAX_LISTED, MAX_MEMBERS) {
    companion object {
        const val MAX_LISTED = 45
        const val MAX_MEMBERS = 8
    }
}
