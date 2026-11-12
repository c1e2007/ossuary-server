package org.rsmod.content.bosses.yama

import org.rsmod.api.player.output.mes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The arena's hazards (Hazards.Yama.js): shadow waves, fire streaks and phase 3's Shadow Crash.
 *
 * - Shadow waves (captured): a diagonal wall sweeping the arena from the south-east or south-west,
 *   2 tiles a tick, every tile's map animation sent at once with its delay. A player on a tile as
 *   it passes takes up to 20 and loses their protection prayers (Wiki).
 * - Fire streaks (captured): rows lit at the east or west edge for 3 ticks, then crossing at 2
 *   tiles a tick. Walking through is safe; standing in one burns and binds (Wiki).
 * - Shadow Crash (phase 3, captured): three rocks in a line through the player, their shadows
 *   closing in on the middle one in a bowtie 3 ticks later, up to 20 there (Wiki).
 */
internal object YamaHazards {
    enum class Corner(val from: Int) {
        SOUTH_EAST(-8548),
        SOUTH_WEST(-11552);

        fun other() = if (this == SOUTH_EAST) SOUTH_WEST else SOUTH_EAST

        val spot: String
            get() = if (this == SOUTH_EAST) YamaIds.SPOT_WAVE_SOUTH_EAST else YamaIds.SPOT_WAVE_SOUTH_WEST
    }

    class Wave(val corner: Corner, val shift: Int, val start: Int, val last: Int) {
        var hit = false
    }

    class Streak(
        val z: Int,
        var x: Int,
        val dir: Int,
        val minX: Int,
        val maxX: Int,
        var wait: Int,
    ) {
        var tiles: List<Int> = emptyList()
    }

    const val WAVE_LEAD = 10
    const val STREAK_LEAD = 9
    private const val CYCLES_PER_STEP = 15
    private val STREAK_CYCLES = intArrayOf(15, 30)
    private const val WAVE_MAX_HIT = 20
    private const val STREAK_WARN_TICKS = 3
    private const val STREAK_DAMAGE = 10
    private const val STREAK_BIND_TICKS = 2

    // ------------------------------------------------------------------ shadow waves

    /** [shift] moves where a pair of waves crosses by that many columns (Wiki: phase 3). */
    private fun waveStep(corner: Corner, x: Int, z: Int, shift: Int = 0): Int =
        if (corner == Corner.SOUTH_EAST) z - x + corner.from + shift else x + z + corner.from - shift

    /** One wave from a corner: every tile's animation now, with its delay (as captured). */
    fun wave(fight: YamaFight, corner: Corner, shift: Int = 0) {
        var last = 0
        for ((x, z) in YamaCommon.FLOOR_TILES) {
            val step = waveStep(corner, x, z, shift)
            if (step < 0) continue
            last = max(last, step)
            YamaHits.tileGraphic(
                fight.player,
                fight.arena.tile(x, z),
                corner.spot,
                delay = step * CYCLES_PER_STEP,
            )
        }
        fight.waves += Wave(corner, shift, fight.tick, last)
    }

    private fun processWaves(fight: YamaFight) {
        val here = fight.playerSource
        val keep = ArrayList<Wave>()
        for (wave in fight.waves) {
            val elapsed = fight.tick - wave.start
            if (elapsed > wave.last / 2 + 1) continue
            val step = waveStep(wave.corner, here.x, here.z, wave.shift)
            if (!wave.hit && YamaCommon.onFloor(here.x, here.z) && step / 2 == elapsed) {
                wave.hit = true
                shadowBurn(fight)
            }
            keep += wave
        }
        fight.waves.clear()
        fight.waves.addAll(keep)
    }

    private fun shadowBurn(fight: YamaFight) {
        val low = (WAVE_MAX_HIT * 0.8).toInt().let { kotlin.math.ceil(WAVE_MAX_HIT * 0.8).toInt() }
        YamaHits.damage(fight, low + fight.deps.random.of(WAVE_MAX_HIT - low + 1))
        YamaHits.dropProtectionPrayers(fight.player)
    }

    // ------------------------------------------------------------------ fire streaks

    /** [count] streaks on distinct random rows, each from a random edge. */
    fun streaks(fight: YamaFight, count: Int) {
        val rows =
            YamaCommon.ARENA_ROWS.filter { it.ranges.size == 1 }.shuffled().take(count)
        for (row in rows) {
            val range = row.ranges.first()
            val fromWest = fight.deps.random.randomBoolean(2)
            fight.streaks +=
                Streak(
                    z = row.z(),
                    x = if (fromWest) range.first else range.last,
                    dir = if (fromWest) 1 else -1,
                    minX = range.first,
                    maxX = range.last,
                    wait = STREAK_WARN_TICKS,
                )
        }
    }

    private fun YamaCommon.ArenaRow.z() = y

    private fun processStreaks(fight: YamaFight) {
        val here = fight.playerSource
        val moved = fight.lastTile?.let { it.x != here.x || it.z != here.z } ?: true
        val keep = ArrayList<Streak>()
        for (streak in fight.streaks) {
            if (streak.wait > 0) {
                streak.wait--
                streak.tiles = listOf(streak.x)
                YamaHits.tileGraphic(
                    fight.player,
                    fight.arena.tile(streak.x, streak.z),
                    YamaIds.SPOT_STREAK,
                    delay = STREAK_CYCLES[0],
                )
            } else {
                val next =
                    listOf(streak.x + streak.dir, streak.x + streak.dir * 2).filter {
                        it in streak.minX..streak.maxX
                    }
                if (next.isEmpty()) continue
                next.forEachIndexed { i, x ->
                    YamaHits.tileGraphic(
                        fight.player,
                        fight.arena.tile(x, streak.z),
                        YamaIds.SPOT_STREAK,
                        delay = STREAK_CYCLES[i],
                    )
                }
                streak.tiles = next
                streak.x = next.last()
            }
            if (!moved && here.z == streak.z && here.x in streak.tiles) {
                YamaHits.damage(fight, STREAK_DAMAGE)
                YamaHits.root(fight, STREAK_BIND_TICKS)
            }
            keep += streak
        }
        fight.streaks.clear()
        fight.streaks.addAll(keep)
        fight.lastTile = fight.arena.tile(here.x, here.z)
    }

    // ------------------------------------------------------------------ Shadow Crash

    private val CARDINAL_AXES = listOf(0 to 1, 1 to 0)
    private val DIAGONAL_AXES = listOf(1 to 1, 1 to -1)
    private val SHADOW_DELAYS = intArrayOf(30, 23, 15, 8)

    private const val CRASH_REACH = 3
    private const val CRASH_SPACING = 3
    private const val CRASH_SHADOW_TICKS = 3
    private const val CRASH_MAX_HIT = 20

    private class Cell(val dx: Int, val dz: Int, val delay: Int)

    /** The bowtie a crash's shadows cover around its centre, with each tile's delay. */
    private fun bowtie(ux: Int, uz: Int, reach: Int): List<Cell> {
        val cells = ArrayList<Cell>()
        val diagonal = ux != 0 && uz != 0
        for (dx in -3..3) {
            for (dz in -3..3) {
                val along = if (diagonal) (dx * ux + dz * uz) / 2.0 else (dx * ux + dz * uz).toDouble()
                val across = if (diagonal) (dx * uz - dz * ux) / 2.0 else (dx * uz - dz * ux).toDouble()
                if (abs(along) > reach || abs(across) > abs(along)) continue
                val ring =
                    min(SHADOW_DELAYS.size - 1, (abs(along) * if (diagonal) 1.5 else 1.0).roundToInt())
                cells += Cell(dx, dz, SHADOW_DELAYS[ring])
            }
        }
        return cells
    }

    /** One crash on the player's tile along [diagonal] or cardinal, turned 90 degrees from [previous]. */
    private fun crash(fight: YamaFight, diagonal: Boolean, previous: Pair<Int, Int>? = null): Pair<Int, Int> {
        val centre = fight.playerSource
        val axes =
            if (diagonal) DIAGONAL_AXES else CARDINAL_AXES.filter { previous == null || it.first != previous.first }
        val (ux, uz) = axes[fight.deps.random.of(axes.size)]
        val reach = if (diagonal) 2 else CRASH_REACH
        YamaHits.tileGraphic(fight.player, fight.arena.tile(centre.x, centre.z), YamaIds.SPOT_CRASH_ROCK)
        YamaHits.tileGraphic(
            fight.player,
            fight.arena.tile(centre.x + ux * reach, centre.z + uz * reach),
            YamaIds.SPOT_CRASH_ROCK,
            delay = 3,
        )
        YamaHits.tileGraphic(
            fight.player,
            fight.arena.tile(centre.x - ux * reach, centre.z - uz * reach),
            YamaIds.SPOT_CRASH_ROCK,
            delay = 3,
        )
        val cells = bowtie(ux, uz, reach).filter { YamaCommon.onFloor(centre.x + it.dx, centre.z + it.dz) }
        fight.at(CRASH_SHADOW_TICKS) {
            for (cell in cells) {
                YamaHits.tileGraphic(
                    fight.player,
                    fight.arena.tile(centre.x + cell.dx, centre.z + cell.dz),
                    YamaIds.SPOT_CRASH_SHADOW,
                    delay = cell.delay,
                )
            }
        }
        fight.at(CRASH_SHADOW_TICKS + 1) {
            val here = fight.playerSource
            if (cells.any { centre.x + it.dx == here.x && centre.z + it.dz == here.z }) {
                YamaHits.damage(fight, fight.deps.random.of(CRASH_MAX_HIT + 1))
            }
        }
        return ux to uz
    }

    /** A Shadow Crash: cardinal, diagonal, cardinal, 3 ticks apart (Wiki, captured). */
    fun shadowCrash(fight: YamaFight) {
        val first = crash(fight, diagonal = false)
        fight.at(CRASH_SPACING) { crash(fight, diagonal = true) }
        fight.at(CRASH_SPACING * 2) { crash(fight, diagonal = false, previous = first) }
    }

    fun process(fight: YamaFight) {
        if (fight.state == YamaState.SEATED) return
        processWaves(fight)
        processStreaks(fight)
    }

    fun clear(fight: YamaFight) {
        fight.waves.clear()
        fight.streaks.clear()
    }

    @Suppress("unused") private fun msg(fight: YamaFight, text: String) = fight.player.mes(text)
}
