package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.runtime.Angles
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

internal class OcellusQuadrants(
    private val deps: BossDeps,
    private val interactions: AiPlayerInteractions,
    private val stoneLocs: Map<CoordGrid, LocInfo>,
    private val onCollapse: (Set<CoordGrid>) -> Unit,
) {
    private val spawned = ArrayList<LocInfo>()
    private val destroyed = LinkedHashSet<Quadrant>()
    private val barriers = ArrayList<LocInfo>()
    private var current: Quadrant? = null
    private var fallingStones: List<CoordGrid> = emptyList()

    fun register() {
        deps.extensionRegistry.register(ENTER) { enter(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(FREEZE) { hold(it.npc) }
        deps.extensionRegistry.register(THAW) { it.npc.scriptedMovement = false }
        deps.extensionRegistry.register(UNTETHER_GLIDE) { glideHome(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(BARRIER_SPAWN) { barriers(centre(it.npc), "_spawn") }
        deps.extensionRegistry.register(BARRIER) { barriers(centre(it.npc), "") }
        deps.extensionRegistry.register(BARRIER_DESPAWN) { barriers(centre(it.npc), "_despawn") }
        deps.extensionRegistry.register(BARRIER_REMOVE) { removeBarriers(centre(it.npc)) }
        deps.extensionRegistry.register(COLLAPSE) { collapse(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(STONES_FALL) { stonesFall(centre(it.npc)) }
        deps.extensionRegistry.register(STONES_BLOCK) { stonesBlock() }
    }

    fun clear() {
        for (loc in spawned) deps.locRepo.del(loc, Int.MAX_VALUE)
        spawned.clear()
        barriers.clear()
        destroyed.clear()
        fallenStones.clear()
        current = null
        fallingStones = emptyList()
    }

    private fun centre(npc: Npc): CoordGrid = npc.spawnCoords.translate(CENTRE_DX, CENTRE_DZ)

    fun standingTurns(): List<Int> {
        val here = current ?: return emptyList()
        return Quadrant.entries
            .filter { it !in destroyed }
            .map { Math.floorMod(it.ordinal - here.ordinal, Quadrant.entries.size) }
    }

    /** Players outside the boss's quadrant can't hurt it, so no spot across the gap is safe. */
    fun isOutside(npc: Npc, tile: CoordGrid): Boolean {
        val quadrant = current ?: return false
        return !quadrant.contains(centre(npc), tile)
    }

    private fun add(
        at: CoordGrid,
        loc: String,
        shape: LocShape = LocShape.CentrepieceStraight,
        rotation: Int = 0,
    ): LocInfo {
        val info = deps.locRepo.add(at, loc, Int.MAX_VALUE, LocAngle[rotation], shape)
        spawned.add(info)
        return info
    }

    private fun enter(npc: Npc, centre: CoordGrid) {
        val players = OcellusArena.players(npc, deps.playerList)
        val open = Quadrant.entries.filter { it !in destroyed }
        val occupied = open.maxByOrNull { q -> players.count { q.contains(centre, it.coords) } }
        val start = current?.let { open.firstOrNull { q -> q.ordinal > it.ordinal } } ?: open.first()
        val withPlayers = occupied?.takeIf { q -> players.any { q.contains(centre, it.coords) } }
        val quadrant = withPlayers ?: start
        current = quadrant
        PathingEntityCommon.telejump(
            npc,
            deps.collision,
            centre.translate(quadrant.bossDx, quadrant.bossDz),
        )
        npc.showAllOps()
        retarget(npc, quadrant, centre, players)
        hold(npc)
        npc.faceSquare(centre, instant = true)
    }

    private fun glideHome(npc: Npc, centre: CoordGrid) {
        val quadrant = current ?: return
        val home = centre.translate(quadrant.bossDx, quadrant.bossDz)
        val start = npc.coords
        if (start == home) return
        PathingEntityCommon.exactMove(
            npc,
            start,
            home,
            delay1 = 0,
            delay2 = UNTETHER_GLIDE_DELAY,
            dir = Angles.bearing(start, home),
            collision = deps.collision,
        )
    }

    private fun retarget(npc: Npc, quadrant: Quadrant, centre: CoordGrid, players: List<Player>) {
        val target =
            players
                .filter { quadrant.contains(centre, it.coords) }
                .minByOrNull { it.coords.chebyshevDistance(npc.coords) } ?: return
        npc.apPlayer2(target, interactions)
    }

    /** Nothing to face and nowhere to route until [THAW]; the engine would otherwise re-face the target. */
    private fun hold(npc: Npc) {
        npc.scriptedMovement = true
        npc.clearFacingLock()
        npc.resetFaceEntity()
    }

    private fun barriers(centre: CoordGrid, variant: String) {
        val quadrant = current ?: return
        barriers.clear()
        for (barrier in quadrant.barriers) {
            val loc = "${BARRIER_LOC}_0${barrier.index}$variant"
            val at = centre.translate(barrier.dx, barrier.dz)
            barriers.add(add(at, loc, rotation = barrier.rotation))
        }
    }

    private fun removeBarriers(centre: CoordGrid) {
        val quadrant = current ?: return
        for (barrier in quadrant.barriers) {
            add(centre.translate(barrier.dx, barrier.dz), NONBLOCKING_LOC)
        }
        barriers.clear()
    }

    private fun collapse(npc: Npc, centre: CoordGrid) {
        val quadrant = current ?: return
        destroyed.add(quadrant)
        onCollapse(quadrant.floor(centre).toSet())
        for ((dx, dz) in quadrant.hidden) {
            add(centre.translate(dx, dz), NONBLOCKING_LOC)
            add(centre.translate(dx, dz), NONBLOCKING_TYPE0_LOC, LocShape.GroundDecor)
        }
        for (slice in quadrant.slices) {
            val info = add(centre.translate(slice.dx, slice.dz), "${SLICE_LOC}_${quadrant.slicePrefix}_0${slice.index}")
            deps.worldRepo.locAnim(info, SLICE_ANIM)
        }
        val tiles = quadrant.floor(centre).filter(::walkable)
        for (tile in tiles) add(tile, BLOCKING_TYPE0_LOC, LocShape.GroundDecor)
        val doomed = tiles.toSet()
        for (player in OcellusArena.players(npc, deps.playerList)) {
            if (player.coords in doomed) {
                player.queueHit(npc, 1, HitType.Typeless, player.hitpoints, deps.playerHitModifier)
            }
        }
    }

    private fun stonesFall(centre: CoordGrid) {
        val fallen = destroyed.lastOrNull() ?: return
        val stones = fallen.arms.flatMap { arm -> arm.stones.map { centre.translate(it.first, it.second) } }
        fallingStones = stones.filter { it in stoneLocs && !blocked(it) }.distinct()
        val anims = STONE_DESPAWN_ANIMS.shuffled()
        fallingStones.forEachIndexed { i, tile ->
            deps.worldRepo.locAnim(stoneLocs.getValue(tile), anims[i % anims.size])
        }
    }

    private fun stonesBlock() {
        for (tile in fallingStones) {
            add(tile, STONE_BLOCKER, LocShape.GroundDecor)
            fallenStones.add(tile)
        }
        fallingStones = emptyList()
    }

    private val fallenStones = HashSet<CoordGrid>()

    private fun blocked(tile: CoordGrid): Boolean = tile in fallenStones

    private fun walkable(tile: CoordGrid): Boolean =
        deps.collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private class Barrier(val index: Int, val dx: Int, val dz: Int, val rotation: Int)

    private class Slice(val index: Int, val dx: Int, val dz: Int)

    private enum class Arm(val stones: List<Pair<Int, Int>>) {
        South(listOf(0 to -4, 0 to -7, 0 to -10)),
        North(listOf(0 to 4, 0 to 7, 0 to 10)),
        East(listOf(4 to 0, 7 to 0, 10 to 0)),
        West(listOf(-4 to 0, -7 to 0, -10 to 0)),
    }

    private enum class Quadrant(
        val sx: Int,
        val sz: Int,
        val bossDx: Int,
        val bossDz: Int,
        val barriers: List<Barrier>,
        val arms: List<Arm>,
        val slicePrefix: String,
        val slices: List<Slice>,
        val hidden: List<Pair<Int, Int>>,
    ) {
        SouthWest(
            -1, -1, -7, -7,
            listOf(Barrier(1, -8, -1, 3), Barrier(2, -1, -7, 0)),
            listOf(Arm.South, Arm.West),
            "sw",
            listOf(Slice(2, -13, -9), Slice(3, -8, -8), Slice(1, -10, -13)),
            listOf(-9 to -9, -10 to -13, -13 to -10),
        ),
        SouthEast(
            1, -1, 5, -7,
            listOf(Barrier(1, 1, -8, 2), Barrier(2, 7, -1, 3)),
            listOf(Arm.South, Arm.East),
            "se",
            listOf(Slice(1, 8, -10), Slice(2, 2, -13), Slice(3, 1, -8)),
            listOf(2 to -10, 2 to -13, 9 to -9),
        ),
        NorthEast(
            1, 1, 5, 5,
            listOf(Barrier(1, 8, 1, 1), Barrier(2, 1, 7, 2)),
            listOf(Arm.North, Arm.East),
            "ne",
            listOf(Slice(2, 8, 2), Slice(3, 1, 1), Slice(1, 2, 8)),
            listOf(2 to 3, 2 to 2, 9 to 9),
        ),
        NorthWest(
            -1, 1, -7, 5,
            listOf(Barrier(1, -1, 8, 0), Barrier(2, -7, 1, 1)),
            listOf(Arm.North, Arm.West),
            "nw",
            emptyList(),
            emptyList(),
        );

        fun contains(centre: CoordGrid, tile: CoordGrid): Boolean {
            val x = tile.x - centre.x
            val z = tile.z - centre.z
            return x * sx in 1..ARM_LENGTH && z * sz in 1..ARM_LENGTH
        }

        fun floor(centre: CoordGrid): List<CoordGrid> =
            (FLOOR_MIN..FLOOR_MAX).flatMap { a ->
                (FLOOR_MIN..FLOOR_MAX).map { b -> centre.translate(sx * a, sz * b) }
            }
    }

    companion object {
        const val ENTER = "ocellus.quadrant_enter"
        const val FREEZE = "ocellus.quadrant_freeze"
        const val THAW = "ocellus.quadrant_thaw"
        const val UNTETHER_GLIDE = "ocellus.untether_glide"
        const val BARRIER_SPAWN = "ocellus.quadrant_barrier_spawn"
        const val BARRIER = "ocellus.quadrant_barrier"
        const val BARRIER_DESPAWN = "ocellus.quadrant_barrier_despawn"
        const val BARRIER_REMOVE = "ocellus.quadrant_barrier_remove"
        const val COLLAPSE = "ocellus.quadrant_collapse"
        const val STONES_FALL = "ocellus.quadrant_stones_fall"
        const val STONES_BLOCK = "ocellus.quadrant_stones_block"

        private const val CENTRE_DX = 1
        private const val CENTRE_DZ = 4
        private const val UNTETHER_GLIDE_DELAY = 90
        private const val ARM_LENGTH = 12
        private const val FLOOR_MIN = 2
        private const val FLOOR_MAX = 12

        private const val BARRIER_LOC = "loc.tfa_ocellus_quadrant_barrier"
        private const val NONBLOCKING_LOC = "loc.invisible_type8_nonblocking"
        private const val NONBLOCKING_TYPE0_LOC = "loc.invisible_type0_nonblocking"
        private const val BLOCKING_TYPE0_LOC = "loc.invisible_type0_blocking"
        private const val STONE_BLOCKER = "loc.invisible_type8_blocking"
        private const val SLICE_LOC = "loc.tfa_wallkit_archive03_slice_collapse"
        private const val SLICE_ANIM = "seq.dungeonkit_guthixian03_collapse_01"

        private val STONE_DESPAWN_ANIMS =
            listOf(
                "seq.dungeonkit_guthixian03_steppingstone_despawn_01",
                "seq.dungeonkit_guthixian03_steppingstone_despawn_02",
                "seq.dungeonkit_guthixian03_steppingstone_despawn_03",
            )
    }
}
