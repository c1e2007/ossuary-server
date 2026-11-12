package org.rsmod.content.bosses.yama

/**
 * Phase 3 (PhaseThree.Yama.js; Wiki: Yama/Strategies, the beat pattern from the scim.gg Yama
 * simulator, an estimate): Yama stays in the centre and attacks every 7 ticks, alternating ranged
 * and magic from the element of the glyphs that were more abundant in phase 2, or swinging at an
 * adjacent player (and summoning a flare with each swing). His beats come every 7 ticks, 10 a
 * cycle: Shadow Crash on beats 0, 3 and 7 when the player is out of melee reach, two flares on
 * beat 8. A pair of shadow waves follows the last crash, and two pairs the flares (captured); the
 * 2nd and 3rd cross a column east or west at random (Wiki). Protection prayers let 22 of a swing
 * and 3 of a cast through.
 */
internal object YamaPhaseThree {
    class State(var element: String, var beat: Int = 0)

    private const val BEATS_PER_CYCLE = 10
    private val CRASH_BEATS = listOf(0, 3, 7)
    private val FLARE_BEATS = listOf(8)
    private const val WAVE_AFTER_CRASH_TICKS = 9
    private val WAVES_AFTER_FLARES_TICKS = listOf(9, 16)

    fun start(fight: YamaFight, opening: String) {
        fight.phaseThree = State(opening)
    }

    /** Yama's turn in phase 3: a standard attack, and the beat's special. */
    fun beat(fight: YamaFight) {
        val state = fight.phaseThree ?: return
        val position = state.beat % BEATS_PER_CYCLE
        state.beat++
        if (YamaAttacks.adjacent(fight)) {
            YamaAttacks.swing(fight)
            YamaFlares.spawn(fight, 1)
        } else {
            YamaAttacks.castOf(fight, state.element)
            state.element = if (state.element == "fire") "shadow" else "fire"
            if (position in CRASH_BEATS) {
                YamaHazards.shadowCrash(fight)
                if (position == CRASH_BEATS.last()) {
                    fight.at(WAVE_AFTER_CRASH_TICKS) { waves(fight, 0) }
                }
            }
        }
        if (position in FLARE_BEATS) {
            YamaFlares.spawn(fight, 2)
            for (ticks in WAVES_AFTER_FLARES_TICKS) {
                fight.at(ticks) { waves(fight, fight.deps.random.of(3) - 1) }
            }
        }
    }

    /** A pair of waves, one from each corner, crossing [shift] columns east of the usual one. */
    private fun waves(fight: YamaFight, shift: Int) {
        if (fight.state != YamaState.FIGHTING || fight.phase != 3) return
        YamaHazards.wave(fight, YamaHazards.Corner.SOUTH_EAST, shift)
        YamaHazards.wave(fight, YamaHazards.Corner.SOUTH_WEST, shift)
    }
}
