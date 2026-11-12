package org.rsmod.api.player.output

import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpServerType
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

public object VarpSync {
    /** Calling this function directly will bypass [VarpType.transmit] conditions. */
    public fun writeVarp(player: Player, varp: VarpServerType, value: Int) {
        writeVarp(player.client, varp, value)
    }

    /** Calling this function directly will bypass [VarpType.transmit] conditions. */
    public fun writeVarp(client: Client<Any, Any>, varp: VarpServerType, value: Int) {
        // The client drops the connection on a varp it does not know. Skip varps that are never
        // transmitted, and ids that are not in the cache at all (the cache manager hands out a
        // never-transmit placeholder for those). Id ranges are not used: server-only state such as
        // varp.bork_burn is stored on the player as usual, it is just not sent.
        if (varp.transmit.never || !ServerCacheManager.getVarps().containsKey(varp.id)) {
            return
        }
        val message =
            if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                VarpSmall(varp.id, value)
            } else {
                VarpLarge(varp.id, value)
            }
        client.write(message)
    }
}
