package org.rsmod.content.raids.tfa.raid.encounter.xarth

import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal object XarthVarns {
    const val HP_MAX = "varn.xarth_hp_max"
    const val WAVE = "varn.xarth_wave"
    const val FIRST_HEAP = "varn.xarth_first_heap"
    const val HEAPS = "varn.xarth_heaps"
    const val SLOT = "varn.xarth_slot"
    const val MAGICS = "varn.xarth_magics"
    const val HEALED = "varn.xarth_healed"
    const val HEAL_PENDING = "varn.xarth_heal_pending"
    const val LAST_HIT = "varn.xarth_last_hit"
    const val DEAD = "varn.xarth_dead"
    const val DONE = "varn.xarth_done"
}

internal enum class Cover {
    NONE,
    PARTIAL,
    FULL,
}

/** Everything here is relative to Xarth's spawn tile (`origin`), so an instance copy works. */
internal object XarthArena {
    const val ENTRY_X = 4175
    const val ENTRY_Z = 5535
    const val SPAWN_X = 4190
    const val SPAWN_Z = 5534
    const val REACH = 30

    const val FOOTPRINT = 3
    const val ARENA_SW_DX = -7
    const val ARENA_SW_DZ = -7
    const val ARENA_NE_DX = 9
    const val ARENA_NE_DZ = 9
    const val HEAP_SIZE = 2

    const val HEAP_NORTH = 0
    const val HEAP_EAST = 1
    const val HEAP_SOUTH = 2
    const val ALL_ROUND = 3
    const val HEAP_COUNT = 3
    const val ALL_HEAPS = 0b111

    const val MAGIC_MAX = 20
    const val RANGED_MAX = 25
    const val MELEE_MAX = 30
    const val AOE_MIN = 14
    const val AOE_MAX = 19
    const val WAVE_MIN = 36
    const val WAVE_MAX = 54
    const val WAVE_PARTIAL_MIN = 5
    const val WAVE_PARTIAL_MAX = 9

    const val CORPSE_HP = 62
    const val HEAL_THRESHOLD = 150
    const val BLAST_PAUSE = 10
    const val BIG_TEAM = 5
    const val SMALL_MAGICS = 2
    const val BIG_MAGICS = 3

    const val INITIAL_FIRES = 7
    const val SPREAD_FIRES = 3
    const val FIRST_SPREAD = 28
    val SPREAD_PERIOD = 20..23

    const val BURN_PERIOD = 4
    const val BURN_CAP = 5
    const val BURN_WAVE_STACK = 3
    const val BURN_LINGER = 12
    const val BLEED_HITS = 4
    const val BLEED_PERIOD = 5
    const val BLEED_DAMAGE = 2
    const val BIND_TICKS = 4

    private const val MAP_SQUARE_MASK = 63
    private const val SOURCE_COUNT = 9
    private const val UMBRA_LOW = 0.45
    private const val UMBRA_HIGH = 0.55
    private const val SMALLER_MIN_HIDDEN = 7
    private const val PILLAR_INSET = 0.15
    private const val RANGED_SLOTS = 4
    private const val FIRE_ATTEMPTS = 20
    private val FIRE_DISTANCE_WEIGHTS = intArrayOf(0, 3, 37, 64, 88, 51, 23, 6)

    private val HEAP_SW_OFFSETS = arrayOf(3 to 6, 6 to 2, 3 to -4)

    val AREA: Area = area(spawnTile(ARENA_SW_DX, ARENA_SW_DZ), spawnTile(ARENA_NE_DX, ARENA_NE_DZ))

    fun inArena(origin: CoordGrid, tile: CoordGrid): Boolean {
        val dx = tile.x - origin.x
        val dz = tile.z - origin.z
        return tile.level == origin.level &&
            dx in ARENA_SW_DX..ARENA_NE_DX &&
            dz in ARENA_SW_DZ..ARENA_NE_DZ
    }

    fun inRoom(origin: CoordGrid, tile: CoordGrid): Boolean {
        val swX = origin.x - (SPAWN_X and MAP_SQUARE_MASK)
        val swZ = origin.z - (SPAWN_Z and MAP_SQUARE_MASK)
        return tile.level == origin.level &&
            tile.x in swX..swX + MAP_SQUARE_MASK &&
            tile.z in swZ..swZ + MAP_SQUARE_MASK
    }

    fun players(origin: CoordGrid, playerList: PlayerList): List<Player> =
        playerList.filter { it.isValidTarget() && inArena(origin, it.coords) }

    fun inFootprint(origin: CoordGrid, tile: CoordGrid): Boolean =
        tile.level == origin.level &&
            tile.x in origin.x until origin.x + FOOTPRINT &&
            tile.z in origin.z until origin.z + FOOTPRINT

    fun footprintTiles(origin: CoordGrid): List<CoordGrid> =
        buildList {
            for (dx in 0 until FOOTPRINT) for (dz in 0 until FOOTPRINT) add(origin.translate(dx, dz))
        }

    fun footprintDistance(origin: CoordGrid, tile: CoordGrid): Int {
        val dx = maxOf(origin.x - tile.x, 0, tile.x - (origin.x + FOOTPRINT - 1))
        val dz = maxOf(origin.z - tile.z, 0, tile.z - (origin.z + FOOTPRINT - 1))
        return maxOf(dx, dz)
    }

    fun arenaTiles(origin: CoordGrid): List<CoordGrid> =
        buildList {
            for (dx in ARENA_SW_DX..ARENA_NE_DX) {
                for (dz in ARENA_SW_DZ..ARENA_NE_DZ) add(origin.translate(dx, dz))
            }
        }

    fun heapSwOffset(heap: Int): Pair<Int, Int> = HEAP_SW_OFFSETS[heap]

    fun heapSw(origin: CoordGrid, heap: Int): CoordGrid {
        val (dx, dz) = heapSwOffset(heap)
        return origin.translate(dx, dz)
    }

    fun heapTiles(origin: CoordGrid, heap: Int): List<CoordGrid> {
        val sw = heapSw(origin, heap)
        return buildList {
            for (dx in 0 until HEAP_SIZE) for (dz in 0 until HEAP_SIZE) add(sw.translate(dx, dz))
        }
    }

    fun standingHeaps(fallenMask: Int): List<Int> =
        (0 until HEAP_COUNT).filter { fallenMask and (1 shl it) == 0 }

    /**
     * A 90 degree wedge out of the footprint towards [shape]'s heap, one tile wider each side per
     * row; [ALL_ROUND] is the whole arena. The footprint itself is never part of it.
     */
    fun waveTiles(origin: CoordGrid, shape: Int): List<CoordGrid> =
        arenaTiles(origin).filter { inWave(origin, shape, it) }

    fun inWave(origin: CoordGrid, shape: Int, tile: CoordGrid): Boolean {
        if (!inArena(origin, tile) || inFootprint(origin, tile)) return false
        if (shape == ALL_ROUND) return true
        val dx = tile.x - origin.x
        val dz = tile.z - origin.z
        val last = FOOTPRINT - 1
        return when (shape) {
            HEAP_NORTH -> dz > last && dx in -(dz - last)..(last + dz - last)
            HEAP_SOUTH -> dz < 0 && dx in dz..(last - dz)
            else -> dx > last && dz in -(dx - last)..(last + dx - last)
        }
    }

    /** The heap a wave number tips over: the first is [first], then east, then what remains. */
    fun heapForWave(wave: Int, first: Int): Int =
        when (wave) {
            0 -> first
            1 -> HEAP_EAST
            else -> if (first == HEAP_NORTH) HEAP_SOUTH else HEAP_NORTH
        }

    fun shapeForWave(wave: Int, first: Int): Int =
        if (wave >= 2) ALL_ROUND else heapForWave(wave, first)

    /** True when no line from any footprint tile to the tile's middle gets past a standing heap. */
    fun umbra(origin: CoordGrid, fallenMask: Int, tile: CoordGrid): Boolean {
        val heaps = standingHeaps(fallenMask)
        if (heaps.isEmpty()) return false
        return sources(origin).all { (sx, sz) ->
            listOf(UMBRA_LOW, UMBRA_HIGH).all { cx ->
                listOf(UMBRA_LOW, UMBRA_HIGH).all { cz ->
                    heaps.any { blocks(origin, it, sx, sz, tile.x + cx, tile.z + cz) }
                }
            }
        }
    }

    fun hiddenSources(origin: CoordGrid, fallenMask: Int, tile: CoordGrid): Int {
        val heaps = standingHeaps(fallenMask)
        return sources(origin).count { (sx, sz) ->
            heaps.any { blocks(origin, it, sx, sz, tile.x + 0.5, tile.z + 0.5) }
        }
    }

    fun smallerExplosion(origin: CoordGrid, fallenMask: Int, tile: CoordGrid): Boolean =
        !umbra(origin, fallenMask, tile) &&
            hiddenSources(origin, fallenMask, tile) in SMALLER_MIN_HIDDEN until SOURCE_COUNT

    /**
     * How well [tile] is shielded: a standing heap shelters whole tiles, another fighter standing
     * between the footprint and the tile only softens it.
     */
    fun cover(
        origin: CoordGrid,
        fallenMask: Int,
        tile: CoordGrid,
        pillars: Collection<CoordGrid>,
    ): Cover =
        when {
            umbra(origin, fallenMask, tile) -> Cover.FULL
            pillars.any { it != tile && pillarBlocks(origin, it, tile) } -> Cover.PARTIAL
            else -> Cover.NONE
        }

    private fun pillarBlocks(origin: CoordGrid, pillar: CoordGrid, tile: CoordGrid): Boolean {
        val cx = origin.x + FOOTPRINT / 2.0
        val cz = origin.z + FOOTPRINT / 2.0
        if (footprintDistance(origin, pillar) >= footprintDistance(origin, tile)) return false
        return crosses(
            cx,
            cz,
            tile.x + 0.5,
            tile.z + 0.5,
            pillar.x + PILLAR_INSET,
            pillar.z + PILLAR_INSET,
            pillar.x + 1 - PILLAR_INSET,
            pillar.z + 1 - PILLAR_INSET,
        )
    }

    private fun sources(origin: CoordGrid): List<Pair<Double, Double>> =
        footprintTiles(origin).map { (it.x + 0.5) to (it.z + 0.5) }

    private fun blocks(
        origin: CoordGrid,
        heap: Int,
        sx: Double,
        sz: Double,
        tx: Double,
        tz: Double,
    ): Boolean {
        val sw = heapSw(origin, heap)
        return crosses(
            sx,
            sz,
            tx,
            tz,
            sw.x.toDouble(),
            sw.z.toDouble(),
            (sw.x + HEAP_SIZE).toDouble(),
            (sw.z + HEAP_SIZE).toDouble(),
        )
    }

    private fun crosses(
        x0: Double,
        z0: Double,
        x1: Double,
        z1: Double,
        minX: Double,
        minZ: Double,
        maxX: Double,
        maxZ: Double,
    ): Boolean {
        var t0 = 0.0
        var t1 = 1.0
        val dx = x1 - x0
        val dz = z1 - z0
        val p = doubleArrayOf(-dx, dx, -dz, dz)
        val q = doubleArrayOf(x0 - minX, maxX - x0, z0 - minZ, maxZ - z0)
        for (i in 0 until 4) {
            if (p[i] == 0.0) {
                if (q[i] < 0) return false
            } else {
                val r = q[i] / p[i]
                if (p[i] < 0) {
                    if (r > t1) return false
                    if (r > t0) t0 = r
                } else {
                    if (r < t0) return false
                    if (r < t1) t1 = r
                }
            }
        }
        return true
    }

    fun perimeter(origin: CoordGrid): List<CoordGrid> =
        arenaTiles(origin).filter { tile ->
            val dx = tile.x - origin.x
            val dz = tile.z - origin.z
            val onEdgeX = dx == ARENA_SW_DX || dx == ARENA_NE_DX
            val onEdgeZ = dz == ARENA_SW_DZ || dz == ARENA_NE_DZ
            (onEdgeX || onEdgeZ) && !(onEdgeX && onEdgeZ)
        }

    fun edgeTile(origin: CoordGrid, random: GameRandom): CoordGrid {
        val edge = perimeter(origin)
        return edge[random.of(edge.size)]
    }

    /** The tile one step along the straight line to the footprint's middle. */
    fun stepTowardsFootprint(origin: CoordGrid, from: CoordGrid): CoordGrid {
        val dx = origin.x + FOOTPRINT / 2 - from.x
        val dz = origin.z + FOOTPRINT / 2 - from.z
        val longest = maxOf(abs(dx), abs(dz))
        if (longest == 0) return from
        val sx = if (abs(dx) * 2 >= longest) dx.sign else 0
        val sz = if (abs(dz) * 2 >= longest) dz.sign else 0
        return from.translate(sx, sz)
    }

    fun adjacentToFootprint(origin: CoordGrid, tile: CoordGrid): Boolean =
        footprintDistance(origin, tile) <= 1

    fun initialFires(origin: CoordGrid, random: GameRandom, count: Int): List<CoordGrid> {
        val byDistance = arenaTiles(origin).groupBy { footprintDistance(origin, it) }
        val total = FIRE_DISTANCE_WEIGHTS.sum()
        val chosen = LinkedHashSet<CoordGrid>()
        var guard = 0
        while (chosen.size < count && guard++ < count * FIRE_ATTEMPTS) {
            var roll = random.of(total)
            var distance = 0
            while (roll >= FIRE_DISTANCE_WEIGHTS[distance]) roll -= FIRE_DISTANCE_WEIGHTS[distance++]
            val ring = byDistance[distance].orEmpty()
            if (ring.isNotEmpty()) chosen += ring[random.of(ring.size)]
        }
        return chosen.toList()
    }

    fun neighbours(origin: CoordGrid, at: CoordGrid): List<CoordGrid> =
        buildList {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val tile = at.translate(dx, dz)
                    if ((dx != 0 || dz != 0) && inArena(origin, tile) && !inFootprint(origin, tile)) {
                        add(tile)
                    }
                }
            }
        }

    fun magicsFor(teamSize: Int): Int = if (teamSize >= BIG_TEAM) BIG_MAGICS else SMALL_MAGICS

    /** Ticks after slot [slot] of a phase-two cycle with [magics] magic casts before the next slot. */
    fun slotGap(magics: Int, slot: Int): Int =
        when {
            slot < magics - 1 -> 2
            slot == magics - 1 -> 3
            else -> 4
        }

    fun cycleLength(magics: Int): Int = (0 until magics + RANGED_SLOTS).sumOf { slotGap(magics, it) }

    fun slotCount(magics: Int): Int = magics + RANGED_SLOTS
}
