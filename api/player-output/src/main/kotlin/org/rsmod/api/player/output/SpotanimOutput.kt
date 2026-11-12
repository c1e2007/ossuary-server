package org.rsmod.api.player.output

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.specific.NpcSpotAnimSpecific
import net.rsprot.protocol.game.outgoing.specific.PlayerSpotAnimSpecific
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Plays a spotanim on [this] player that only their own client sees. @see [PlayerSpotAnimSpecific] */
public fun Player.spotanimSpecific(spot: String, delay: Int = 0, height: Int = 0, slot: Int = 0) {
    client.write(PlayerSpotAnimSpecific(slotId, spot.asRSCM(RSCMType.SPOTANIM), slot, height, delay))
}

/** Plays a spotanim on [npc] that only [this] player's client sees. @see [NpcSpotAnimSpecific] */
public fun Player.spotanimSpecific(
    npc: Npc,
    spot: String,
    delay: Int = 0,
    height: Int = 0,
    slot: Int = 0,
) {
    client.write(NpcSpotAnimSpecific(npc.slotId, spot.asRSCM(RSCMType.SPOTANIM), slot, height, delay))
}

/** Removes a spotanim previously played on [npc] with [spotanimSpecific] in [slot]. */
public fun Player.clearSpotanimSpecific(npc: Npc, slot: Int = 0) {
    client.write(NpcSpotAnimSpecific(npc.slotId, -1, slot, 0, 0))
}
