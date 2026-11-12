package org.rsmod.content.raids.tfa.raid.encounter.xarth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class XarthArenaTest {
    private val origin = CoordGrid(XarthArena.SPAWN_X, XarthArena.SPAWN_Z, 0)

    private fun fixture(rows: List<String>): Set<CoordGrid> =
        buildSet {
            rows.forEachIndexed { row, line ->
                line.forEachIndexed { col, ch ->
                    if (ch == '#') add(CoordGrid(4183 + col, 5543 - row, 0))
                }
            }
        }

    private fun struck(shape: Int, fallen: Int): Set<CoordGrid> {
        val heapTiles =
            XarthArena.standingHeaps(fallen).flatMap { XarthArena.heapTiles(origin, it) }.toSet()
        return XarthArena.waveTiles(origin, shape)
            .filter { it !in heapTiles && !XarthArena.umbra(origin, fallen, it) }
            .toSet()
    }

    @Test
    fun `north wave matches the captured tiles to within the one tile the shadow rule misses`() {
        val captured =
            fixture(
                listOf(
                    "##########....###",
                    ".#########...###.",
                    "..########...##..",
                    "...#######..##...",
                    "....#########....",
                    ".....#######.....",
                    "......#####......",
                    ".......BBB.......",
                    ".......BBB.......",
                    ".......BBB.......",
                )
            )
        val actual = struck(XarthArena.HEAP_NORTH, fallen = 0)
        val difference = captured.symmetricDifference(actual)
        assertEquals(setOf(CoordGrid(4193, 5543, 0)), difference)
    }

    @Test
    fun `east wave matches the captured tiles once the north heap is down`() {
        val captured =
            fixture(
                listOf(
                    "................#",
                    "...............##",
                    "..............###",
                    ".............####",
                    "............#####",
                    "...........####..",
                    "..........###....",
                    ".......BBB###....",
                    ".......BBB#######",
                    ".......BBB#######",
                    "..........#######",
                    "...........######",
                    "............#####",
                    ".............####",
                    "..............###",
                    "...............##",
                    "................#",
                )
            )
        assertEquals(captured, struck(XarthArena.HEAP_EAST, fallen = 1 shl XarthArena.HEAP_NORTH))
    }

    @Test
    fun `all round wave leaves the footprint and the shadow behind the last heap`() {
        val fallen = (1 shl XarthArena.HEAP_NORTH) or (1 shl XarthArena.HEAP_EAST)
        val actual = struck(XarthArena.ALL_ROUND, fallen)
        val heap = XarthArena.heapTiles(origin, XarthArena.HEAP_SOUTH)
        assertEquals(266, actual.size)
        assertFalse(actual.any { it in heap })
        assertFalse(actual.any { XarthArena.inFootprint(origin, it) })
        for (dz in listOf(-7, -6)) {
            assertFalse(CoordGrid(4194, origin.z + dz, 0) in actual)
        }
    }

    @Test
    fun `a standing fighter in line only softens the wave for those behind`() {
        val front = origin.translate(1, 5)
        val behind = origin.translate(1, 7)
        val fallen = XarthArena.ALL_HEAPS
        assertEquals(Cover.PARTIAL, XarthArena.cover(origin, fallen, behind, listOf(front)))
        assertEquals(Cover.NONE, XarthArena.cover(origin, fallen, front, listOf(behind)))
        assertEquals(Cover.NONE, XarthArena.cover(origin, fallen, behind, emptyList()))
    }

    @Test
    fun `phase two cycles last 2N plus 17 ticks`() {
        assertEquals(21, XarthArena.cycleLength(2))
        assertEquals(23, XarthArena.cycleLength(3))
    }

    @Test
    fun `phase two slots fall where the capture puts them`() {
        val ticks = ArrayList<Int>()
        var at = 0
        for (slot in 0 until XarthArena.slotCount(2)) {
            ticks += at
            at += XarthArena.slotGap(2, slot)
        }
        assertEquals(listOf(0, 2, 5, 9, 13, 17), ticks)
        assertEquals(21, at)
    }

    @Test
    fun `team size picks the magic count`() {
        assertEquals(2, XarthArena.magicsFor(3))
        assertEquals(2, XarthArena.magicsFor(4))
        assertEquals(3, XarthArena.magicsFor(5))
    }

    @Test
    fun `the corpse ring is the arena edge without its corners`() {
        val ring = XarthArena.perimeter(origin)
        assertEquals(60, ring.size)
        assertFalse(origin.translate(-7, -7) in ring)
        assertFalse(origin.translate(9, 9) in ring)
        assertTrue(origin.translate(-7, 0) in ring)
        assertTrue(origin.translate(2, 9) in ring)
    }

    @Test
    fun `a corpse walks the line to the footprint and stops beside it`() {
        var tile = CoordGrid(4183, 5542, 0)
        assertEquals(CoordGrid(4184, 5541, 0), XarthArena.stepTowardsFootprint(origin, tile))
        tile = XarthArena.stepTowardsFootprint(origin, tile)
        assertEquals(CoordGrid(4185, 5540, 0), XarthArena.stepTowardsFootprint(origin, tile))
        var steps = 0
        while (!XarthArena.adjacentToFootprint(origin, tile) && steps++ < 20) {
            tile = XarthArena.stepTowardsFootprint(origin, tile)
        }
        assertTrue(XarthArena.adjacentToFootprint(origin, tile))
        assertFalse(XarthArena.inFootprint(origin, tile))
    }

    @Test
    fun `fires only spread to tiles beside the fire, never onto the footprint`() {
        val fire = origin.translate(-1, 1)
        val next = XarthArena.neighbours(origin, fire)
        assertTrue(next.all { it.chebyshevDistance(fire) == 1 })
        assertFalse(next.any { XarthArena.inFootprint(origin, it) })
        assertFalse(fire in next)
    }

    private fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> = (this - other) + (other - this)
}
