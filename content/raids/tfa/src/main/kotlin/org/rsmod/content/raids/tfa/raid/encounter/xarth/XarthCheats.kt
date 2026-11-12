package org.rsmod.content.raids.tfa.raid.encounter.xarth

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

class XarthCheats
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val xarth: XarthEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfaxarth") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a fresh Xarth instance and teleport in"
            cheat { toXarth() }
        }
        onCommand("xarth") {
            requiredRights = Rights.ADMINISTRATOR
            desc = USAGE
            cheat { xarthCommand() }
        }
    }

    private fun Cheat.xarthCommand() {
        val fight = xarth.at(player.coords)
        if (fight == null) {
            player.mes("You aren't in a Xarth room.")
            return
        }
        when (args.firstOrNull()) {
            "start" -> fight.ready(player)
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) player.mes("Usage: ::xarth hp <percent>") else applied(fight.setHealthPercent(percent))
            }
            "wave" -> {
                val wave = args.getOrNull(1)?.toIntOrNull()
                if (wave == null) player.mes("Usage: ::xarth wave <1-3>") else applied(fight.forceWave(wave))
            }
            "p2" -> fight.run(player, "p2_start")
            "heal" -> {
                val amount = args.getOrNull(1)?.toIntOrNull()
                if (amount == null) player.mes("Usage: ::xarth heal <hp>") else fight.addHealed(amount)
            }
            "debug" -> fight.debugState().forEach(player::mes)
            "leave" -> leave()
            else -> player.mes("Usage: $USAGE")
        }
    }

    private fun Cheat.applied(done: Boolean) {
        player.mes(if (done) "Done." else "Xarth isn't in a fight. Use ::xarth start first.")
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

    private fun Cheat.toXarth() {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            when (val opened = xarth.open(player)) {
                is XarthEncounter.Opened.Failed -> mes(opened.reason)
                is XarthEncounter.Opened.Room -> enter(opened)
            }
        }
    }

    private fun ProtectedAccess.enter(room: XarthEncounter.Opened.Room) {
        telejump(room.enter, TeleportType.Exempt)
        manager.finalizeEntry(player, room.fight.session, clock.cycle)
    }

    private companion object {
        const val USAGE = "Xarth testing: start | hp <pct> | wave <1-3> | p2 | heal <hp> | debug | leave"
    }
}
