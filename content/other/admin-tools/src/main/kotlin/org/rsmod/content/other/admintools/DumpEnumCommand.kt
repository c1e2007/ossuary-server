package org.rsmod.content.other.admintools

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import java.io.File
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** `::dumpenum <from> [to]` writes enum definitions, with item names, to a text file. */
class DumpEnumCommand : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("dumpenum") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Dump enums to C:/Main project/_staging/enum_<from>.txt"
            cheat {
                val from = args.getOrNull(0)?.toIntOrNull()
                val to = args.getOrNull(1)?.toIntOrNull() ?: from
                if (from == null || to == null) {
                    player.mes("Usage: ::dumpenum <from> [to]")
                    return@cheat
                }
                val out = StringBuilder()
                for (id in from..to) {
                    val e = ServerCacheManager.getEnum(id) ?: continue
                    var text = e.toString()
                    out.append("== $id\n").append(text.take(20000)).append("\n")
                }
                File("C:/Main project/_staging/enum_$from.txt").writeText(out.toString())
                player.mes("Wrote enum_$from.txt")
            }
        }
        onCommand("dumpitem") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Dump all item names to C:/Main project/_staging/items.txt"
            cheat {
                val lines = ServerCacheManager.getItems().values
                    .filter { !it.isPlaceholder && !it.isCert && it.name.isNotBlank() }
                    .joinToString("\n") { "${it.id}\t${it.name}" }
                File("C:/Main project/_staging/items.txt").writeText(lines)
                player.mes("Wrote items.txt")
            }
        }
    }
}
