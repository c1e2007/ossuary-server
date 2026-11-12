package org.rsmod.content.minigames.colosseum

import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.hit.HitType

internal object ColosseumEffects {
    private val RELENTLESS_MAX_HIT = intArrayOf(0, 1, 3, 6)
    private val BLASPHEMY_DRAIN = doubleArrayOf(0.0, 0.2, 0.4, 0.6)
    private val DOOM_LIMIT = intArrayOf(Int.MAX_VALUE, 15, 10, 5)

    fun maxHitBonus(run: ColosseumRun): Int =
        RELENTLESS_MAX_HIT[run.modifiers.tier(ColosseumModifier.RELENTLESS)]

    fun damageTaken(run: ColosseumRun, amount: Int, fromEnemy: Boolean) {
        if (amount <= 0 || run.stage == RunStage.ENDED) return
        if (fromEnemy) run.hitByEnemy = true
        val player = run.player
        run.waveDamage += amount
        player.colosseumWaveDamage = run.waveDamage
        val drain = (amount * BLASPHEMY_DRAIN[run.modifiers.tier(ColosseumModifier.BLASPHEMY)]).toInt()
        if (drain > 0) player.statSub("stat.prayer", constant = drain, percent = 0)
        val doom = run.modifiers.tier(ColosseumModifier.DOOM)
        if (doom == 0) return
        run.doomStacks++
        if (run.doomStacks >= DOOM_LIMIT[doom]) {
            val source = run.fight?.npc ?: run.npcs.firstOrNull() ?: return
            player.queueHit(source, 1, HitType.Typeless, player.hitpoints, NoopPlayerHitModifier)
        }
    }

    fun waveCompleted(run: ColosseumRun) {
        run.doomStacks = 0
    }
}
