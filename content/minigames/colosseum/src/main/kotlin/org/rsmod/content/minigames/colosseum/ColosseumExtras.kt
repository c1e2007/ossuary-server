package org.rsmod.content.minigames.colosseum

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private val KILLS_KEY = AttributeKey<Int>(persistenceKey = "colosseum_sol_kills")
private val DEATHS_KEY = AttributeKey<Int>(persistenceKey = "colosseum_deaths")
private val BEST_KEY = AttributeKey<Int>(persistenceKey = "colosseum_best_ticks")

internal var Player.colosseumKills: Int
    get() = attr[KILLS_KEY] ?: 0
    set(value) {
        attr[KILLS_KEY] = value
    }

internal var Player.colosseumDeaths: Int
    get() = attr[DEATHS_KEY] ?: 0
    set(value) {
        attr[DEATHS_KEY] = value
    }

internal var Player.colosseumBestTicks: Int
    get() = attr[BEST_KEY] ?: 0
    set(value) {
        attr[BEST_KEY] = value
    }

internal fun formatTicks(ticks: Int): String {
    if (ticks <= 0) return "-"
    val centis = ticks * 60
    val minutes = centis / 6000
    val seconds = (centis % 6000) / 100
    return "%d:%02d.%02d".format(minutes, seconds, centis % 100)
}

internal class ColosseumExtras
@Inject
constructor(private val runs: ColosseumRuns, private val rewards: ColosseumRewards) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.colosseum_scoreboard") { viewStats() }
        onOpLoc2("loc.colosseum_scoreboard") { viewStats() }
        onOpLoc3("loc.colosseum_scoreboard") { viewGlory() }

        onCommand("colosseum") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the Colosseum lobby"
            cheat { player.coords = Colosseum.LOBBY }
        }
        onCommand("colokill") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Kill the active Colosseum wave"
            cheat {
                val run = runs.runFor(player)
                if (run == null || run.stage != RunStage.WAVE) {
                    player.mes("Start a wave first.")
                } else {
                    for (npc in run.npcs) npc.hitpoints = 0
                }
            }
        }
        onCommand("cologlory") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Set your best Glory"
            cheat {
                player.colosseumGlory = args[0].toInt().coerceAtLeast(0)
                player.mes("Your Glory is now ${player.colosseumGlory} (${player.colosseumRank}).")
            }
        }
        onCommand("colowave") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Set the next Colosseum wave (1-12)"
            cheat {
                val run = runs.runFor(player)
                val wave = args[0].toInt()
                if (run == null || run.stage != RunStage.INTERMISSION) {
                    player.mes("Use this between waves, in a run.")
                } else if (wave !in 1..Colosseum.FINAL_WAVE) {
                    player.mes("Use ::colowave <1-${Colosseum.FINAL_WAVE}>.")
                } else {
                    run.wave = wave - 1
                    run.loot?.future = rewards.roll(wave, player, run.random)
                    player.mes("The next wave is wave $wave.")
                }
            }
        }
        onCommand("colomod") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Set a Colosseum modifier tier, e.g. ::colomod solarflare 3"
            cheat {
                val run = runs.runFor(player)
                val modifier =
                    ColosseumModifier.entries.firstOrNull {
                        it.name.replace("_", "").equals(args[0].replace("-", "").replace("_", ""), true)
                    }
                val tier = args.getOrNull(1)?.toInt() ?: 1
                if (run == null || run.stage != RunStage.INTERMISSION) {
                    player.mes("Use this between waves, in a run.")
                } else if (modifier == null || tier !in 0..3) {
                    player.mes("Use ::colomod <${ColosseumModifier.entries.joinToString("|") { it.name.lowercase() }}> <0-3>.")
                } else {
                    if (tier == 0) run.modifiers.tiers.remove(modifier) else run.modifiers.tiers[modifier] = if (modifier.tiered) tier else 1
                    player.setModifierTier(modifier, run.modifiers.tier(modifier))
                    player.mes("${modifier.name.lowercase()} is now tier ${run.modifiers.tier(modifier)}.")
                }
            }
        }
    }

    private fun ProtectedAccess.viewStats() {
        ifOpenMainModal("interface.colosseum_scoreboard")
        ifSetHide("component.colosseum_scoreboard:glory_content", true)
        ifSetHide("component.colosseum_scoreboard:stats_content", false)
        ifSetText("component.colosseum_scoreboard:title_text", "Colosseum Statistics")
        ifSetText("component.colosseum_scoreboard:pkc2_content", player.colosseumWavesCompleted.toString())
        ifSetText("component.colosseum_scoreboard:pkc_content", player.colosseumKills.toString())
        ifSetText("component.colosseum_scoreboard:pd_content", player.colosseumDeaths.toString())
        ifSetText("component.colosseum_scoreboard:pbt_content", formatTicks(player.colosseumBestTicks))
        for (world in WORLD_COMPONENTS) ifSetText("component.colosseum_scoreboard:$world", "-")
    }

    private fun ProtectedAccess.viewGlory() {
        ifOpenMainModal("interface.colosseum_scoreboard")
        ifSetHide("component.colosseum_scoreboard:glory_content", false)
        ifSetHide("component.colosseum_scoreboard:stats_content", true)
        val glory = player.colosseumGlory
        ifSetText("component.colosseum_scoreboard:glory_title_text", player.colosseumRank)
        ifSetText("component.colosseum_scoreboard:glory_number_text", "%,d".format(glory))
        ifSetText("component.colosseum_scoreboard:glory_number_text_global", "%,d".format(glory))
        ifSetText("component.colosseum_scoreboard:glory_rewards_unlock_1", perks(PERKS_FIRST, glory))
        ifSetText("component.colosseum_scoreboard:glory_rewards_unlock_2", perks(PERKS_SECOND, glory))
    }

    private fun perks(perks: List<Pair<Int, String>>, glory: Int): String =
        perks
            .mapIndexed { index, (required, text) ->
                val line = "- $text" + if (index < perks.size - 1) "<br>" else ""
                if (glory >= required) line else "<col=999999>$line</col>"
            }
            .joinToString("")

    private companion object {
        val WORLD_COMPONENTS = listOf("gkc2_content", "gkc_content", "gd_content", "gbt_content")
        val PERKS_FIRST =
            listOf(
                2000 to "Bank access at the Colosseum.",
                5000 to "Fortis respawn point.",
                8000 to "Improved price for stolen goods.",
                12000 to "Colosseum Ring of Dueling teleport.",
            )
        val PERKS_SECOND = listOf(16000 to "Fortis herb patch protection.", 20000 to "Fortis Salute emote.")
    }
}
