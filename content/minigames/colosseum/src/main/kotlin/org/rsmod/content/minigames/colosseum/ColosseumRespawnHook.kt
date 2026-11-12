package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class ColosseumRespawnHook @Inject constructor(private val runs: ColosseumRuns) :
    PlayerRespawnHook {
    override fun respawnCoords(player: Player): CoordGrid? {
        val run = runs.runFor(player) ?: return null
        runs.end(run)
        return Colosseum.LOBBY_RESPAWN
    }
}
