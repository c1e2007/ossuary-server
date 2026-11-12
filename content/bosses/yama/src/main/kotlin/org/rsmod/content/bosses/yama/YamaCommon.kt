package org.rsmod.content.bosses.yama

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Shared constants, tile helpers, distance and box helpers and the arena bounds
 * (Common.Yama.js and data/yama.json). Every other Yama file reads bounds and helpers from here.
 *
 * Tiles are the cache map's. The instance copies the map squares (22, 157) and (23, 157) with
 * their origin at [SOURCE_ORIGIN_X], [SOURCE_ORIGIN_Z], so a cache tile maps into the instance by
 * a fixed offset from the arrival tile ([ARRIVAL]).
 */
internal object YamaCommon {
    /** South-west tile of the copied map squares (map square (22, 157)). */
    const val SOURCE_ORIGIN_X = 1408
    const val SOURCE_ORIGIN_Z = 10048

    val ARRIVAL = CoordGrid(1503, 10050, 0)
    val ARRIVAL_FACE = CoordGrid(1503, 10052, 0)
    val THRONE_TILE = CoordGrid(1500, 10089, 0)
    val VOICE_RESPAWN = CoordGrid(1439, 10076, 0)
    val YAMA_TILE = CoordGrid(1501, 10076, 0)
    val ARENA_CENTRE = CoordGrid(1503, 10078, 0)
    val LOOT_TILE = CoordGrid(1503, 10088, 0)

    const val MUSIC_TRACK = 280
    const val FADE_MOVE_TICKS = 2
    const val FADE_IN_TICKS = 3

    const val YAMA_HITPOINTS = 2500
    val THRESHOLDS = intArrayOf(1666, 833)
    const val GLYPH_SIZE = 3

    /** One row of the arena floor: [y] and the x ranges that are floor on it. */
    class ArenaRow(val y: Int, val ranges: List<IntRange>)

    val ARENA_ROWS =
        listOf(
            ArenaRow(10093, listOf(1499..1507)),
            ArenaRow(10092, listOf(1499..1507)),
            ArenaRow(10091, listOf(1499..1507)),
            ArenaRow(10090, listOf(1500..1507)),
            ArenaRow(10088, listOf(1498..1507)),
            ArenaRow(10087, listOf(1497..1510)),
            ArenaRow(10086, listOf(1492..1492, 1495..1515)),
            ArenaRow(10085, listOf(1490..1516)),
            ArenaRow(10084, listOf(1491..1516)),
            ArenaRow(10083, listOf(1490..1515)),
            ArenaRow(10082, listOf(1490..1515)),
            ArenaRow(10081, listOf(1490..1515)),
            ArenaRow(10080, listOf(1491..1515)),
            ArenaRow(10079, listOf(1491..1515)),
            ArenaRow(10078, listOf(1491..1514)),
            ArenaRow(10077, listOf(1490..1514)),
            ArenaRow(10076, listOf(1490..1514)),
            ArenaRow(10075, listOf(1490..1514)),
            ArenaRow(10074, listOf(1489..1516)),
            ArenaRow(10073, listOf(1491..1515)),
            ArenaRow(10072, listOf(1490..1514)),
            ArenaRow(10071, listOf(1490..1513)),
            ArenaRow(10070, listOf(1490..1513)),
            ArenaRow(10069, listOf(1493..1512)),
            ArenaRow(10068, listOf(1500..1506)),
        )

    private val ROW_BY_Y = ARENA_ROWS.associateBy { it.y }

    fun onFloor(x: Int, z: Int): Boolean = ROW_BY_Y[z]?.ranges?.any { x in it } == true

    /** Every arena floor tile as an (x, z) pair on the cache map. */
    val FLOOR_TILES: List<Pair<Int, Int>> =
        ARENA_ROWS.flatMap { row -> row.ranges.flatMap { range -> range.map { x -> x to row.y } } }

    /** Chebyshev distance between a tile and a `size` x `size` box whose south-west is `box`. */
    fun gap(a: Int, aSize: Int, b: Int, bSize: Int): Int =
        maxOf(0, maxOf(a - (b + bSize - 1), b - (a + aSize - 1)))

    const val CLIENT_CYCLES_PER_TICK = 30
}

/** Where cache tiles land in a player's instance. */
internal class YamaArena(val origin: CoordGrid) {
    /** The instance copy of a cache tile. */
    fun tile(x: Int, z: Int, level: Int = 0): CoordGrid =
        origin.translate(x - YamaCommon.SOURCE_ORIGIN_X, z - YamaCommon.SOURCE_ORIGIN_Z).let {
            CoordGrid(it.x, it.z, level)
        }

    fun tile(coord: CoordGrid): CoordGrid = tile(coord.x, coord.z, coord.level)

    /** The cache tile an instance tile copies. */
    fun source(coord: CoordGrid): CoordGrid =
        CoordGrid(
            coord.x - origin.x + YamaCommon.SOURCE_ORIGIN_X,
            coord.z - origin.z + YamaCommon.SOURCE_ORIGIN_Z,
            coord.level,
        )
}

internal enum class YamaResult {
    NONE,
    VICTORIOUS,
    DEFEATED,
    ESCAPED,
}

private val CONTRACT_OFFERED = AttributeKey<Boolean>(persistenceKey = "yama_contract_offered")
private val CONTRACT_SIGNED = AttributeKey<Boolean>(persistenceKey = "yama_contract_signed")
private val LAST_RESULT = AttributeKey<Int>(persistenceKey = "yama_last_result")
private val BEST_TICKS = AttributeKey<Int>(persistenceKey = "yama_best_ticks")

internal var Player.voiceProgress by intVarBit(YamaIds.VB_VOICE_PROGRESS)
internal var Player.yamaKills by intVarp(YamaIds.VP_KILLS)

internal var Player.contractOffered: Boolean
    get() = attr[CONTRACT_OFFERED] ?: false
    set(value) {
        attr[CONTRACT_OFFERED] = value
    }

internal var Player.contractSigned: Boolean
    get() = attr[CONTRACT_SIGNED] ?: false
    set(value) {
        attr[CONTRACT_SIGNED] = value
    }

internal var Player.lastYamaResult: YamaResult
    get() = YamaResult.entries.getOrElse(attr[LAST_RESULT] ?: 0) { YamaResult.NONE }
    set(value) {
        attr[LAST_RESULT] = value.ordinal
    }

internal var Player.yamaBestTicks: Int
    get() = attr[BEST_TICKS] ?: 0
    set(value) {
        attr[BEST_TICKS] = value
    }

/** A fight's length as Yama's messages print it: m:ss. */
internal fun formatYamaTicks(ticks: Int): String {
    val seconds = Math.round(ticks * 0.6).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
