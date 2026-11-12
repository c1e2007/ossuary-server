package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import java.util.IdentityHashMap
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.spotanimMapPrivate
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

internal class OcellusSouls(
    private val deps: BossDeps,
    private val voidTiles: () -> Set<CoordGrid>,
) {
    private val current = IdentityHashMap<Player, Fragment>()

    fun register() {
        deps.extensionRegistry.register(TAKE) { take(it.npc, short = it.params as Boolean) }
        deps.extensionRegistry.register(RELEASE) { it.npc.infoProtocol.setSpotanim(-1, 0, 0, BOSS_SLOT) }
    }

    private class Fragment(val tile: CoordGrid, val owed: Int, val settledAt: Int, var age: Int = 0)

    private fun take(boss: Npc, short: Boolean) {
        for (player in OcellusArena.quadrantPlayers(boss, deps.playerList)) {
            player.spotanimSpecific(PLAYER_IDLE_SPOT, slot = PLAYER_SLOT)
            deps.worldQueues.add(TAKE_DELAY) { drain(boss, player, short) }
        }
    }

    private fun drain(boss: Npc, player: Player, short: Boolean) {
        if (!player.isValidTarget()) return
        val taken = player.hitpoints / 2
        if (taken <= 0) return
        player.queueHit(boss, 1, HitType.Typeless, taken, deps.playerHitModifier)
        val safe = safeTiles(boss)
        if (safe.isEmpty()) return
        val tile = safe[deps.random.of(safe.size)]
        val settled = if (short) SHORT_SPAWN_TICKS else LONG_SPAWN_TICKS
        player.spotanimMapPrivate(tile, if (short) SHORT_SPAWN_SPOT else LONG_SPAWN_SPOT)
        val fragment = Fragment(tile, taken, settled)
        current.put(player, fragment)?.let { player.spotanimMapPrivate(it.tile, DESPAWN_SPOT) }
        deps.worldQueues.add(settled) {
            if (current[player] === fragment) player.spotanimMapPrivate(tile, IDLE_SPOT)
        }
        watch(boss, player, fragment)
    }

    private fun watch(boss: Npc, player: Player, fragment: Fragment) {
        deps.worldQueues.add(1) {
            if (current[player] !== fragment) return@add
            if (!player.isValidTarget() || !boss.isSlotAssigned) {
                current.remove(player)
                return@add
            }
            fragment.age++
            if (fragment.age >= fragment.settledAt && player.coords == fragment.tile) {
                current.remove(player)
                player.spotanimSpecific(PLAYER_REGAIN_SPOT, slot = PLAYER_SLOT)
                player.statHeal(HITPOINTS, fragment.owed, 0)
                player.spotanimMapPrivate(fragment.tile, DESPAWN_SPOT)
            } else {
                watch(boss, player, fragment)
            }
        }
    }

    private fun safeTiles(boss: Npc): List<CoordGrid> {
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        val covered = voidTiles()
        val safe = ArrayList<CoordGrid>()
        for (dx in -REACH..REACH) {
            for (dz in -REACH..REACH) {
                val tile = centre.translate(dx, dz)
                if (!OcellusArena.sameQuadrantFloor(boss, tile) || tile in covered) continue
                if (deps.collision[tile.x, tile.z, tile.level] and BLOCKED != 0) continue
                safe.add(tile)
            }
        }
        return safe
    }

    companion object {
        const val TAKE = "ocellus.souls_take"
        const val RELEASE = "ocellus.souls_release"

        const val BOSS_SLOT = 2

        private const val TAKE_DELAY = 1
        private const val PLAYER_SLOT = 2
        private const val PLAYER_IDLE_SPOT = "spotanim.vfx_ocellus_soul_fragment_player_idle"
        private const val PLAYER_REGAIN_SPOT = "spotanim.vfx_ocellus_soul_fragment_player_regain"
        private const val LONG_SPAWN_SPOT = "spotanim.vfx_ocellus_soul_fragment_spawn_12_ticks"
        private const val SHORT_SPAWN_SPOT = "spotanim.vfx_ocellus_soul_fragment_spawn_6_ticks"
        private const val IDLE_SPOT = "spotanim.vfx_ocellus_soul_fragment_idle"
        private const val DESPAWN_SPOT = "spotanim.vfx_ocellus_soul_fragment_despawn"
        private const val LONG_SPAWN_TICKS = 12
        private const val SHORT_SPAWN_TICKS = 6
        private const val REACH = 12
        private const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
        private const val HITPOINTS = "stat.hitpoints"
    }
}
