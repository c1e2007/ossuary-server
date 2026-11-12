package org.rsmod.content.raids.tfa.raid.encounter.guardian

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GuardianIntroScript @Inject constructor(private val encounter: GuardianEncounter) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.tfa_guardian_start") { touch(it.npc) }
    }

    private suspend fun ProtectedAccess.touch(npc: Npc) {
        val fight = encounter.of(npc) ?: return
        if (fight.elapsedTicks != null) {
            mes("Your party has already started the challenge. You can't join them now.")
            return
        }
        val begin = choice2("Yes.", true, "No.", false, title = "Begin the challenge?")
        if (begin) fight.ready(player)
    }
}
