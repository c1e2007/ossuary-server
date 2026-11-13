package org.rsmod.content.bosses.yama

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Developer commands for testing Yama (Commands.Yama.js), on the repo's admin command
 * registration: `::yamahp`, `::yamajudgehp`, `::yamajudgekill` and `::yamakill`. `::yamato`
 * enters the domain through the instance (the reference has no equivalent; it is the task's).
 */
internal class YamaCommands @Inject constructor(private val fights: YamaFights) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("yamato") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the Voice of Yama's ledge at the bottom of the Chasm of Fire"
            cheat { player.coords = YamaCommon.VOICE_RESPAWN }
        }
        onCommand("yamahp") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Set your Yama's hitpoints (::yamahp 1666 reaches the first phase change)"
            cheat {
                val yama = fights.fightOf(player)?.yama
                val hitpoints = args.getOrNull(0)?.toIntOrNull()
                if (yama == null || yama.hitpoints <= 0) {
                    player.mes("You are not fighting Yama.")
                } else if (hitpoints == null || hitpoints < 1) {
                    player.mes("Use ::yamahp <hitpoints>.")
                } else {
                    yama.hitpoints = hitpoints
                    player.mes("Yama now has $hitpoints hitpoints.")
                }
            }
        }
        onCommand("yamajudge") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Start the next phase change at Yama's next attack"
            cheat {
                val fight = fights.fightOf(player)
                if (fight == null || fight.state != YamaState.FIGHTING || fight.phase >= 3) {
                    player.mes("You are not in phase 1 or 2 of the fight.")
                } else {
                    fight.pendingTransition = true
                    player.mes("The Judge comes at Yama's next attack.")
                }
            }
        }
        onCommand("yamaphase") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Skip to phase 2 or 3 via the Judge: ::yamaphase <2-3>"
            cheat {
                val fight = fights.fightOf(player)
                val target = args.getOrNull(0)?.toIntOrNull()
                if (fight == null || fight.state != YamaState.FIGHTING || target !in 2..3) {
                    player.mes("Use ::yamaphase <2-3> while fighting Yama.")
                } else {
                    fight.phase = target!! - 1
                    fight.yama?.hitpoints = YamaCommon.THRESHOLDS[target - 2]
                    fight.pendingTransition = true
                    player.mes("The Judge comes at Yama's next attack; then phase $target.")
                }
            }
        }
        onCommand("yamajudgehp") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Set the Judge of Yama's hitpoints"
            cheat {
                val judge = fights.fightOf(player)?.judge?.npc
                val hitpoints = args.getOrNull(0)?.toIntOrNull()
                if (judge == null || hitpoints == null || hitpoints < 1) {
                    player.mes("Use ::yamajudgehp <hitpoints> while facing the Judge.")
                } else {
                    judge.hitpoints = hitpoints
                    player.mes("The Judge of Yama now has $hitpoints hitpoints.")
                }
            }
        }
        onCommand("yamajudgekill") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Kill the Judge of Yama"
            cheat {
                val judge = fights.fightOf(player)?.judge?.npc
                if (judge == null) {
                    player.mes("You are not facing the Judge of Yama.")
                } else {
                    judge.hitpoints = 0
                }
            }
        }
        onCommand("yamakill") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Finish your Yama off"
            cheat {
                val yama = fights.fightOf(player)?.yama
                if (yama == null || yama.hitpoints <= 0) {
                    player.mes("You are not fighting Yama.")
                } else {
                    yama.hitpoints = 1
                }
            }
        }
    }
}
