package org.rsmod.content.raids.tfa.raid.encounter.guardian

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GuardianCheats
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val guardian: GuardianEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfaguardian") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a fresh Archive Guardian instance and teleport in"
            cheat { toGuardian() }
        }
        onCommand("guardian") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Archive Guardian testing: start | special | shield | land | enrage | hp <percent> | leave"
            cheat { guardianCommand() }
        }
    }

    private fun Cheat.guardianCommand() {
        when (args.firstOrNull()) {
            "start" -> fight()?.ready(player) ?: player.mes("You aren't in an Archive Guardian room.")
            "shield",
            "land" -> report(fight()?.setHealthPercent(0), "Shield dropped to 1 hp.")
            "special" -> report(fight()?.forceSpecial(player), "Special triggered.")
            "enrage" -> report(fight()?.setHealthPercent(ENRAGE_PERCENT), "Health set to $ENRAGE_PERCENT%.")
            "leave" -> leave()
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) {
                    player.mes("Usage: ::guardian hp <percent>")
                    return
                }
                report(fight()?.setHealthPercent(percent), "Boss hp set to $percent%.")
            }
            else -> player.mes("Usage: ::guardian start | special | shield | land | enrage | hp <percent> | leave")
        }
    }

    private fun Cheat.fight() = guardian.at(player.coords)

    private fun Cheat.report(applied: Boolean?, message: String) {
        player.mes(if (applied == true) message else "The Guardian isn't in a fight. Use ::guardian start first.")
    }

    private fun Cheat.leave() {
        val session = manager.sessionForPlayer(player)
        if (session == null) {
            player.mes("You aren't in an instance.")
            return
        }
        val exit = manager.leave(player, session, clock.cycle)
        launcher.launch(player, busyText = "Close your dialog first.") {
            telejump(exit, TeleportType.Exempt)
        }
    }

    private fun Cheat.toGuardian() {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            when (val opened = guardian.open(player)) {
                is GuardianEncounter.Opened.Failed -> mes(opened.reason)
                is GuardianEncounter.Opened.Room -> enter(opened)
            }
        }
    }

    private fun ProtectedAccess.enter(room: GuardianEncounter.Opened.Room) {
        telejump(room.enter, TeleportType.Exempt)
        manager.finalizeEntry(player, room.fight.session, clock.cycle)
    }

    private companion object {
        const val ENRAGE_PERCENT = 30
    }
}
