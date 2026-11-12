package org.rsmod.content.raids.tfa.raid.encounter.bork

import kotlin.math.abs
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal object BorkVarns {
    const val HP_MAX = "varn.bork_hp_max"
    const val SMORK_MAX = "varn.bork_smork_max"
    const val SMORK_PCT = "varn.bork_smork_pct"
    const val TEMPO = "varn.bork_tempo"
    const val DAZED = "varn.bork_dazed"
    const val ARMOUR = "varn.bork_armour"
    const val MAGIC_ARMOUR = "varn.bork_magic_armour"
    const val BORK_DEAD = "varn.bork_dead"
    const val BORK_REMOVED = "varn.bork_removed"
    const val SMORK_DEAD = "varn.bork_smork_dead"
    const val SMORK_REMOVED = "varn.bork_smork_removed"
    const val STOMP_SIDE = "varn.bork_stomp_side"
    const val DONE = "varn.bork_done"
    const val GUARANTEED_HIT = "varn.guaranteed_hit"
}

internal object BorkArena {
    const val ENTRY_X = 4173
    const val ENTRY_Z = 5599
    const val SPAWN_X = 4194
    const val SPAWN_Z = 5603
    const val REACH = 30

    const val GONG_MIN = 16
    const val GONG_MAX = 32
    const val VOLLEY_MAX = 25
    const val FRENZY_VOLLEY_MAX = VOLLEY_MAX * 3 / 2
    const val PRAYER_LEAK_PERCENT = 25
    const val NAPALM_MIN = 21
    const val NAPALM_MAX = 28
    const val NAPALM_BORK_MIN = 25
    const val NAPALM_BORK_MAX = 30
    const val OIL_MIN = 6
    const val OIL_MAX = 7
    const val BURN_HIT = 2
    const val BALL_MAX = 40
    const val ENRAGED_GONG_MAX = 35

    const val SIZE = 4
    const val SMORK_DX = -4
    const val SMORK_DZ = -5
    const val ARENA_SW_DX = -10
    const val ARENA_SW_DZ = -12
    const val ARENA_NE_DX = 4
    const val ARENA_NE_DZ = 4
    const val TOWER_SW_DX = -4
    const val TOWER_SW_DZ = -5
    const val TOWER_SIZE = 3
    const val HANDLER_DX = -33
    const val HANDLER_DZ = -34
    private const val MAP_SQUARE_MASK = 63
    private const val SHADOW_INSET = 0.3
    private const val ARMOUR_BASE = 8
    private const val ARMOUR_STEP = 2
    private const val ARMOUR_MAX = 14
    private const val MAGIC_STEP = 2
    private const val MAGIC_MAX = 6

    val SPACING = intArrayOf(7, 6, 5, 4)
    val FLIGHT = intArrayOf(150, 120, 90, 90)

    val AREA: Area = area(spawnTile(ARENA_SW_DX, ARENA_SW_DZ), spawnTile(ARENA_NE_DX, ARENA_NE_DZ))

    private val CORNERS =
        setOf(
            ARENA_SW_DX to ARENA_SW_DZ,
            ARENA_SW_DX to ARENA_NE_DZ,
            ARENA_NE_DX to ARENA_SW_DZ,
            ARENA_NE_DX to ARENA_NE_DZ,
        )

    fun inArena(origin: CoordGrid, tile: CoordGrid): Boolean {
        val dx = tile.x - origin.x
        val dz = tile.z - origin.z
        return tile.level == origin.level &&
            dx in ARENA_SW_DX..ARENA_NE_DX &&
            dz in ARENA_SW_DZ..ARENA_NE_DZ &&
            (dx to dz) !in CORNERS
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

    fun arenaTiles(origin: CoordGrid): List<CoordGrid> =
        buildList {
            for (dx in ARENA_SW_DX..ARENA_NE_DX) {
                for (dz in ARENA_SW_DZ..ARENA_NE_DZ) {
                    if ((dx to dz) !in CORNERS) add(origin.translate(dx, dz))
                }
            }
        }

    fun distance(npc: Npc, tile: CoordGrid): Int {
        val dx = maxOf(npc.coords.x - tile.x, 0, tile.x - (npc.coords.x + SIZE - 1))
        val dz = maxOf(npc.coords.z - tile.z, 0, tile.z - (npc.coords.z + SIZE - 1))
        return maxOf(dx, dz)
    }

    fun dustOrigin(npc: Npc): CoordGrid = npc.coords.translate(2, 2)

    /** Rings 1..[reach] around his footprint, minus what the tower blocks. */
    fun gongTiles(npc: Npc, reach: Int): List<CoordGrid> =
        arenaTiles(npc.spawnCoords).filter { distance(npc, it) in 1..reach && !shadowed(npc, it) }

    fun gongReach(npc: Npc, fighters: List<Player>, first: Boolean): Int {
        if (!first) return 1
        val nearest = fighters.minOfOrNull { distance(npc, it.coords) } ?: return 1
        return if (nearest in 2..3) nearest else 1
    }

    fun stompTiles(npc: Npc): List<CoordGrid> =
        arenaTiles(npc.spawnCoords).filter { !shadowed(npc, it) }

    fun side(npc: Npc, toward: CoordGrid): Int {
        val dx = toward.x - (npc.coords.x + SIZE / 2.0 - 0.5)
        val dz = toward.z - (npc.coords.z + SIZE / 2.0 - 0.5)
        return if (abs(dz) >= abs(dx)) {
            if (dz >= 0) 0 else 2
        } else {
            if (dx >= 0) 1 else 3
        }
    }

    /** The two non-corner edge tiles on [side] (0 north, 1 east, 2 south, 3 west), clockwise first. */
    fun feet(npc: Npc, side: Int): List<CoordGrid> {
        val at = npc.coords
        val last = SIZE - 1
        return when (side) {
            0 -> listOf(at.translate(2, last), at.translate(1, last))
            1 -> listOf(at.translate(last, 1), at.translate(last, 2))
            2 -> listOf(at.translate(1, 0), at.translate(2, 0))
            else -> listOf(at.translate(0, 2), at.translate(0, 1))
        }
    }

    fun foot(npc: Npc, side: Int, index: Int): CoordGrid = feet(npc, side)[index % 2]

    /** True when every line from his middle 2x2 to a point 0.3 inside the tile's corners crosses the tower. */
    fun shadowed(npc: Npc, tile: CoordGrid): Boolean {
        val origin = npc.spawnCoords
        val tx = (origin.x + TOWER_SW_DX).toDouble()
        val tz = (origin.z + TOWER_SW_DZ).toDouble()
        if (tile.x >= tx && tile.x < tx + TOWER_SIZE && tile.z >= tz && tile.z < tz + TOWER_SIZE) {
            return true
        }
        val sources =
            listOf(1.0, 3.0).flatMap { ox ->
                listOf(1.0, 3.0).map { oz -> (npc.coords.x + ox) to (npc.coords.z + oz) }
            }
        val near = SHADOW_INSET
        val far = 1 - SHADOW_INSET
        val targets =
            listOf(near, far).flatMap { cx ->
                listOf(near, far).map { cz -> (tile.x + cx) to (tile.z + cz) }
            }
        return sources.all { (sx, sz) ->
            targets.all { (ex, ez) ->
                crosses(sx, sz, ex, ez, tx, tz, tx + TOWER_SIZE, tz + TOWER_SIZE)
            }
        }
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

    /**
     * The splash around [tile]: the tile and a 2x3 block beyond it (or a 2x2 on a diagonal) towards the
     * tower, or away from it when the tile is within 3 of the tower, plus up to two drips beside it.
     */
    fun napalmSplash(origin: CoordGrid, tile: CoordGrid, random: GameRandom): List<CoordGrid> {
        val towerX = origin.x + TOWER_SW_DX + 1
        val towerZ = origin.z + TOWER_SW_DZ + 1
        var dx = towerX - tile.x
        var dz = towerZ - tile.z
        if (maxOf(abs(dx), abs(dz)) <= TOWER_SIZE + 1) {
            dx = -dx
            dz = -dz
        }
        val sx = Integer.signum(dx)
        val sz = Integer.signum(dz)
        val diagonal = minOf(abs(dx), abs(dz)) * 2 > maxOf(abs(dx), abs(dz))
        val tiles = LinkedHashSet<CoordGrid>()
        tiles += tile
        if (diagonal) {
            tiles += tile.translate(sx, 0)
            tiles += tile.translate(0, sz)
            tiles += tile.translate(sx, sz)
        } else {
            val alongX = abs(dx) >= abs(dz)
            for (step in 1..2) {
                for (side in -1..1) {
                    tiles +=
                        if (alongX) tile.translate(sx * step, side) else tile.translate(side, sz * step)
                }
            }
        }
        repeat(random.of(3)) {
            tiles += tile.translate(random.of(3) - 1, random.of(3) - 1)
        }
        return tiles.toList()
    }

    fun armour(smorkPct: Int): Int =
        (ARMOUR_BASE + ARMOUR_STEP * ((100 - smorkPct) / 25)).coerceAtMost(ARMOUR_MAX)

    fun magicArmour(smorkPct: Int): Int =
        (MAGIC_STEP * ((100 - smorkPct) / 25)).coerceAtMost(MAGIC_MAX)

    fun tempoFor(smorkPct: Int): Int =
        when {
            smorkPct <= 25 -> 3
            smorkPct <= 50 -> 2
            smorkPct <= 75 -> 1
            else -> 0
        }
}
