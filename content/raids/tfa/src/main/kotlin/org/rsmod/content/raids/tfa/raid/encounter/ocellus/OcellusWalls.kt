package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import kotlin.math.abs
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

internal class OcellusWalls(private val deps: BossDeps) {
    private class Telegraph(
        val occupied: List<Quadrant>,
        val arms: Set<Arm>,
        val gaps: Map<Arm, Int>,
    )

    private val walls = HashMap<Pair<Arm, Int>, LocInfo>()
    private var pending: Telegraph? = null
    private var volleys = 0
    private val pools = HashMap<Quadrant, List<CoordGrid>>()
    private val portals = HashMap<Quadrant, CoordGrid>()

    fun register() {
        deps.extensionRegistry.register(SPAWN) { spawn(centre(it.npc)) }
        deps.extensionRegistry.register(TELEGRAPH) { telegraph(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(CLOSED_TELEGRAPH) {
            telegraph(it.npc, centre(it.npc), closed = true)
        }
        deps.extensionRegistry.register(FIRE) { fire(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(POOLS) { pools(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(POOLS_DETONATE) { detonatePools(it.npc) }
        deps.extensionRegistry.register(PORTALS_SPAWN) { spawnPortals() }
        deps.extensionRegistry.register(PORTALS_TICK) { tickPortals(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(PORTALS_END) { endPortals() }
        deps.extensionRegistry.register(DESPAWN) {
            for (loc in walls.values) deps.worldRepo.locAnim(loc, WALL_DESPAWN_SEQ)
        }
        deps.extensionRegistry.register(CLEAR) { clear() }
    }

    fun clear() {
        pending = null
        volleys = 0
        pools.clear()
        portals.clear()
        for (loc in walls.values) deps.locRepo.del(loc, Int.MAX_VALUE)
        walls.clear()
    }

    private fun centre(npc: Npc): CoordGrid = npc.spawnCoords.translate(CENTRE_DX, CENTRE_DZ)

    private fun spawn(centre: CoordGrid) {
        clear()
        for (arm in Arm.entries) {
            for (index in 0 until SEGMENTS) {
                walls[arm to index] = setWall(arm, arm.segment(centre, index), index, active = false)
            }
        }
    }

    private fun setWall(arm: Arm, origin: CoordGrid, index: Int, active: Boolean): LocInfo {
        val type = if (active) WALL_ACTIVE else WALL_INACTIVE
        val loc = deps.locRepo.add(origin, type, Int.MAX_VALUE, arm.angle, LocShape.CentrepieceStraight)
        if (active) deps.worldRepo.locAnim(loc, WALL_IDLE_SEQ)
        return loc
    }

    private fun occupiedQuadrants(npc: Npc, centre: CoordGrid): List<Quadrant> {
        val players = OcellusArena.players(npc, deps.playerList)
        return Quadrant.entries.filter { q -> players.any { q.contains(centre, it.coords) } }
    }

    private fun telegraph(npc: Npc, centre: CoordGrid, closed: Boolean = false) {
        val roll = if (closed) null else nextGap()
        val occupied = occupiedQuadrants(npc, centre)
        if (occupied.isEmpty()) return
        val arms = occupied.flatMap { it.arms }.toSet()
        val gaps =
            if (roll == null) {
                emptyMap()
            } else {
                val (horizontal, vertical) = roll
                arms.associateWith {
                    val distance = if (it.horizontal) horizontal else vertical
                    if (it.sign > 0) distance else SEGMENTS - 1 - distance
                }
            }
        for (arm in arms) {
            for (index in 0 until SEGMENTS) {
                if (index == gaps[arm]) continue
                walls[arm to index] = setWall(arm, arm.segment(centre, index), index, active = true)
            }
        }
        pending = Telegraph(occupied, arms, gaps)
    }

    /**
     * The gap's distance from the centre on each axis, as (horizontal, vertical). Every capture
     * has the same [GAP_PATTERNS], one per group of volleys and in order, whoever stands where.
     */
    private fun nextGap(): Pair<Int, Int> {
        val group = GAP_PATTERNS[volleys / GAP_PATTERN_LENGTH % GAP_PATTERNS.size]
        val (vertical, horizontal) = group[volleys % GAP_PATTERN_LENGTH]
        volleys++
        return horizontal to vertical
    }

    private fun walkable(x: Int, z: Int, level: Int): Boolean =
        deps.collision[x, z, level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private fun pools(npc: Npc, centre: CoordGrid) {
        pools.clear()
        val players = OcellusArena.players(npc, deps.playerList)
        for (quadrant in occupiedQuadrants(npc, centre)) {
            val underfoot =
                players.filter { quadrant.contains(centre, it.coords) }.map { it.coords }.distinct()
            val bounds = quadrant.bounds(centre)
            val free = ArrayList<CoordGrid>()
            for (x in bounds.minX..bounds.maxX) {
                for (z in bounds.minZ..bounds.maxZ) {
                    val tile = CoordGrid(x, z, centre.level)
                    if (walkable(x, z, centre.level) && tile !in underfoot) free.add(tile)
                }
            }
            val extra = minOf(POOLS_PER_QUADRANT - underfoot.size, free.size).coerceAtLeast(0)
            val chosen = underfoot + List(extra) { free.removeAt(deps.random.of(free.size)) }
            for (tile in chosen) deps.worldRepo.spotanimMap(SpotanimType(POOL.asRSCM(RSCMType.SPOTANIM)), tile)
            pools[quadrant] = chosen
        }
    }

    private fun detonatePools(npc: Npc) {
        val tiles = pools.values.flatten().toSet()
        for (player in OcellusArena.players(npc, deps.playerList)) {
            if (player.coords !in tiles) continue
            val damage = deps.random.of(POOL_DAMAGE)
            player.queueHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
    }

    private fun spawnPortals() {
        portals.clear()
        for ((quadrant, tiles) in pools) portals[quadrant] = deps.random.pick(tiles)
        for (tile in portals.values) portalSpotanim(PORTAL_SPAWN, tile)
    }

    private fun tickPortals(npc: Npc, centre: CoordGrid) {
        for (tile in portals.values) portalSpotanim(PORTAL_IDLE, tile)
        for (player in OcellusArena.players(npc, deps.playerList)) {
            val from = portals.entries.firstOrNull { it.value == player.coords }?.key ?: continue
            val next = from.clockwise
            val offsetX = abs(player.coords.x - centre.x)
            val offsetZ = abs(player.coords.z - centre.z)
            val destination =
                CoordGrid(centre.x + next.dx * offsetX, centre.z + next.dz * offsetZ, centre.level)
            PathingEntityCommon.telejump(player, deps.collision, destination)
        }
    }

    private fun endPortals() {
        for (tile in portals.values) portalSpotanim(PORTAL_DESPAWN, tile)
        portals.clear()
        pools.clear()
    }

    private fun portalSpotanim(spotanim: String, tile: CoordGrid) {
        deps.worldRepo.spotanimMap(SpotanimType(spotanim.asRSCM(RSCMType.SPOTANIM)), tile)
    }

    private fun fire(npc: Npc, centre: CoordGrid) {
        val (occupied, arms, gaps) = pending?.let { Triple(it.occupied, it.arms, it.gaps) } ?: return
        pending = null
        val players = OcellusArena.players(npc, deps.playerList)
        for (arm in arms) {
            val targets = occupied.filter { arm in it.arms }
            for (index in 0 until SEGMENTS) {
                walls[arm to index] = setWall(arm, arm.segment(centre, index), index, active = false)
                if (index == gaps[arm]) continue
                for (quadrant in targets) {
                    launch(arm, centre, index, quadrant)
                    for (player in players) hitIfInLane(npc, player, arm, centre, index, quadrant)
                }
            }
        }
    }

    private fun hitIfInLane(
        npc: Npc,
        player: Player,
        arm: Arm,
        centre: CoordGrid,
        index: Int,
        quadrant: Quadrant,
    ) {
        if (!arm.lane(centre, index, quadrant).contains(player.coords)) return
        val damage = deps.random.of(LANE_DAMAGE)
        player.queueHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    private fun launch(arm: Arm, centre: CoordGrid, index: Int, quadrant: Quadrant) {
        val lane = arm.lane(centre, index, quadrant)
        val origin = arm.segment(centre, index)
        val start =
            if (arm.horizontal) origin.translate(1, quadrant.dz) else origin.translate(quadrant.dx, 1)
        val end =
            if (arm.horizontal) {
                CoordGrid(origin.x, if (quadrant.dz > 0) lane.maxZ else lane.minZ, origin.level)
            } else {
                CoordGrid(if (quadrant.dx > 0) lane.maxX else lane.minX, origin.z, origin.level)
            }
        val distance = maxOf(abs(end.x - start.x), abs(end.z - start.z))
        deps.worldRepo.projAnim(
            ProjAnim(
                spotanim = PORTAL_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
                startHeight = PROJECTILE_HEIGHT,
                endHeight = PROJECTILE_HEIGHT,
                startTime = 0,
                endTime = (distance + 1) * PROJECTILE_CYCLES_PER_TILE,
                angle = PROJECTILE_ANGLE,
                progress = PROJECTILE_PROGRESS,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = start,
                endCoord = end,
            )
        )
    }

    private data class Rect(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
        fun contains(coord: CoordGrid): Boolean = coord.x in minX..maxX && coord.z in minZ..maxZ
    }

    private enum class Arm(val horizontal: Boolean, val sign: Int, val angle: LocAngle) {
        East(true, 1, LocAngle.West),
        West(true, -1, LocAngle.East),
        North(false, 1, LocAngle.South),
        South(false, -1, LocAngle.North);

        fun segment(centre: CoordGrid, index: Int): CoordGrid {
            val offset = if (sign > 0) 1 + index * 2 else -ARM_LENGTH + index * 2
            return if (horizontal) centre.translate(offset, 0) else centre.translate(0, offset)
        }

        fun lane(centre: CoordGrid, index: Int, quadrant: Quadrant): Rect {
            val origin = segment(centre, index)
            val q = quadrant.bounds(centre)
            return if (horizontal) {
                Rect(origin.x, origin.x + 1, q.minZ, q.maxZ)
            } else {
                Rect(q.minX, q.maxX, origin.z, origin.z + 1)
            }
        }
    }

    private enum class Quadrant(val dx: Int, val dz: Int) {
        NorthEast(1, 1),
        SouthEast(1, -1),
        SouthWest(-1, -1),
        NorthWest(-1, 1);

        val clockwise: Quadrant
            get() = entries[(ordinal + 1) % entries.size]

        val arms: List<Arm>
            get() = listOf(if (dx > 0) Arm.East else Arm.West, if (dz > 0) Arm.North else Arm.South)

        fun bounds(centre: CoordGrid): Rect {
            val x = if (dx > 0) centre.x + 1..centre.x + ARM_LENGTH else centre.x - ARM_LENGTH..centre.x - 1
            val z = if (dz > 0) centre.z + 1..centre.z + ARM_LENGTH else centre.z - ARM_LENGTH..centre.z - 1
            return Rect(x.first, x.last, z.first, z.last)
        }

        fun contains(centre: CoordGrid, coord: CoordGrid): Boolean = bounds(centre).contains(coord)
    }

    companion object {
        const val SPAWN = "ocellus.walls_spawn"
        const val TELEGRAPH = "ocellus.walls_telegraph"
        const val CLOSED_TELEGRAPH = "ocellus.walls_closed_telegraph"
        const val FIRE = "ocellus.walls_fire"
        const val POOLS = "ocellus.pools"
        const val POOLS_DETONATE = "ocellus.pools_detonate"
        const val PORTALS_SPAWN = "ocellus.portals_spawn"
        const val PORTALS_TICK = "ocellus.portals_tick"
        const val PORTALS_END = "ocellus.portals_end"
        const val DESPAWN = "ocellus.walls_despawn"
        const val CLEAR = "ocellus.walls_clear"

        private const val CENTRE_DX = 1
        private const val CENTRE_DZ = 4
        private const val SEGMENTS = 6
        private const val ARM_LENGTH = SEGMENTS * 2

        private const val GAP_PATTERN_LENGTH = 6
        private val GAP_PATTERNS =
            listOf(
                listOf(1 to 1, 3 to 1, 1 to 3, 1 to 1, 2 to 2, 4 to 1),
                listOf(2 to 2, 2 to 3, 1 to 4, 2 to 2, 2 to 4, 3 to 3),
                listOf(1 to 0, 1 to 3, 1 to 5, 3 to 3, 2 to 2, 3 to 0),
            )
        private const val POOLS_PER_QUADRANT = 13
        private const val POOL = "spotanim.vfx_ocellus_p2_aoe_dodge"
        private const val PORTAL_SPAWN = "spotanim.vfx_ocellus_p2_safe_portal_spawn"
        private const val PORTAL_IDLE = "spotanim.vfx_ocellus_p2_safe_portal_idle"
        private const val PORTAL_DESPAWN = "spotanim.vfx_ocellus_p2_safe_portal_despawn"
        private val POOL_DAMAGE = 5..25
        private val LANE_DAMAGE = 20..26

        const val WALL_INACTIVE = "loc.tfa_ocellus_quadrant_wall_inactive"
        const val WALL_ACTIVE = "loc.tfa_ocellus_quadrant_wall_active"
        const val WALL_IDLE_SEQ = "seq.vfx_ocellus_p2_portal_idle"
        const val WALL_DESPAWN_SEQ = "seq.vfx_ocellus_p2_portal_despawn"
        const val PORTAL_PROJECTILE = "spotanim.vfx_ocellus_portal_projectile"
        const val PROJECTILE_HEIGHT = 100
        const val PROJECTILE_ANGLE = 127
        const val PROJECTILE_PROGRESS = 100
        const val PROJECTILE_CYCLES_PER_TILE = 6
    }
}
