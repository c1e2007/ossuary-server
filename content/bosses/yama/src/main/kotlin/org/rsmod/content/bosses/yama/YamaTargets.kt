package org.rsmod.content.bosses.yama

import dev.openrune.types.NpcMode
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionPlayer

/**
 * Every tick, for Yama and for every npc the fight tracks: if it is not in the combat mode, or the
 * player is not its target, the target is re-issued. The engine resets an npc to its default mode
 * whenever an interaction is cancelled, and nothing else re-applies the target (the same fix as the
 * Colosseum's). Yama uses ap-player2 because he fights from range in his standing stance; see
 * YAMA-DEVIATIONS.md. Summoned npcs (flares, the Judge, surges) do not attack through the engine:
 * their attacks are the fight's own timers, so they are pinned and have no target to hold.
 */
internal object YamaTargets {
    fun reassert(fight: YamaFight) {
        if (!fight.player.isValidTarget()) return
        if (fight.state != YamaState.FIGHTING && fight.state != YamaState.SUMMONING) return
        val yama = fight.yama
        if (yama != null && yama.isSlotAssigned && yama.hitpoints > 0) engage(fight, yama)
    }

    private fun engage(fight: YamaFight, npc: Npc) {
        val target = (npc.interaction as? InteractionPlayer)?.target
        if (npc.mode != NpcMode.ApPlayer2 || target !== fight.player) {
            npc.apPlayer2(fight.player, fight.deps.interactions)
        }
    }
}
