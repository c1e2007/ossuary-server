package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.ocellusMet by intVarBit("varbit.tfa_ocellus_met")

class OcellusIntroScript @Inject constructor(private val encounter: OcellusEncounter) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.tfa_ocellus_intro") { talkTo(it.npc) }
        onOpNpc3("npc.tfa_ocellus_intro") { encounter.of(it.npc)?.ready(player) }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) {
            if (player.ocellusMet == 0) {
                introduction()
                player.ocellusMet = 1
            }
            readyPrompt(npc)
        }
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(confused, "You... Who are you? And how did you get in here?")
        chatNpc(neutral, "Human... You stand before Ocellus... Chosen... of Guthix...")
        chatPlayer(confused, "You serve Guthix?")
        chatNpc(
            neutral,
            "Yes... He charged me... to guard the source of his power... " +
                "But his enemies approach... They are not worthy of it...",
        )
        chatPlayer(
            neutral,
            "Well, then we're on the same side. I'm part of an alliance defending this " +
                "place. The danger is mostly gone though. We held off the attackers.",
        )
        chatNpc(
            neutral,
            "No... Your alliance... is false... Armadyl... Saradomin... They are no " +
                "better... than the rest... They are not worthy... of this power...",
        )
        chatPlayer(shocked, "We don't want it! We just want to keep this place safe!")
        chatNpc(
            neutral,
            "Wrong... Many try... but none can resist... You will try to take it... His blade...",
        )
        chatNpc(
            neutral,
            "There is only one solution... Only Guthix himself is worthy... but he is long gone...",
        )
        chatNpc(neutral, "To keep his power safe... I will take his place...")
        chatNpc(neutral, "The time has come... for ascension.")
    }

    private suspend fun Dialogue.readyPrompt(npc: Npc) {
        val ready =
            choice2("Yes.", true, "No.", false, title = "Are you ready for the final challenge?")
        if (ready) {
            encounter.of(npc)?.ready(player)
        }
    }
}
