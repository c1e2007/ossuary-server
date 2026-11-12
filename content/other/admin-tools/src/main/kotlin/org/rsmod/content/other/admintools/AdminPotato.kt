package org.rsmod.content.other.admintools

import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rotten potato, an admin's tool:
 * - Eat: every registered `::command`, grouped. The list is read from the command map at runtime,
 *   so new commands appear by themselves; a name no group claims lands in "Other".
 * - Slice: saved teleports (go, add, delete).
 * - Peel: save the current tile as a teleport (auto-named, nothing to type).
 * - Mash: run the last command again.
 *
 * Teleports are saved on the account, so they survive logouts and restarts.
 */
class AdminPotato @Inject constructor(private val commands: CheatCommandMap) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(POTATO) { if (requireAdmin()) commandMenu() }
        onOpHeld2(POTATO) { if (requireAdmin()) teleportMenu() }
        onOpHeld3(POTATO) { if (requireAdmin()) addTeleport() }
        onOpHeld4(POTATO) { if (requireAdmin()) repeatLast() }
    }

    // ------------------------------------------------------------------------------- commands

    private suspend fun ProtectedAccess.commandMenu() {
        val groups = commands.commands.keys.groupBy(::groupOf).mapValues { (_, names) -> names.sorted() }
        val titles = (GROUPS.map { it.title } + OTHER).filter(groups::containsKey)
        while (true) {
            val index = pick("Admin commands", titles.map { "$it (${groups.getValue(it).size})" }) ?: return
            val names = groups.getValue(titles[index])
            val choice = pick(titles[index], names.map(::commandLabel), back = true) ?: continue
            runCommand(names[choice])
            return
        }
    }

    private fun commandLabel(name: String): String {
        val desc = commands[name]?.desc.orEmpty()
        return clean(if (desc.isEmpty()) "::$name" else "::$name - $desc").take(MENU_TEXT_LIMIT)
    }

    private fun ProtectedAccess.runCommand(name: String) {
        mes(clean("::$name - ${commands[name]?.desc.orEmpty()}"))
        execute(name, emptyList())
    }

    private fun ProtectedAccess.repeatLast() {
        val last = player.attr[LAST_COMMAND]
        if (last == null) {
            mes("No command run yet this session.")
            return
        }
        val parts = last.split(' ')
        execute(parts.first(), parts.drop(1))
    }

    /** Must stay the last thing a menu does: a command may start a script of its own. */
    private fun ProtectedAccess.execute(name: String, args: List<String>) {
        player.attr[LAST_COMMAND] = (listOf(name) + args).joinToString(" ")
        commands.execute(player, name, args)
    }

    private fun groupOf(name: String): String =
        GROUPS.firstOrNull { name in it.names || it.prefixes.any(name::startsWith) }?.title ?: OTHER

    // ------------------------------------------------------------------------------- teleports

    private suspend fun ProtectedAccess.teleportMenu() {
        val saved = player.savedTeleports()
        val picked = pick("Saved teleports", listOf(ADD, DELETE) + saved.map(SavedTeleport::label)) ?: return
        when (picked) {
            0 -> addTeleport()
            1 -> deleteTeleport(saved)
            else -> telejump(saved[picked - 2].coord, TeleportType.Exempt)
        }
    }

    private fun ProtectedAccess.addTeleport() {
        val here = player.coords
        val name = "Tile ${here.x}, ${here.z}, ${here.level}"
        val saved = player.savedTeleports().filterNot { it.name.equals(name, ignoreCase = true) }
        if (saved.size >= MAX_TELEPORTS) {
            mes("You already have $MAX_TELEPORTS teleports saved. Delete one first.")
            return
        }
        player.saveTeleports(saved + SavedTeleport(name, here))
        mes("Saved teleport: $name (${here.x}, ${here.z}, ${here.level}).")
    }

    private suspend fun ProtectedAccess.deleteTeleport(saved: List<SavedTeleport>) {
        if (saved.isEmpty()) {
            mes("You have no saved teleports.")
            return
        }
        val index = pick("Delete which teleport?", saved.map(SavedTeleport::label)) ?: return
        player.saveTeleports(saved.filterIndexed { i, _ -> i != index })
        mes("Deleted teleport: ${saved[index].name}.")
    }

    private fun Player.savedTeleports(): List<SavedTeleport> =
        attr[TELEPORTS].orEmpty().lines().mapNotNull(SavedTeleport::parse)

    private fun Player.saveTeleports(list: List<SavedTeleport>) {
        attr[TELEPORTS] = list.joinToString("\n", transform = SavedTeleport::encode)
    }

    // ------------------------------------------------------------------------------- menus

    /** A menu that pages past the interface's limit. Returns the chosen index, or null for none. */
    private suspend fun ProtectedAccess.pick(
        title: String,
        options: List<String>,
        back: Boolean = false,
    ): Int? {
        val pages = options.chunked(PAGE_SIZE).ifEmpty { listOf(emptyList()) }
        var page = 0
        while (true) {
            val extras = buildList {
                if (back || page > 0) add(BACK)
                if (page < pages.lastIndex) add(NEXT)
            }
            val choice = menu(title, false, extras + pages[page])
            if (choice >= extras.size) {
                return page * PAGE_SIZE + choice - extras.size
            }
            when {
                extras[choice] == NEXT -> page++
                page > 0 -> page--
                else -> return null
            }
        }
    }

    /** The menu interface splits on `|` and reads `<` as a tag; saved lines split on tabs. */
    private fun clean(text: String): String = text.replace(Regex("[|<>\\t\\n]"), " ")

    private class Group(
        val title: String,
        val names: Set<String> = emptySet(),
        val prefixes: List<String> = emptyList(),
    )

    private class SavedTeleport(val name: String, val coord: CoordGrid) {
        val label: String
            get() = "$name (${coord.x}, ${coord.z}, ${coord.level})"

        fun encode(): String = "$name\t${coord.x}\t${coord.z}\t${coord.level}"

        companion object {
            fun parse(line: String): SavedTeleport? {
                val parts = line.split('\t')
                if (parts.size != 4) {
                    return null
                }
                val x = parts[1].toIntOrNull() ?: return null
                val z = parts[2].toIntOrNull() ?: return null
                val level = parts[3].toIntOrNull() ?: return null
                return SavedTeleport(parts[0], CoordGrid(x, z, level))
            }
        }
    }

    private companion object {
        const val POTATO = "obj.rotten_potato"
        const val OTHER = "Other"
        const val ADD = "Add a teleport here"
        const val DELETE = "Delete a teleport"
        const val BACK = "< Back"
        const val NEXT = "Next page"
        const val PAGE_SIZE = 120
        const val MENU_TEXT_LIMIT = 70
        const val NAME_LIMIT = 40
        const val MAX_TELEPORTS = 500

        val TELEPORTS = AttributeKey<String>("admin_saved_teleports")
        val LAST_COMMAND = AttributeKey<String>(temp = true)

        val GROUPS =
            listOf(
                Group("Movement", setOf("tele", "telezone", "up", "down", "forward", "backwards", "back", "left", "right", "mypos")),
                Group("Player", setOf("master", "reset", "god", "die", "gamemode", "transmog", "pet", "venom", "venomclear", "disease", "maxhit", "completeall", "complete", "het")),
                Group("Items and bank", setOf("item", "spawn", "spawnold", "invadd", "invclear", "openbank", "craftmat")),
                Group("NPCs", setOf("nanim", "nspot", "nproj"), listOf("npc")),
                Group("Objects and world", setOf("object", "objectdel", "locadd", "locdel", "star", "gemstonecrab", "boat", "unboat", "boatbuild", "shipyard")),
                Group("Animation and sound", setOf("anim", "spot", "synth")),
                Group("Vars and interfaces", setOf("varp", "varbit", "getvarp", "getvarbit", "ifopen", "interface", "echo")),
                Group("Raids and bosses", setOf("raid", "wardens", "akkha", "baba", "kephri", "zebak", "apmeken", "scabaras", "gauntlet", "doomsim", "dezion", "titanbot"), listOf("boss", "toa")),
                Group("Server", setOf("reboot", "slowreboot", "worldtype", "loadplugin", "discordlink")),
            )
    }
}
