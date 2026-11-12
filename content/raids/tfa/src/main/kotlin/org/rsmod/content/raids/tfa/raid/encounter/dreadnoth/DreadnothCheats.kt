package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

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

class DreadnothCheats
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val dreadnoth: DreadnothEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfadreadnoth") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a fresh Dreadnoth instance and teleport in"
            cheat { toDreadnoth() }
        }
        onCommand("dreadnoth") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Dreadnoth testing: start | wave <1-3> | enrage | hp <percent> | leave"
            cheat { dreadnothCommand() }
        }
    }

    private fun Cheat.dreadnothCommand() {
        when (args.firstOrNull()) {
            "start" -> fight()?.ready(player) ?: player.mes("You aren't in a Dreadnoth room.")
            "wave" -> {
                val wave = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..WAVES }
                if (wave == null) {
                    player.mes("Usage: ::dreadnoth wave <1-$WAVES>")
                    return
                }
                report(fight()?.forceWave(wave), "Wave $wave queued.")
            }
            "debug" ->
                fight()?.debugState()?.forEach(player::mes)
                    ?: player.mes("[debug] No Dreadnoth room at ${player.coords}.")
            "enrage" -> report(fight()?.forceEnrage(), "Enrage queued.")
            "leave" -> leave()
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) {
                    player.mes("Usage: ::dreadnoth hp <percent>")
                    return
                }
                report(fight()?.setHealthPercent(percent), "Boss hp set to $percent%.")
            }
            else -> player.mes("Usage: ::dreadnoth start | wave <1-$WAVES> | enrage | hp <percent> | debug | leave")
        }
    }

    private fun Cheat.fight() = dreadnoth.at(player.coords)

    private fun Cheat.report(applied: Boolean?, message: String) {
        player.mes(if (applied == true) message else "Dreadnoth isn't in a fight. Use ::dreadnoth start first.")
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

    private fun Cheat.toDreadnoth() {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            when (val opened = dreadnoth.open(player)) {
                is DreadnothEncounter.Opened.Failed -> mes(opened.reason)
                is DreadnothEncounter.Opened.Room -> enter(opened)
            }
        }
    }

    private fun ProtectedAccess.enter(room: DreadnothEncounter.Opened.Room) {
        telejump(room.enter, TeleportType.Exempt)
        manager.finalizeEntry(player, room.fight.session, clock.cycle)
    }

    private companion object {
        const val WAVES = 3
    }
}
