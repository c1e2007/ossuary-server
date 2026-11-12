package org.rsmod.content.raids.tfa.raid.encounter.xarth

import dev.openrune.types.HitmarkTypeGroup
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed

/**
 * Burn, bleed and bind: per-player damage-over-time chains and the run lock, kept in temporary
 * server-only varps so they clear on logout.
 */
internal class XarthStatus(
    private val deps: BossDeps,
    private val boss: () -> Npc,
    private val active: (Player) -> Boolean,
) {
    fun ignite(player: Player, add: Int) {
        if (!active(player)) return
        val state = player.burn
        val burning = state > 0
        val stack = ((if (burning) state % STACK_SCALE else 0) + add).coerceIn(1, XarthArena.BURN_CAP)
        player.burn = pack(stack, XarthArena.BURN_LINGER)
        deal(player, stack, hitmark_groups.burn)
        if (burning) return
        player.mes("<col=ff9040>You have been set alight!</col>")
        scheduleBurn(player)
    }

    fun bleed(player: Player) {
        if (!active(player)) return
        val bleeding = player.bleed > 0
        player.bleed = XarthArena.BLEED_HITS
        if (bleeding) return
        player.mes("<col=ff9040>You have started bleeding!</col>")
        scheduleBleed(player)
    }

    fun bind(player: Player) {
        if (!active(player)) return
        val bound = player.bound > 0
        player.bound = XarthArena.BIND_TICKS
        if (!bound) scheduleBind(player)
    }

    private fun scheduleBurn(player: Player) {
        deps.worldQueues.add(XarthArena.BURN_PERIOD) {
            val state = player.burn
            if (state <= 0 || !active(player)) {
                player.burn = 0
                return@add
            }
            val linger = state / STACK_SCALE - XarthArena.BURN_PERIOD
            if (linger <= 0) {
                deal(player, BURN_LAST, hitmark_groups.burn)
                player.burn = 0
                return@add
            }
            val stack = state % STACK_SCALE
            deal(player, stack, hitmark_groups.burn)
            player.burn = pack(stack, linger)
            scheduleBurn(player)
        }
    }

    private fun scheduleBleed(player: Player) {
        deps.worldQueues.add(XarthArena.BLEED_PERIOD) {
            val left = player.bleed
            if (left <= 0 || !active(player)) {
                player.bleed = 0
                return@add
            }
            deal(player, XarthArena.BLEED_DAMAGE, hitmark_groups.bleed)
            player.bleed = left - 1
            if (left > 1) scheduleBleed(player) else player.bleed = 0
        }
    }

    private fun scheduleBind(player: Player) {
        deps.worldQueues.add(1) {
            val left = player.bound
            if (left <= 0 || !active(player)) {
                player.bound = 0
                return@add
            }
            if (player.varMoveSpeed == MoveSpeed.Run) {
                player.setActiveMoveSpeed(MoveSpeed.Walk)
                player.mes("<col=ff0000>Xarth's demonic chains prevent you from running!</col>")
            }
            player.bound = left - 1
            if (left > 1) scheduleBind(player) else player.bound = 0
        }
    }

    private fun deal(player: Player, damage: Int, hitmark: HitmarkTypeGroup) {
        player.queueHit(boss(), 1, HitType.Typeless, damage, deps.playerHitModifier, hitmark)
    }

    private fun pack(stack: Int, linger: Int): Int = linger * STACK_SCALE + stack

    private companion object {
        const val STACK_SCALE = 10
        const val BURN_LAST = 1

        var Player.burn by intVarp("varp.xarth_burn")
        var Player.bleed by intVarp("varp.xarth_bleed")
        var Player.bound by intVarp("varp.xarth_bound")
    }
}
