package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The way in (Voice.Yama.js, and the Chasm part of docs/yama.md): the Chasm of Fire's way down, the
 * Voice of Yama, its Talk-to / Travel / Join ops and its first-meeting and returning dialogue.
 *
 * The Voice itself is already placed by `.data/raw-cache/map/npcs/chasm_of_fire.toml` (plane 1 in
 * that file; the reference stands it on plane 0), so no second spawn is registered here.
 * Skipped, as the task says: party and contract handling.
 */
internal class YamaVoice @Inject constructor(private val gate: YamaEntryGate) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(YamaIds.CHASM_ROPE) { goDown() }

        for (voice in VOICES) {
            onOpNpc1(voice) { startDialogue(it.npc) { talk() } }
            onOpNpc3(voice) { travel() }
            onOpNpc4(voice) { join() }
        }
    }

    /** The pick-up animation a tick after arriving, then the move to the Chasm's top floor. */
    private suspend fun ProtectedAccess.goDown() {
        anim(YamaIds.SEQ_PICKUP)
        delay(1)
        delay(1)
        telejump(CHASM_TOP_FLOOR)
    }

    private suspend fun Dialogue.talk() {
        if (!canEnter(player)) {
            mes("You need to complete A Kingdom Divided before entering Yama's Domain.")
            return
        }
        if (player.voiceProgress < VOICE_MET) {
            chatNpc(neutral, "The arrogant human finally arrives...")
            chatPlayer(neutral, "Upset that I disrupted your plans?")
            chatNpc(
                neutral,
                "Disgusting. My master, the great demon Yama, wishes to speak with you.",
            )
            chatPlayer(
                neutral,
                "Yama wants to speak with me? How do you know that? You shouldn't even be able " +
                    "to talk to him.",
            )
            chatNpc(
                neutral,
                "My master wishes to have the satisfaction of explaining that to you himself. " +
                    "For this, I envy him. I will make do with the idiotic expression you wear " +
                    "presently.",
            )
            chatPlayer(
                neutral,
                "I planted the device! Your connection should be severed!",
            )
            chatNpc(
                neutral,
                "Silence, human! You will make no demands of me. Proceed to my master's domain " +
                    "and speak with him yourself if you desire an answer.",
            )
        } else {
            chatNpc(
                neutral,
                "Proceed or begone, human. I will not bear your foul presence any longer than is " +
                    "necessary.",
            )
        }
        // The first talk is over either way once its menu comes up (docs/yama.md).
        if (player.voiceProgress < VOICE_MET) player.voiceProgress = VOICE_MET
        val enter = choice2("Enter Yama's domain.", true, "Stay.", false)
        if (enter) {
            access.enterDomain()
        } else if (player.voiceProgress >= VOICE_MET) {
            chatPlayer(neutral, "I think I'd rather stay here.")
            chatNpc(neutral, "Weakness! Cowardice! Disgusting. Begone, human!")
        }
    }

    private suspend fun ProtectedAccess.travel() {
        if (!canEnter(player)) {
            mes("You need to complete A Kingdom Divided before entering Yama's Domain.")
            return
        }
        enterDomain()
    }

    /** Duo fights aren't supported; the line is the reference's own. */
    private suspend fun ProtectedAccess.join() {
        mes("Yama does not yet accept challengers in pairs. Return alone, mortal.")
    }

    private suspend fun ProtectedAccess.enterDomain() {
        gate.enter?.invoke(this)
    }

    private fun canEnter(player: org.rsmod.game.entity.Player): Boolean =
        QuestRequirements.hasCompleted(player, REQUIRED_QUEST)

    private companion object {
        private val VOICES =
            listOf(YamaIds.VOICE, YamaIds.VOICE_1OP, YamaIds.VOICE_2OP, YamaIds.VOICE_3OP)

        private val CHASM_TOP_FLOOR = org.rsmod.map.CoordGrid(1435, 10077, 2)

        private const val VOICE_MET = 2

        /** Assumed quest key: A Kingdom Divided is not implemented as a quest on this server. */
        private const val REQUIRED_QUEST = "quest_akd"
    }
}
