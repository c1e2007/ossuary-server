package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal object DreadnothVarns {
    const val HP_MAX = "varn.dreadnoth_hp_max"
    const val ABSORBED = "varn.dreadnoth_absorbed"
    const val DEALT = "varn.dreadnoth_dealt"
    const val SPLIT = "varn.dreadnoth_split"
    const val WAVES_DONE = "varn.dreadnoth_waves_done"
    const val SPAWN_BUDGET = "varn.dreadnoth_spawn_budget"
    const val DONE = "varn.dreadnoth_done"
}

internal object DreadnothArena {
    private const val SW_DX = -8
    private const val SW_DZ = -5
    private const val NE_DX = 9
    private const val NE_DZ = 9
    private const val SIZE = 5
    private const val HALF = SIZE / 2
    private const val JUMP_REACH = 5
    private const val LANDING_MIN_DX = -5
    private const val LANDING_MAX_DX = 5
    private const val LANDING_MIN_DZ = -5
    private const val LANDING_MAX_DZ = 5
    private const val COMPASS_STEP_DEGREES = 45.0
    private const val HOP_MAX = 2
    private const val MAP_SQUARE_MASK = 63

    const val REACH = 30
    const val ENTRY_X = 4174
    const val ENTRY_Z = 5664
    const val SPAWN_X = 4189
    const val SPAWN_Z = 5661

    val AREA: Area = area(spawnTile(SW_DX, SW_DZ), spawnTile(NE_DX, NE_DZ))

    fun contains(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(SW_DX, SW_DZ)
        val ne = origin.translate(NE_DX, NE_DZ)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    fun inRoom(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(-(SPAWN_X and MAP_SQUARE_MASK), -(SPAWN_Z and MAP_SQUARE_MASK))
        return tile.level == sw.level &&
            tile.x in sw.x..sw.x + MAP_SQUARE_MASK &&
            tile.z in sw.z..sw.z + MAP_SQUARE_MASK
    }

    fun players(origin: CoordGrid, playerList: PlayerList): List<Player> =
        playerList.filter { it.isValidTarget() && contains(origin, it.coords) }

    fun centre(npc: Npc): CoordGrid = npc.coords.translate(HALF, HALF)

    fun footprint(npc: Npc): List<CoordGrid> =
        buildList {
            for (dx in 0 until SIZE) for (dz in 0 until SIZE) add(npc.coords.translate(dx, dz))
        }

    fun inFootprint(npc: Npc, tile: CoordGrid): Boolean =
        tile.level == npc.coords.level &&
            tile.x in npc.coords.x until npc.coords.x + SIZE &&
            tile.z in npc.coords.z until npc.coords.z + SIZE

    /** The 7x7 ring around the 5x5 minus its four corner tiles, which no footprint edge touches. */
    fun edgeAdjacent(npc: Npc, player: Player): Boolean {
        val dx = player.coords.x - npc.coords.x
        val dz = player.coords.z - npc.coords.z
        if (player.coords.level != npc.coords.level) return false
        if (dx !in -1..SIZE || dz !in -1..SIZE) return false
        val cornerX = dx == -1 || dx == SIZE
        val cornerZ = dz == -1 || dz == SIZE
        return !(cornerX && cornerZ)
    }

    /**
     * South-west tile of the enrage landing: centred on [target] when it is within [JUMP_REACH] of
     * the boss, otherwise [JUMP_REACH] tiles along the nearest of the eight compass directions
     * towards it. Clamped so the 5x5 stays inside the arena.
     */
    fun landing(npc: Npc, target: Player): CoordGrid {
        val from = centre(npc)
        val dx = target.coords.x - from.x
        val dz = target.coords.z - from.z
        val centreX: Int
        val centreZ: Int
        if (maxOf(abs(dx), abs(dz)) <= JUMP_REACH) {
            centreX = target.coords.x
            centreZ = target.coords.z
        } else {
            val step = compassStep(dx, dz)
            centreX = from.x + step.first * JUMP_REACH
            centreZ = from.z + step.second * JUMP_REACH
        }
        val spawn = npc.spawnCoords
        val swX =
            (centreX - HALF).coerceIn(spawn.x + LANDING_MIN_DX, spawn.x + LANDING_MAX_DX)
        val swZ =
            (centreZ - HALF).coerceIn(spawn.z + LANDING_MIN_DZ, spawn.z + LANDING_MAX_DZ)
        return CoordGrid(swX, swZ, from.level)
    }

    fun landingCentre(npc: Npc, target: Player): CoordGrid =
        landing(npc, target).translate(HALF, HALF)

    fun jelliesUnder(boss: Npc, owned: List<Npc>, jellyType: Int): List<Npc> =
        owned.filter { it.visType.id == jellyType && it.isSlotAssigned && inFootprint(boss, it.coords) }

    fun dyingJellies(owned: List<Npc>, jellyType: Int): List<Npc> =
        owned.filter {
            it.visType.id == jellyType &&
                it.isSlotAssigned &&
                !it.ignoreCombatInteractions &&
                it.hitpoints <= 1
        }

    fun hop(jelly: Npc): CoordGrid {
        val box = (-HOP_MAX..HOP_MAX).toList()
        val offsets =
            box.flatMap { dx -> box.map { dz -> dx to dz } }.filter { (dx, dz) -> dx != 0 || dz != 0 }
        val (dx, dz) = offsets[Random.nextInt(offsets.size)]
        return jelly.coords.translate(dx, dz)
    }

    private fun compassStep(dx: Int, dz: Int): Pair<Int, Int> {
        val degrees = Math.toDegrees(atan2(dz.toDouble(), dx.toDouble()))
        val octant = (degrees / COMPASS_STEP_DEGREES).roundToInt()
        return when (Math.floorMod(octant, 8)) {
            0 -> 1 to 0
            1 -> 1 to 1
            2 -> 0 to 1
            3 -> -1 to 1
            4 -> -1 to 0
            5 -> -1 to -1
            6 -> 0 to -1
            else -> 1 to -1
        }
    }
}
