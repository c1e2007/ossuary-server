package org.rsmod.api.net.rsprot

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.Infos
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.entity.Player

/**
 * Gives a player with no connection the network library's info objects, so real clients can see it
 * like anyone else. It keeps the [org.rsmod.game.client.NoopClient], so nothing is ever sent to it.
 * Used by server-run bots on admin-listed accounts.
 */
@Singleton
class BotSessions
@Inject
constructor(
    private val service: NetworkService<Player>,
    private val regions: RegionRegistry,
) {
    private val infos = IdentityHashMap<Player, Infos>()

    /** Call with [Player.slotId] set, just before the player is added to the registry. */
    fun attach(player: Player) {
        val allocated = service.infoProtocols.alloc(player.slotId, OldSchoolClientType.DESKTOP)
        infos[player] = allocated
        val cycle = RspCycle(null, allocated, regions)
        player.clientCycle = cycle
        cycle.init(player)
    }

    /** Called when the player leaves the world. Does nothing for anyone who isn't a bot. */
    fun release(player: Player) {
        val allocated = infos.remove(player) ?: return
        service.infoProtocols.dealloc(allocated)
    }
}
