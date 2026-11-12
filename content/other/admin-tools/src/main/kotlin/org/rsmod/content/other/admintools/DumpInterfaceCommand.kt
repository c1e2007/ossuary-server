package org.rsmod.content.other.admintools

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import java.io.File
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** `::dumpif <interface id>` writes the cache definition of an interface to a text file. */
class DumpInterfaceCommand : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("dumpif") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Dump an interface definition to C:/Main project/_staging/if_<id>.txt"
            cheat {
                val id = args.firstOrNull()?.toIntOrNull()
                val type = id?.let { ServerCacheManager.getInterface(it) }
                if (id == null || type == null) {
                    player.mes("Usage: ::dumpif <interface id> (not found).")
                    return@cheat
                }
                File("C:/Main project/_staging/if_$id.txt").writeText(type.toString())
                player.mes("Wrote if_$id.txt")
            }
        }
    }
}
