package org.rsmod.content.raids.tfa.party

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TfaPartyFormatTest {
    private val settings = TfaPartySettings(preferredSize = 4, preferredCompletions = 0)

    @Test
    fun `a lone leader row matches the captured list`() {
        val row = TfaPartyFormat.row("IronDev1", emptyList(), 1, settings, age = 13)
        assertEquals("IronDev1||||||||1|4|0|Normal|13", row)
    }

    @Test
    fun `a full row matches the captured list`() {
        val others = listOf("p p cheese", "Miasna", "Shamu", "yallu", "r0yalrag3r")
        val row = TfaPartyFormat.row("Sebla", others, 6, settings, age = 96)
        assertEquals("Sebla|p p cheese|Miasna|Shamu|yallu|r0yalrag3r|||6|4|0|Normal|96", row)
    }

    @Test
    fun `practice mode and preferences show in the row`() {
        val practice = TfaPartySettings(preferredSize = 2, preferredCompletions = 50, mode = TfaPartySettings.PRACTICE)
        assertEquals("A|B|||||||2|2|50|Practice|0", TfaPartyFormat.row("A", listOf("B"), 2, practice, 0))
    }

    @Test
    fun `lobby names pad empty slots like the captured overlay`() {
        assertEquals(
            "USA VERY FAT<br>-<br>-<br>-<br>-<br>-<br>-<br>-",
            TfaPartyFormat.lobbyNames(listOf("USA VERY FAT")),
        )
        assertEquals("-<br>-<br>-<br>-<br>-<br>-<br>-<br>-", TfaPartyFormat.lobbyNames(emptyList()))
    }

    @Test
    fun `a member line matches the captured details`() {
        val line =
            TfaPartyFormat.member("USA VERY FAT", 125, listOf(99, 99, 99, 99, 99, 99, 90), 0, 0)
        assertEquals("USA VERY FAT|125|99|99|99|99|99|99|90|0|0", line)
    }
}
