package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Yama on his throne (Throne.Yama.js, docs/yama.md): Talk-to, the contract and Challenge.
 *
 * Challenge is the engage gate. Before a signature Yama asks for one first; after it the fight
 * starts at once. The dialogue is the Wiki transcript's. Contracts beyond the first are skipped
 * (the "Actually, I have some questions" branch and the hiscores record), as the task says.
 */
internal class YamaThrone @Inject constructor(private val fights: YamaFights) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(YamaIds.YAMA_OCCUPIED) { startDialogue(it.npc) { talk() } }
        onOpNpc3(YamaIds.YAMA_OCCUPIED) { challenge() }
    }

    private suspend fun ProtectedAccess.challenge() {
        val fight = fights.pending(player)
        if (fight == null) {
            mes("Yama is not here to be challenged.")
            return
        }
        if (!player.contractSigned) {
            val throne = fight.throne
            if (throne != null) startDialogue(throne) { challengeUnsigned() }
            return
        }
        fights.engage(player)
    }

    private suspend fun Dialogue.talk() {
        if (!player.contractSigned) {
            if (player.contractOffered) {
                chatNpc(
                    neutral,
                    "You are back. Excellent. I have little time for cowards. If you are ready, " +
                        "I will retrieve the contract.",
                )
                offerContract()
            } else {
                firstMeeting()
            }
            return
        }
        when (player.lastYamaResult) {
            YamaResult.VICTORIOUS -> chatNpc(neutral, pick(VICTORIOUS_LINES))
            YamaResult.DEFEATED -> chatNpc(neutral, pick(DEFEATED_LINES))
            YamaResult.ESCAPED -> chatNpc(neutral, pick(ESCAPED_LINES))
            YamaResult.NONE -> Unit
        }
        if (player.lastYamaResult != YamaResult.NONE) {
            player.lastYamaResult = YamaResult.NONE
            chatNpc(neutral, "In any case, ${player.displayName}, have you come to challenge me?")
        } else {
            chatNpc(neutral, "Welcome, ${player.displayName}. Have you come to challenge me?")
        }
        val start = choice2("Yes. Let's do this.", true, "No, I'd rather not.", false)
        if (start) {
            chatPlayer(neutral, "Yes. Let's do this.")
            fights.engageFromDialogue(player)
        } else {
            chatPlayer(neutral, "No, I'd rather not.")
            chatNpc(
                neutral,
                "I see. Perhaps you are just here to waste my time then? Return when you are not.",
            )
        }
    }

    private suspend fun Dialogue.challengeUnsigned() {
        chatNpc(
            neutral,
            "Ah... Yes, let us not get bogged down in the minutiae. Before we start, a quick " +
                "contract. Nothing but a formality.",
        )
        offerContract()
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(
            neutral,
            "Ah... ${player.displayName}, welcome. I would offer you some water, but we don't " +
                "get much of it down here.",
        )
        chatPlayer(neutral, "Thanks...")
        chatNpc(neutral, "I take it you have questions.")
        val choice =
            choice3(
                "You know why I'm here. Stop messing around.",
                1,
                "I take it you have answers.",
                2,
                "Yes, a few of them actually.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "You know why I'm here. Stop messing around.")
                chatNpc(
                    neutral,
                    "I do, but it is important that it is you who is asking for information, not " +
                        "me that is offering it.",
                )
                chatPlayer(neutral, "Okay... How are you still communicating with your followers?")
            }
            2 -> {
                chatPlayer(neutral, "I take it you have answers.")
                chatNpc(neutral, "I do, but I rarely share them umprompted.")
                chatPlayer(neutral, "Okay, how are you still communicating with your followers?")
            }
            else -> {
                chatPlayer(
                    neutral,
                    "Yes, a few of them actually. How are you still communicating with your " +
                        "followers?",
                )
            }
        }
        chatNpc(neutral, "Why, through my voice, of course.")
        chatPlayer(
            neutral,
            "You know what I mean. How are you still communicating with your voice?",
        )
        chatNpc(neutral, "The same way I always have.")
        chatPlayer(
            neutral,
            "But the dark nullifier... I thought it was supposed to sever your connection.",
        )
        chatNpc(
            neutral,
            "Hmm... Yes, if anything could have done so, it would have been the power of the " +
                "Dark Altar.",
        )
        chatNpc(
            neutral,
            "However, when your opponents have only one angle of approach, it makes them so " +
                "predictable.",
        )
        chatPlayer(neutral, "You knew we would try to use it?")
        chatNpc(
            neutral,
            "Yes. When I heard you would be hand delivering me a connection to the Dark " +
                "Altar... Why, I was overjoyed.",
        )
        chatPlayer(
            neutral,
            "But... I remember planting it! The plan went off without a hitch!",
        )
        chatNpc(neutral, "I do not doubt that you remember it that way.")
        chatPlayer(neutral, "What did you do?!")
        chatNpc(
            neutral,
            "A simple transfiguration of the mind. My disciples granted you a memory of success. " +
                "A fitting payment for your gratious[sic] service.",
        )
        chatPlayer(neutral, "You modified my memory?!")
        chatNpc(
            neutral,
            "Yes. And I have now answered five of your questions. I believe my generosity has " +
                "been both demonstrated and exhausted.",
        )
        chatPlayer(
            neutral,
            "You call that gloating generosity? If you didn't want something from me, I wouldn't " +
                "still be down here. Get to the point.",
        )
        chatNpc(
            neutral,
            "Hmm... Perhaps you are more astute than I thought. Very well, there is something " +
                "that I want.",
        )
        chatPlayer(neutral, "Well you won't be getting it from me!")
        chatNpc(
            neutral,
            "I am Yama. Every mortal desires something of mine. Ancient armaments? Hidden " +
                "knowledge? Or perhaps a taste of revenge...",
        )
        chatNpc(neutral, "These things could be yours.")
        chatNpc(
            neutral,
            "Face me in combat. If you win, you take it all. Armaments, knowledge, revenge.",
        )
        chatPlayer(neutral, "And if I lose?")
        chatNpc(neutral, "I take your soul. For all of eternity.")
        chatPlayer(neutral, "No thanks.")
        chatNpc(neutral, "I see. Perhaps we could lower the stakes then.")
        chatNpc(
            neutral,
            "I will face you in an inhibited form. I'll even allow you to bring an ally, " +
                "provided they have entered the same contract.",
        )
        chatNpc(
            neutral,
            "If you win, I will give you a chance to gain some of my treasures, and of course, " +
                "you will have sampled your revenge.",
        )
        chatPlayer(neutral, "And if I lose?")
        chatNpc(
            neutral,
            "When you take your final rest, I shall take your soul for a year. A drop in the " +
                "ocean compared to eternity.",
        )
        chatPlayer(neutral, "How can I know you'll keep your word?")
        chatNpc(
            neutral,
            "The same way I will know you shall keep yours. A binding contract. As luck would " +
                "have it, I had one of my scribes draft one up for just such an occasion.",
        )
        chatPlayer(neutral, "...Okay.")
        offerContract()
    }

    /** "Sign right here." and the two-step confirmation. */
    private suspend fun Dialogue.offerContract() {
        chatNpc(neutral, "Sign right here.")
        player.contractOffered = true
        val sign = choice2("Yes.", true, "No.", false, title = "Sign the contract?")
        if (!sign) {
            declineContract()
            return
        }
        val sure =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Are you sure? A contract with Yama cannot be backed out of.",
            )
        if (!sure) {
            declineContract()
            return
        }
        player.contractSigned = true
        chatNpc(
            neutral,
            "Excellent... Challenge me when you are ready, ${player.displayName}. I very much " +
                "look forward to it.",
        )
    }

    private suspend fun Dialogue.declineContract() {
        chatPlayer(neutral, "I'm not sure I want to...")
        chatNpc(
            neutral,
            "Of course. There is no rush. Leave, with your tail tucked between your legs. If " +
                "you grow a spine and wish to fulfil your revenge, I will be waiting.",
        )
    }

    private fun pick(lines: List<String>): String = lines.random()

    private companion object {
        val VICTORIOUS_LINES =
            listOf(
                "I hope you are finding the spoils from my treasury to your liking. There are " +
                    "plenty more for you to claim.",
                "Your victories mount. You are getting better at this.",
                "A victory earned. But there is room for improvement, is there not?",
                "Here we go again. I suppose it is entertaining watching you desperately guzzle " +
                    "potions, at the very least.",
                "I must once again commend your strategy. Employing tactics so boring that they " +
                    "put your opponents to sleep is very novel.",
                "Back again I see. I can see how you find these bouts exhilarating, being such a " +
                    "feeble opponent.",
                "Your willingness to wager your soul continues to surprise. I suppose you think " +
                    "of it even less worth than I do.",
                "Honestly, I expected more. That it takes you as long as it does to defeat my " +
                    "inhibited form is rather embarrassing.",
                "You look happy with yourself. How endearing.",
                "You return once more. Excellent.",
                "Ah, you have returned. Your competence matches only your greed.",
            )
        val DEFEATED_LINES =
            listOf(
                "Don't let a single defeat set you back. You could have so many more.",
                "My treasury remains over capacity. And here I was hoping to be relieved of " +
                    "some of its contents.",
                "What a shame for our last bout to have ended that way! Maybe you will do " +
                    "better next time.",
                "Back after being defeated so quickly? I suppose what you lack in skill you " +
                    "make up for with perseverance.",
                "Some losses are unavoidable. Yours, of course, was not.",
                "Your donation is greatly appreciated.",
                "Defeat finds you. Of course, that is hardly unexpected.",
            )
        val ESCAPED_LINES =
            listOf(
                "If you are struggling to achieve victory, I suppose avoiding defeat is the " +
                    "best you can do.",
                "You must be tired, having to run all the way back here.",
                "Back for more? Well, you can always escape again I suppose.",
                "Excellent work evading. Next time, try fighting back.",
                "Our last bout was cut short. Perhaps the next will be more fruitful.",
                "Back again? Watching you escape in such a hurry, I thought you might never " +
                    "return.",
                "Ah, I see your legs have stopped quivering. Perhaps next time you will fight " +
                    "to the end.",
                "I hear adventurers like to boast about their feats. I wonder if people realise " +
                    "how readily they run at the first sight of risk.",
                "A well executed retreat. You must be very practiced.",
                "There is no shame in defeat. There is plenty to be found in your cowardice, " +
                    "however.",
            )
    }
}
