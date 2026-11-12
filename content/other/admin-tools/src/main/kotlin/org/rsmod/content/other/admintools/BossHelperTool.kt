package org.rsmod.content.other.admintools

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The boss helper tool (the beta-world item, cache id 3667). Its "Debug" option opens a menu:
 * - Teleports: a list of bosses and teleport straight to them.
 * - Swap spellbook, Max stats, Reset stats, Unlock everything, All gear: run the matching
 *   `::command` (`::spawn` is the gear table).
 *
 * Admin only, like the other admin items. Not done yet: entry items and slayer tasks on teleport,
 * the gear loadouts.
 */
class BossHelperTool @Inject constructor(private val commands: CheatCommandMap) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(TOOL) { if (requireAdmin()) mainMenu() }
    }

    private suspend fun ProtectedAccess.mainMenu() {
        when (menu("Boss helper tool", false, OPTIONS)) {
            0 -> teleportMenu()
            1 -> spellbookMenu()
            2 -> run("master")
            3 -> run("reset")
            4 -> run("completeall")
            5 -> run("spawn")
        }
    }

    private suspend fun ProtectedAccess.teleportMenu() {
        val choice = menu("Boss list", false, BOSSES.map { it.name })
        val boss = BOSSES.getOrNull(choice) ?: return
        mes("Teleporting to ${boss.name}.")
        telejump(boss.coord, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.spellbookMenu() {
        val choice = menu("Swap spellbook", false, SPELLBOOKS)
        val book = SPELLBOOKS.getOrNull(choice) ?: return
        run("spellbook", book.lowercase())
    }

    /** Must stay the last thing a menu does: a command may start a script of its own. */
    private fun ProtectedAccess.run(name: String, vararg args: String) {
        commands.execute(player, name, args.toList())
    }

    private class Boss(val name: String, x: Int, z: Int, level: Int = 0) {
        val coord = CoordGrid(x, z, level)
    }

    private companion object {
        /** The item's gameval symbol is `viking_toyship` (id 3667); the cache names it. */
        const val TOOL = "obj.viking_toyship"

        val OPTIONS = listOf("Teleports", "Swap spellbook", "Max stats", "Reset stats", "Unlock everything", "All gear")
        val SPELLBOOKS = listOf("Standard", "Ancients", "Lunars", "Arceuus")

        // Coordinates are from memory and unchecked in-game: confirm each with ::mypos.
        val BOSSES =
            listOf(
                Boss("Barrows", 3565, 3289),
                Boss("Chambers of Xeric", 1233, 3573),
                Boss("Corporeal Beast", 2964, 4382, 2),
                Boss("Ferox Enclave", 3130, 3631),
                Boss("Giant Mole", 1752, 5237),
                Boss("Kalphite Queen", 3228, 3109),
                Boss("King Black Dragon", 2271, 4680),
                Boss("TzHaar Fight Cave", 2438, 5168),
                Boss("Theatre of Blood", 3677, 3219),
                Boss("Vorkath", 2272, 4054),
                Boss("Wintertodt", 1630, 3962),
                Boss("Zulrah", 2268, 3070),
            )
    }
}
