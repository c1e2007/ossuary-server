package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal object OcellusSlash {
    const val AIM = "ocellus.slash_aim"
    const val AIM_AT = "ocellus.slash_aim_at"
    const val RELEASE = "ocellus.slash_release"
    const val STEPS = 13
    const val STEP_DELAY = 2

    private const val FACING_DISTANCE = 5
    private const val SUPERIOR_HALF_WIDTH = 1.25
    private const val SOUTH = 6
    private val DIRECTIONS =
        listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

    fun register(deps: BossDeps) {
        deps.extensionRegistry.register(AIM) { aim(it.npc, it.target) }
        deps.extensionRegistry.register(AIM_AT) { it.npc.lockFacing(it.target.coords) }
        deps.extensionRegistry.register(RELEASE) { it.npc.clearFacingLock() }
    }

    fun pickAim(npc: Npc, deps: BossDeps): CoordGrid {
        val inside = OcellusArena.quadrantPlayers(npc, deps.playerList)
        return if (inside.isEmpty()) CoordGrid.NULL else deps.random.pick(inside).coords
    }

    /**
     * The superior slash: every floor tile within [SUPERIOR_HALF_WIDTH] of the ray from the boss's
     * centre to [target], taken a ring at a time out from the boss. Unlike [band] the bearing is
     * not snapped to eight directions.
     */
    fun superiorBand(npc: Npc, target: Player, step: Int): List<CoordGrid> {
        val centre = centre(npc)
        val rx = target.coords.x - centre.x
        val rz = target.coords.z - centre.z
        val length = hypot(rx.toDouble(), rz.toDouble())
        val ux = if (length == 0.0) 0.0 else rx / length
        val uz = if (length == 0.0) -1.0 else rz / length
        val ring = step + npc.size / 2
        val tiles = ArrayList<CoordGrid>()
        for (dx in -ring..ring) {
            for (dz in -ring..ring) {
                if (maxOf(abs(dx), abs(dz)) != ring) continue
                if (dx * ux + dz * uz <= 0.0) continue
                if (abs(dx * uz - dz * ux) > SUPERIOR_HALF_WIDTH) continue
                tiles.add(centre.translate(dx, dz))
            }
        }
        return tiles.filter { OcellusArena.sameQuadrantFloor(npc, it) }
    }

    fun band(npc: Npc, target: Player, step: Int): List<CoordGrid> {
        val (dx, dz) = DIRECTIONS[octant(npc, target)]
        val sw = npc.coords
        val far = npc.size - 1
        val tiles =
            when {
                dz == 0 -> {
                    val x = if (dx > 0) sw.x + far + step else sw.x - step
                    (0..far).map { CoordGrid(x, sw.z + it, sw.level) }
                }
                dx == 0 -> {
                    val z = if (dz > 0) sw.z + far + step else sw.z - step
                    (0..far).map { CoordGrid(sw.x + it, z, sw.level) }
                }
                else -> {
                    val cx = if (dx > 0) sw.x + far else sw.x
                    val cz = if (dz > 0) sw.z + far else sw.z
                    listOf(
                        CoordGrid(cx + dx * step, cz + dz * step, sw.level),
                        CoordGrid(cx + dx * (step - 1), cz + dz * step, sw.level),
                        CoordGrid(cx + dx * step, cz + dz * (step - 1), sw.level),
                    )
                }
            }
        return tiles.filter { OcellusArena.sameQuadrantFloor(npc, it) }
    }

    private fun aim(npc: Npc, target: Player) {
        val (dx, dz) = DIRECTIONS[octant(npc, target)]
        npc.lockFacing(centre(npc).translate(dx * FACING_DISTANCE, dz * FACING_DISTANCE))
    }

    private fun octant(npc: Npc, target: Player): Int {
        val centre = centre(npc)
        val dx = target.coords.x - centre.x
        val dz = target.coords.z - centre.z
        if (dx == 0 && dz == 0) return SOUTH
        val turns = (atan2(dz.toDouble(), dx.toDouble()) / (Math.PI / 4)).roundToInt()
        return Math.floorMod(turns, DIRECTIONS.size)
    }

    private fun centre(npc: Npc): CoordGrid = npc.coords.translate(npc.size / 2, npc.size / 2)
}
