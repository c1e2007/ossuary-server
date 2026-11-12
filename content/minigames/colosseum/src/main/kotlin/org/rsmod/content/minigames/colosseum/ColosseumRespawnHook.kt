package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class ColosseumRespawnHook
@Inject
constructor(private val runs: ColosseumRuns, private val rewards: ColosseumRewards) :
    PlayerRespawnHook {
    override fun respawnCoords(player: Player): CoordGrid? {
        val run = runs.runFor(player) ?: return null
        rewards.keepGlory(run)
        player.colosseumDeaths = player.colosseumDeaths + 1
        rewards.lost(run)
        runs.end(run)
        return Colosseum.LOBBY_RESPAWN
    }
}
