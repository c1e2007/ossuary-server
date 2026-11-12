package org.rsmod.content.raids.tfa.raid

import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceSession
import org.rsmod.events.UnboundEvent

internal interface TfaFight {
    val session: InstanceSession
    val elapsedTicks: Int?
    val finished: Boolean
}

data class TfaRoomCleared(val instanceId: InstanceId, val room: String) : UnboundEvent
