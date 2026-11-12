package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class DreadnothArenaTest {
    private val spawn = CoordGrid(4189, 5661)
    private val boss = Npc(NpcServerType(id = 1, name = "Boss", size = 5, hitpoints = 100), spawn)

    private fun player(x: Int, z: Int) = Player().apply { coords = CoordGrid(x, z) }

    @Test
    fun `melee reaches every edge-adjacent tile of the footprint but not the ring corners`() {
        assertTrue(DreadnothArena.edgeAdjacent(boss, player(4188, 5661)))
        assertTrue(DreadnothArena.edgeAdjacent(boss, player(4193, 5665)))
        assertTrue(DreadnothArena.edgeAdjacent(boss, player(4191, 5660)))
        assertTrue(DreadnothArena.edgeAdjacent(boss, player(4191, 5663)))
        assertFalse(DreadnothArena.edgeAdjacent(boss, player(4188, 5660)))
        assertFalse(DreadnothArena.edgeAdjacent(boss, player(4194, 5660)))
        assertFalse(DreadnothArena.edgeAdjacent(boss, player(4188, 5666)))
        assertFalse(DreadnothArena.edgeAdjacent(boss, player(4194, 5666)))
        assertFalse(DreadnothArena.edgeAdjacent(boss, player(4195, 5663)))
    }

    @Test
    fun `landing centres on a player within reach`() {
        val landing = DreadnothArena.landing(boss, player(4192, 5663))
        assertEquals(CoordGrid(4190, 5661), landing)
        assertEquals(CoordGrid(4192, 5663), DreadnothArena.landingCentre(boss, player(4192, 5663)))
    }

    @Test
    fun `landing snaps to the nearest compass direction beyond reach`() {
        val centre = CoordGrid(4191, 5663)
        val east = DreadnothArena.landing(boss, player(4198, 5664))
        assertEquals(CoordGrid(centre.x + 5 - 2, centre.z - 2), east)
        val north = DreadnothArena.landing(boss, player(4190, 5670))
        assertEquals(CoordGrid(centre.x - 2, centre.z + 5 - 2), north)
    }

    @Test
    fun `landing stays inside the arena`() {
        val west = DreadnothArena.landing(boss, player(4181, 5663))
        assertEquals(4184, west.x)
        val east = DreadnothArena.landing(boss, player(4198, 5670))
        assertTrue(east.x <= 4194 && east.z <= 5666)
        val south = DreadnothArena.landing(boss, player(4191, 5656))
        assertTrue(south.z >= 5656)
    }
}
