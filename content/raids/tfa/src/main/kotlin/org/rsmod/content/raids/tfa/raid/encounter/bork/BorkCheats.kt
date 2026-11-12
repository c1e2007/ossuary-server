package org.rsmod.content.raids.tfa.raid.encounter.bork

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

class BorkCheats
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val bork: BorkEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfabork") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a fresh Bork instance and teleport in"
            cheat { toBork() }
        }
        onCommand("bork") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Bork testing: start | tempo <0-3> | daze | stomp | smorks <pct> | bork <pct> | debug | leave"
            cheat { borkCommand() }
        }
    }

    private fun Cheat.borkCommand() {
        val fight = bork.at(player.coords)
        if (fight == null) {
            player.mes("You aren't in a Bork room.")
            return
        }
        when (args.firstOrNull()) {
            "start" -> fight.ready(player)
            "tempo" -> {
                val tempo = args.getOrNull(1)?.toIntOrNull()
                if (tempo == null) player.mes("Usage: ::bork tempo <0-3>") else fight.setTempo(tempo)
            }
            "daze" -> fight.run(player, "daze")
            "stomp" -> fight.run(player, "stomp")
            "smorks" -> percent(fight.setSmorkPercent(percentArg() ?: return), "Smorks")
            "bork" -> percent(fight.setBorkPercent(percentArg() ?: return), "Bork")
            "debug" -> fight.debugState().forEach(player::mes)
            "leave" -> leave()
            else ->
                player.mes(
                    "Usage: ::bork start | tempo <0-3> | daze | stomp | smorks <pct> | bork <pct> | debug | leave"
                )
        }
    }

    private fun Cheat.percentArg(): Int? {
        val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
        if (percent == null) player.mes("Usage: ::bork <smorks|bork> <percent>")
        return percent
    }

    private fun Cheat.percent(applied: Boolean, who: String) {
        player.mes(if (applied) "$who hp set." else "Bork isn't in a fight. Use ::bork start first.")
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

    private fun Cheat.toBork() {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            when (val opened = bork.open(player)) {
                is BorkEncounter.Opened.Failed -> mes(opened.reason)
                is BorkEncounter.Opened.Room -> enter(opened)
            }
        }
    }

    private fun ProtectedAccess.enter(room: BorkEncounter.Opened.Room) {
        telejump(room.enter, TeleportType.Exempt)
        manager.finalizeEntry(player, room.fight.session, clock.cycle)
    }
}
