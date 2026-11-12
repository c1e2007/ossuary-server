package org.rsmod.content.bosses.yama

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid

/**
 * The glyphs of phases 1 and 2 (Glyphs.Yama.js): five 3x3 floor glyphs light up as a phase starts,
 * the three by the throne and to the south in the element of the phase's first special, the two at
 * the sides in the other. A player on a lit glyph's centre absorbs it a tick later, the glyph goes
 * out, and 10 ticks of immunity to its element follow. Fire burns for 2-12 and shadow drains 2-12
 * prayer, more the closer Yama stands to the glyph. The glyphs go out at the phase change and
 * stay out in phase 3.
 */
internal object YamaGlyphs {
    class Glyph(val x: Int, val z: Int, val element: String, var lit: Boolean = true) {
        var standing = 0
    }

    private val MAJOR = listOf(1497 to 10084, 1507 to 10084, 1502 to 10072)
    private val MINOR = listOf(1495 to 10077, 1509 to 10077)
    private val ROTATION = LocAngle.East

    private const val IMMUNITY_TICKS = 10
    private const val ABSORB_SLOT = 2
    private const val FAR_VALUE = 2
    private val BY_YAMA_DISTANCE = listOf(1 to 12, 2 to 9, 3 to 6)

    private fun indexOf(element: String) = if (element == "fire") 0 else 1

    private fun other(element: String) = if (element == "fire") "shadow" else "fire"

    private fun litId(element: String) =
        if (element == "fire") YamaIds.GLYPH_FIRE else YamaIds.GLYPH_SHADOW

    private fun outId(element: String) =
        if (element == "fire") YamaIds.GLYPH_FIRE_OUT else YamaIds.GLYPH_SHADOW_OUT

    private fun tileOf(fight: YamaFight, glyph: Glyph): CoordGrid = fight.arena.tile(glyph.x, glyph.z)

    /** Lights the phase's glyphs: [major] is the element of the three by the throne. */
    fun light(fight: YamaFight, major: String) {
        clear(fight)
        for ((x, z) in MAJOR) fight.glyphs += Glyph(x, z, major)
        for ((x, z) in MINOR) fight.glyphs += Glyph(x, z, other(major))
        for (glyph in fight.glyphs) fight.setLoc(tileOf(fight, glyph), litId(glyph.element), ROTATION)
    }

    /** A glyph goes out: its flash, then the inactive glyph a tick later (as captured). */
    private fun putOut(fight: YamaFight, glyph: Glyph) {
        glyph.lit = false
        val tile = tileOf(fight, glyph)
        fight.setLoc(tile, outId(glyph.element), ROTATION)
        fight.at(1) { fight.setLoc(tile, YamaIds.GLYPH_INACTIVE, ROTATION) }
    }

    /** Every glyph still lit goes out (the phase change). */
    fun putAllOut(fight: YamaFight) {
        for (glyph in fight.glyphs) if (glyph.lit) putOut(fight, glyph)
    }

    /** Every glyph left as it is out of a fight: gone, at once. */
    fun clear(fight: YamaFight) {
        for (glyph in fight.glyphs) {
            glyph.lit = false
            fight.setLoc(tileOf(fight, glyph), YamaIds.GLYPH_INACTIVE, ROTATION)
        }
        fight.glyphs.clear()
    }

    /** The glyph's value by how close Yama is to it: 12 adjacent, then 9, 6, and 2 from four tiles. */
    private fun strength(fight: YamaFight, glyph: Glyph): Int {
        val yama = fight.yama ?: return FAR_VALUE
        val source = fight.arena.source(yama.coords)
        val distance =
            maxOf(
                YamaCommon.gap(glyph.x, YamaCommon.GLYPH_SIZE, source.x, 3),
                YamaCommon.gap(glyph.z, YamaCommon.GLYPH_SIZE, source.z, 3),
            )
                .coerceAtLeast(1)
        return BY_YAMA_DISTANCE.firstOrNull { distance <= it.first }?.second ?: FAR_VALUE
    }

    /** Each tick: a player on a lit glyph's centre absorbs it a tick later. */
    fun process(fight: YamaFight) {
        val here = fight.playerSource
        for (glyph in fight.glyphs) {
            val onCentre = glyph.lit && here.x == glyph.x + 1 && here.z == glyph.z + 1
            if (!onCentre) {
                glyph.standing = 0
                continue
            }
            glyph.standing++
            if (glyph.standing >= 2) absorb(fight, glyph)
        }
        for (element in listOf("fire", "shadow")) {
            val index = indexOf(element)
            if (fight.immunity[index] != 0 && fight.tick >= fight.immunity[index]) {
                fight.immunity[index] = 0
                fight.player.mes("<col=ff3045>The Glyph of ${element.replaceFirstChar { it.uppercase() }} fades away.")
            }
        }
    }

    private fun absorb(fight: YamaFight, glyph: Glyph) {
        val player = fight.player
        val value = strength(fight, glyph)
        putOut(fight, glyph)
        fight.immunity[indexOf(glyph.element)] = fight.tick + IMMUNITY_TICKS
        val fire = glyph.element == "fire"
        player.mes(
            if (fire) "<col=ff8e32>You absorb a Glyph of Fire."
            else "<col=a53fff>You absorb a Glyph of Shadow."
        )
        player.spotanimSpecific(
            if (fire) YamaIds.SPOT_IMMUNITY_FIRE else YamaIds.SPOT_IMMUNITY_SHADOW,
            slot = ABSORB_SLOT,
        )
        if (fire) YamaHits.damage(fight, value) else YamaHits.drainPrayer(player, value)
    }

    /** Whether a glyph keeps [element] off the player now. */
    fun immune(fight: YamaFight, element: String): Boolean =
        fight.immunity[indexOf(element)] > fight.tick

    /** The special's damage soaked up by a glyph: the message and a 0 (as captured). */
    fun blocked(fight: YamaFight, element: String) {
        fight.player.mes(
            if (element == "fire") "<col=ff8e32>The Glyph of Fire absorbs the damage from the attack."
            else "<col=a53fff>The Glyph of Shadow absorbs the damage from the attack."
        )
        YamaHits.damage(fight, 0)
    }
}
