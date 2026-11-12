package org.rsmod.content.other.admintools

import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.toggleGodMode
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** "Drink" on the flask runs the same toggle as `::god`: immunity plus 100-damage hits. */
class AdminFlask : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(FLASK) { drink() }
    }

    private fun ProtectedAccess.drink() {
        if (!requireAdmin()) {
            return
        }
        player.toggleGodMode()
        mes("God mode ${if (player.adminGodMode) "enabled" else "disabled"}.")
    }

    private companion object {
        const val FLASK = "obj.doti_flask"
    }
}
