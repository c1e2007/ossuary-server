package org.rsmod.content.minigames.colosseum

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.random.Random

internal data class RingTile(val x: Int, val z: Int, val ring: Int)

internal object ColosseumSolPatterns {
    const val MIN_X = 1818
    const val MAX_X = 1831
    const val MIN_Z = 3100
    const val MAX_Z = 3113
    const val BARRICADE_MIN_X = 1817
    const val BARRICADE_MAX_X = 1832
    const val BARRICADE_MIN_Z = 3099
    const val BARRICADE_MAX_Z = 3114
    const val SIZE = 5
    private const val LINE = 4
    private const val SHIELD_REACH = 7
    private val SHIELD_SAFE_RING = intArrayOf(4, 5)

    val DIRECTIONS: Map<String, Pair<Int, Int>> =
        mapOf(
            "N" to (0 to 1),
            "NE" to (1 to 1),
            "E" to (1 to 0),
            "SE" to (1 to -1),
            "S" to (0 to -1),
            "SW" to (-1 to -1),
            "W" to (-1 to 0),
            "NW" to (-1 to 1),
        )

    private class SpearShape(
        val strip: IntArray?,
        val first: List<Pair<Int, Int>>,
        val second: List<Pair<Int, Int>>,
    )

    private val SPEAR: Map<String, SpearShape> =
        mapOf(
            "W" to SpearShape(intArrayOf(-1, 0, 0, 5), listOf(-2 to 1, -2 to 3), listOf(-2 to 0, -2 to 2, -2 to 4)),
            "E" to SpearShape(intArrayOf(5, 0, 6, 5), listOf(6 to 1, 6 to 3), listOf(6 to 0, 6 to 2, 6 to 4)),
            "N" to SpearShape(intArrayOf(0, 5, 5, 6), listOf(1 to 6, 3 to 6), listOf(0 to 6, 2 to 6, 4 to 6)),
            "S" to SpearShape(intArrayOf(0, -1, 5, 0), listOf(1 to -2, 3 to -2), listOf(0 to -2, 2 to -2, 4 to -2)),
            "NE" to SpearShape(null, listOf(4 to 5, 5 to 4), listOf(6 to 6, 3 to 6, 6 to 3)),
            "SE" to SpearShape(null, listOf(5 to 0, 4 to -1), listOf(6 to 1, 6 to -2, 3 to -2)),
            "SW" to SpearShape(null, listOf(-1 to 0, 0 to -1), listOf(-2 to -2, -2 to 1, 1 to -2)),
            "NW" to SpearShape(null, listOf(-1 to 4, 0 to 5), listOf(-2 to 3, -2 to 6, 1 to 6)),
        )

    fun inside(x: Int, z: Int): Boolean = x in MIN_X..MAX_X && z in MIN_Z..MAX_Z

    fun directionTo(sol: ArenaTile, player: ArenaTile): String {
        val closestX = player.x.coerceIn(sol.x, sol.x + SIZE - 1)
        val closestZ = player.z.coerceIn(sol.z, sol.z + SIZE - 1)
        val dx = sign((player.x - closestX).toDouble()).toInt()
        val dz = sign((player.z - closestZ).toDouble()).toInt()
        return DIRECTIONS.entries.firstOrNull { it.value == dx to dz }?.key ?: "NW"
    }

    private fun square(centreX: Int, centreZ: Int, radius: Int, skipRing: Int = -1): List<RingTile> {
        val tiles = mutableListOf<RingTile>()
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val ring = max(abs(dx), abs(dz))
                if (ring == skipRing) continue
                val x = centreX + dx
                val z = centreZ + dz
                if (inside(x, z)) tiles += RingTile(x, z, ring)
            }
        }
        return tiles
    }

    fun line(startX: Int, startZ: Int, direction: Pair<Int, Int>, ring: Int): List<RingTile> {
        val tiles = mutableListOf<RingTile>()
        var n = 0
        while (n < LINE && inside(startX + direction.first * n, startZ + direction.second * n)) {
            tiles += RingTile(startX + direction.first * n, startZ + direction.second * n, ring + n)
            n++
        }
        return tiles
    }

    fun spear(sol: ArenaTile, player: ArenaTile, second: Boolean): List<RingTile> {
        val key = directionTo(sol, player)
        val shape = SPEAR.getValue(key)
        val diagonal = key.length == 2
        val radius = if (second && diagonal) 3 else 2
        val tiles = square(sol.x + 2, sol.z + 2, radius).toMutableList()
        var ring = radius + 1
        shape.strip?.let { strip ->
            for (x in sol.x + strip[0] until sol.x + strip[2]) {
                for (z in sol.z + strip[1] until sol.z + strip[3]) {
                    if (inside(x, z)) tiles += RingTile(x, z, ring)
                }
            }
            ring++
        }
        for ((dx, dz) in if (second) shape.second else shape.first) {
            tiles += line(sol.x + dx, sol.z + dz, DIRECTIONS.getValue(key), ring)
        }
        return tiles
    }

    fun shield(sol: ArenaTile, second: Boolean): List<RingTile> =
        square(sol.x + 2, sol.z + 2, SHIELD_REACH, SHIELD_SAFE_RING[if (second) 1 else 0])

    fun sandTargets(
        player: ArenaTile,
        count: Int,
        random: Random,
        taken: (ArenaTile) -> Boolean,
    ): List<ArenaTile> {
        val tiles = mutableListOf<ArenaTile>()
        val seen = mutableSetOf<ArenaTile>()
        var tries = 0
        while (tiles.size < count && tries < count * 20) {
            tries++
            val x = (player.x - 4 + random.nextInt(9)).coerceIn(MIN_X, MAX_X)
            val z = (player.z - 4 + random.nextInt(9)).coerceIn(MIN_Z, MAX_Z)
            val tile = ArenaTile(x, z)
            if (tile in seen || taken(tile)) continue
            seen += tile
            tiles += tile
        }
        return tiles
    }

    /** The barricade's edge tiles between the pillars, with the rotation each faces inward. */
    fun barricade(): List<Triple<Int, Int, Int>> {
        val tiles = mutableListOf<Triple<Int, Int, Int>>()
        for (x in BARRICADE_MIN_X + 2..BARRICADE_MAX_X - 2) {
            tiles += Triple(x, BARRICADE_MIN_Z, 2)
            tiles += Triple(x, BARRICADE_MAX_Z, 0)
        }
        for (z in BARRICADE_MIN_Z + 2..BARRICADE_MAX_Z - 2) {
            tiles += Triple(BARRICADE_MAX_X, z, 1)
            tiles += Triple(BARRICADE_MIN_X, z, 3)
        }
        return tiles
    }

    fun chebyshev(a: ArenaTile, b: ArenaTile): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    fun clampTo(value: Int, low: Int, high: Int): Int = min(high, max(low, value))
}
