package org.rsmod.content.raids.tfa

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusEncounter
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TfaCheatScript
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val ocellus: OcellusEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfaocellus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a fresh Ocellus instance and teleport in front of the arena entrance"
            cheat { toOcellus(phaseThree = false) }
        }
        onCommand("tfalobby") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to The Fractured Archive lobby"
            cheat { toLobby() }
        }
        onCommand("ocellus") {
            requiredRights = Rights.ADMINISTRATOR
            desc =
                "Ocellus testing: start | shield | p3 (fresh instance, teleport in and start at " +
                    "phase 3) | hp <percent> | leave"
            cheat { ocellusCommand() }
        }
    }

    private fun Cheat.ocellusCommand() {
        when (args.firstOrNull()) {
            "start" -> fight()?.ready(player) ?: player.mes("You aren't in an Ocellus room.")
            "shield" -> report(fight()?.setHealthPercent(0), "Shield dropped to 1 hp.")
            "p3" -> toOcellus(phaseThree = true)
            "leave" -> leave()
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) {
                    player.mes("Usage: ::ocellus hp <percent>")
                    return
                }
                report(fight()?.setHealthPercent(percent), "Boss health set to $percent%.")
            }
            else -> player.mes("Usage: ::ocellus start | shield | p3 | hp <percent> | leave")
        }
    }

    private fun Cheat.fight() = ocellus.at(player.coords)

    private fun Cheat.report(applied: Boolean?, message: String) {
        player.mes(if (applied == true) message else "Ocellus isn't in a fight. Use ::ocellus start first.")
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

    private fun Cheat.toLobby() {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            telejump(LOBBY_SPAWN, TeleportType.Exempt)
        }
    }

    private fun Cheat.toOcellus(phaseThree: Boolean) {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            when (val opened = ocellus.open(player)) {
                is OcellusEncounter.Opened.Failed -> mes(opened.reason)
                is OcellusEncounter.Opened.Room -> enter(opened, phaseThree)
            }
        }
    }

    private fun ProtectedAccess.enter(room: OcellusEncounter.Opened.Room, phaseThree: Boolean) {
        val fight = room.fight
        telejump(if (phaseThree) fight.phaseThreeSpot else room.enter, TeleportType.Exempt)
        manager.finalizeEntry(player, fight.session, clock.cycle)
        if (!phaseThree) return
        mes(if (fight.startAtPhaseThree(player)) "Starting Ocellus at phase 3." else "Couldn't start Ocellus.")
    }

    private companion object {
        val LOBBY_SPAWN = CoordGrid(4130, 5555, 0)
    }
}
