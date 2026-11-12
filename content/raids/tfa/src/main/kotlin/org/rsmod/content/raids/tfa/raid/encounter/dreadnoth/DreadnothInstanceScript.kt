package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

import jakarta.inject.Inject
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.content.raids.tfa.party.TfaLobby
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DreadnothInstanceScript
@Inject
constructor(private val encounter: DreadnothEncounter, private val lobby: TfaLobby) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<InstanceEndedEvent>(instanceEventId(DreadnothEncounter.KEY)) {
            encounter.close(instanceId)
            lobby.raidEnded(instanceId)
        }
    }
}
