package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class OcellusBombs(private val deps: BossDeps, private val standingTurns: () -> List<Int>) {
    private val marked = ArrayList<Player>()

    fun register() {
        deps.extensionRegistry.register(MARK) { mark(it.npc) }
        deps.extensionRegistry.register(IDLE) { show(IDLE_SPOT) }
    }

    fun clear() {
        marked.clear()
    }

    fun tiles(npc: Npc): List<CoordGrid> {
        val middle = OcellusArena.middle(npc)
        val turns = standingTurns()
        val spots =
            marked
                .filter { it.isValidTarget() && OcellusArena.sameQuadrantFloor(npc, it.coords) }
                .map { it.coords }
        marked.clear()
        return spots
            .flatMap { spot ->
                turns.map { quarterTurns ->
                    var dx = spot.x - middle.x
                    var dz = spot.z - middle.z
                    repeat(quarterTurns) {
                        val turned = -dz
                        dz = dx
                        dx = turned
                    }
                    middle.translate(dx, dz)
                }
            }
            .distinct()
    }

    private fun mark(npc: Npc) {
        marked.clear()
        if (standingTurns().size < MIN_QUADRANTS) return
        val candidates = OcellusArena.quadrantPlayers(npc, deps.playerList).toMutableList()
        repeat(minOf(MARKS, candidates.size)) {
            marked.add(candidates.removeAt(deps.random.of(candidates.size)))
        }
        show(SPAWN_SPOT)
    }

    private fun show(spot: String) {
        for (player in marked) player.spotanim(spot, height = SPOT_HEIGHT, slot = SPOT_SLOT)
    }

    companion object {
        const val MARK = "ocellus.bombs_mark"
        const val IDLE = "ocellus.bombs_idle"

        private const val MARKS = 2
        private const val MIN_QUADRANTS = 2
        private const val SPOT_SLOT = 5
        private const val SPOT_HEIGHT = 255
        private const val SPAWN_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_spawn_01"
        private const val IDLE_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_idle_01"
    }
}
