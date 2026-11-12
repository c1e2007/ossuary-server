package org.rsmod.content.raids.tfa.party

internal object TfaPartyFormat {
    private const val EMPTY_NAME = "-"
    private const val FIELD = '|'

    fun lobbyNames(names: List<String>): String =
        List(TfaParties.MAX_MEMBERS) { names.getOrNull(it) ?: EMPTY_NAME }.joinToString("<br>")

    /** `leader|member1..member7|size|preferredSize|preferredCompletions|mode|age` */
    fun row(
        leader: String,
        others: List<String>,
        size: Int,
        settings: TfaPartySettings,
        age: Int,
    ): String =
        buildString {
            append(leader).append(FIELD)
            for (slot in 0 until TfaParties.MAX_MEMBERS - 1) {
                append(others.getOrNull(slot).orEmpty()).append(FIELD)
            }
            append(size).append(FIELD)
            append(settings.preferredSize).append(FIELD)
            append(settings.preferredCompletions).append(FIELD)
            append(settings.modeName).append(FIELD)
            append(age)
        }

    /** `name|combat|attack|strength|ranged|magic|defence|hitpoints|prayer|completions|practice` */
    fun member(name: String, combat: Int, skills: List<Int>, completions: Int, practice: Int): String =
        buildString {
            append(name).append(FIELD).append(combat).append(FIELD)
            for (level in skills) append(level).append(FIELD)
            append(completions).append(FIELD).append(practice)
        }
}
