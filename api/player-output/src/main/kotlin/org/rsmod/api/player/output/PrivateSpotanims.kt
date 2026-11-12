package org.rsmod.api.player.output

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid

/**
 * Plays a spotanim on [coords] that only [this] player's client sees. Tiles outside the player's
 * current build area are ignored.
 */
public fun Player.spotanimMapPrivate(coords: CoordGrid, spot: String, delay: Int = 0, height: Int = 0) {
    val inZone = ZoneGrid.from(coords)
    writeZoneProt(coords, MapAnim(spot.asRSCM(RSCMType.SPOTANIM), delay, height, inZone.x, inZone.z))
}
