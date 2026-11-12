package org.rsmod.content.other.admintools

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.quest.manager.Quest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** `::completeall` finishes every quest that has been implemented, for testing. */
class AdminQuestCommands @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("completeall") {
            desc = "Complete every implemented quest"
            cheat { protectedAccess.launch(player) { completeAllQuests() } }
        }
    }

    private fun ProtectedAccess.completeAllQuests() {
        val pending = Quest.all().filterNot { it.isQuestCompleted(player) }
        for (quest in pending) {
            quest.completeQuest(this)
        }
        mes("Completed ${pending.size} quests.")
    }
}
