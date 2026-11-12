package org.rsmod.content.bosses.yama

import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

/**
 * Yama's attacks in phases 1 and 2 (Attacks.Yama.js): the rotation, the standard attacks and the
 * specials. Max hits, cadences and telegraphs are the reference's; nothing is rounded.
 *
 * - The rotation: one standard attack in the phase's opening element, then a special of the other
 *   element, after which his standard attacks take that element; then three standard attacks and
 *   a special, each special the other element from the last. Void flares stand in for one shadow
 *   special a phase and change nothing.
 * - A standard attack is a swing if the player is next to him, else magic (fire) or ranged
 *   (shadow): 46 at most; Protect from Melee lets 6 through, the others block it all.
 */
internal object YamaAttacks {
    private const val MAX_HIT = 46

    internal enum class Style(val type: HitType) {
        FIRE(HitType.Magic),
        SHADOW(HitType.Ranged),
    }

    private fun other(element: String) = if (element == "fire") "shadow" else "fire"

    private fun styleOf(element: String) = if (element == "fire") Style.FIRE else Style.SHADOW

    /** A phase's rotation: the opening element, and which shadow special void flares replace. */
    fun startRotation(fight: YamaFight): String {
        val opening = if (fight.deps.random.randomBoolean(2)) "fire" else "shadow"
        val firstSpecial = other(opening)
        // The shadow specials' places: shadow first -> specials 1, 3, 5...; fire first -> 2, 4...
        val shadowSlots = if (firstSpecial == "shadow") listOf(1, 3, 5) else listOf(2, 4)
        val choices =
            if (firstSpecial == "shadow") listOf(shadowSlots[1], shadowSlots[2])
            else listOf(shadowSlots[0], shadowSlots[1])
        fight.rotation =
            YamaFight.Rotation(
                opening = opening,
                element = opening,
                flareSlot = choices[fight.deps.random.of(2)],
            )
        return firstSpecial
    }

    /** Yama's turn in phases 1 and 2. */
    fun attack(fight: YamaFight) {
        val rotation = fight.rotation ?: return
        val dueSpecial =
            if (rotation.specials == 0) rotation.sinceSpecial >= 1 else rotation.sinceSpecial >= 3
        if (dueSpecial) {
            special(fight)
            return
        }
        rotation.sinceSpecial++
        standard(fight, rotation.element)
    }

    private fun special(fight: YamaFight) {
        val rotation = fight.rotation ?: return
        rotation.specials++
        rotation.sinceSpecial = 0
        val element = other(rotation.lastSpecial ?: rotation.opening)
        if (element == "shadow" && rotation.specials == rotation.flareSlot) {
            summonFlares(fight)
            return
        }
        rotation.lastSpecial = element
        rotation.element = element
        if (fight.phase == 1) {
            if (element == "fire") rockfall(fight) else spike(fight)
        } else if (element == "fire") {
            meteor(fight)
        } else {
            stomp(fight)
        }
    }

    // ------------------------------------------------------------------ standard attacks

    /** Whether the player stands next to Yama (where he swings instead). */
    fun adjacent(fight: YamaFight): Boolean {
        val yama = fight.yama ?: return false
        val at = yama.coords
        val p = fight.player.coords
        val size = 3
        val dx = if (p.x < at.x) at.x - p.x else maxOf(0, p.x - (at.x + size - 1))
        val dz = if (p.z < at.z) at.z - p.z else maxOf(0, p.z - (at.z + size - 1))
        return dx + dz == 1 && p.level == at.level
    }

    private fun standard(fight: YamaFight, element: String) {
        if (adjacent(fight)) {
            swing(fight)
        } else {
            cast(fight, styleOf(element), element)
        }
    }

    /** A cast of [element]: the animation, the projectile-less spotanims and the hit 3 ticks on. */
    fun cast(fight: YamaFight, style: Style, element: String) {
        val yama = fight.yama ?: return
        val player = fight.player
        val magic = style == Style.FIRE
        yama.anim(YamaIds.SEQ_MAGIC, delay = 30)
        yama.spotanim(if (magic) YamaIds.SPOT_MAGIC else YamaIds.SPOT_RANGED, delay = 30)
        player.spotanimSpecific(
            if (magic) YamaIds.SPOT_MAGIC_IMPACT else YamaIds.SPOT_RANGED_IMPACT,
            slot = 2,
        )
        if (YamaGlyphs.immune(fight, element)) {
            fight.at(3) { YamaHits.damage(fight, 0) }
            return
        }
        val through = if (fight.phase == 3) 3 else 0
        val damage = YamaHits.strike(fight, yama, style.type, MAX_HIT, 3, through)
        maybePoison(fight, damage, 3)
    }

    fun castOf(fight: YamaFight, element: String) = cast(fight, styleOf(element), element)

    /** The axe swing on the target. */
    fun swing(fight: YamaFight): Int {
        val yama = fight.yama ?: return 0
        yama.anim(YamaIds.SEQ_MELEE)
        val through = if (fight.phase == 3) 22 else 6
        val damage = YamaHits.strike(fight, yama, HitType.Melee, MAX_HIT, 1, through)
        maybePoison(fight, damage, 1)
        return damage
    }

    private fun maybePoison(fight: YamaFight, damage: Int, delay: Int) {
        if (!fight.deps.random.randomBoolean(4)) return
        fight.at(delay + 1) { if (damage > 0) YamaHits.poison(fight, POISON_DAMAGE) }
    }

    // ------------------------------------------------------------------ phase 1 specials

    /** The flaming rock (fire) and the shadow spike: a rock from above landing 6 ticks later. */
    private fun fallingSpecial(
        fight: YamaFight,
        castSpot: String,
        projSpot: String,
        impactSpot: String,
        impactDelay: Int,
        message: String,
        element: String,
        onHit: (Int) -> Unit,
    ) {
        val yama = fight.yama ?: return
        val player = fight.player
        yama.anim(YamaIds.SEQ_SPECIAL)
        yama.spotanim(castSpot)
        player.mes(message)
        fight.deps.bossProjectile(
            spotanim = projSpot.asRSCM(RSCMType.SPOTANIM),
            src = yama.coords,
            target = player.coords,
            startHeight = 2000,
            endHeight = 0,
            delay = 65,
            travel = 115,
            curve = 60,
            progress = 100,
            homing = null,
        )
        if (impactDelay > 0) player.spotanimSpecific(impactSpot, delay = impactDelay)
        fight.at(6) {
            if (impactDelay == 0) player.spotanimSpecific(impactSpot)
            if (YamaGlyphs.immune(fight, element)) {
                YamaGlyphs.blocked(fight, element)
                return@at
            }
            val value = fight.deps.random.of(MAX_HIT + 1)
            YamaHits.damage(fight, value)
            onHit(value)
        }
    }

    private fun rockfall(fight: YamaFight) {
        say(fight, ROCKFALL_LINES)
        fallingSpecial(
            fight,
            YamaIds.SPOT_ROCK_CAST,
            YamaIds.SPOT_ROCK_PROJ,
            YamaIds.SPOT_ROCK_IMPACT,
            180,
            "<col=ff8e32>Yama conjures a fire-based attack at you.",
            "fire",
        ) { value ->
            if (value > 0) fight.at(1) { YamaHits.burn(fight, 10) }
        }
    }

    private fun spike(fight: YamaFight) {
        say(fight, STOMP_LINES)
        fallingSpecial(
            fight,
            YamaIds.SPOT_SPIKE_CAST,
            YamaIds.SPOT_SPIKE_PROJ,
            YamaIds.SPOT_SPIKE_IMPACT,
            0,
            "<col=a53fff>Yama launches a shadow-based attack at you.",
            "shadow",
        ) { value -> YamaHits.drainPrayer(fight.player, (value * 0.33).toInt()) }
    }

    // ------------------------------------------------------------------ phase 2 specials

    /** Meteor Strike: a blast on the player's tile 6 ticks on; within a tile of its centre is death. */
    private fun meteor(fight: YamaFight) {
        val yama = fight.yama ?: return
        val player = fight.player
        yama.anim(YamaIds.SEQ_STOMP)
        yama.spotanim(YamaIds.SPOT_METEOR_CAST)
        say(fight, ROCKFALL_LINES)
        val centre = fight.playerSource
        fight.at(2) {
            player.mes("<col=ff8e32>Yama conjures a fire-based attack at you.")
            val half = (METEOR_SIZE - 1) shr 1
            val marker =
                fight.spawn(
                    YamaIds.METEOR,
                    fight.arena.tile(centre.x - half, centre.z - half),
                )
            if (marker != null) {
                marker.anim(YamaIds.SEQ_METEOR_MARKER)
                marker.spotanim(YamaIds.SPOT_METEOR_MARKER)
                fight.at(METEOR_HIT_TICKS - 2 + 1) { fight.remove(marker) }
            }
        }
        fight.at(METEOR_HIT_TICKS) {
            val here = fight.playerSource
            val distance = maxOf(kotlin.math.abs(here.x - centre.x), kotlin.math.abs(here.z - centre.z))
            if (YamaGlyphs.immune(fight, "fire")) {
                YamaGlyphs.blocked(fight, "fire")
                return@at
            }
            val value =
                when {
                    distance <= 1 -> player.hitpoints
                    distance <= 2 -> 80
                    distance <= 3 -> 40
                    else -> return@at
                }
            YamaHits.damage(fight, value)
            fight.at(1) { YamaHits.burn(fight, 8) }
        }
    }

    /** Shadow Stomp: a shadow along the ground, up to 80, roots for 2 ticks and drains prayer. */
    private fun stomp(fight: YamaFight) {
        val yama = fight.yama ?: return
        val player = fight.player
        yama.anim(YamaIds.SEQ_STOMP)
        yama.spotanim(YamaIds.SPOT_STOMP_CAST)
        say(fight, STOMP_LINES)
        fight.at(2) {
            player.mes("<col=a53fff>Yama conjures a shadow-based attack at you.")
            fight.deps.bossProjectile(
                spotanim = YamaIds.SPOT_STOMP_PROJ.asRSCM(RSCMType.SPOTANIM),
                src = yama.coords,
                target = player.coords,
                startHeight = 10,
                endHeight = 10,
                delay = 0,
                travel = 90,
                curve = 127,
                progress = 150,
                homing = null,
            )
        }
        fight.at(5) {
            player.spotanimSpecific(YamaIds.SPOT_STOMP_IMPACT)
            if (YamaGlyphs.immune(fight, "shadow")) {
                YamaGlyphs.blocked(fight, "shadow")
                return@at
            }
            val value = fight.deps.random.of(81)
            YamaHits.damage(fight, value)
            YamaHits.root(fight, 2)
            YamaHits.drainPrayer(player, (value * 0.33).toInt())
        }
    }

    // ------------------------------------------------------------------ void flares

    private fun summonFlares(fight: YamaFight) {
        val yama = fight.yama ?: return
        yama.anim(YamaIds.SEQ_MAGIC)
        fight.at(2) { YamaFlares.spawn(fight, 2) }
    }

    /** One of Yama's lines for the moment (Wiki transcript). */
    private fun say(fight: YamaFight, lines: List<String>) {
        fight.yama?.say(lines[fight.deps.random.of(lines.size)])
    }

    @Suppress("unused") private fun unusedCoord(c: CoordGrid) = c

    private const val POISON_DAMAGE = 20
    private const val METEOR_SIZE = 7
    private const val METEOR_HIT_TICKS = 6

    private val ROCKFALL_LINES = listOf("Colabi, infernus", "Ven, estella infernus", "Colabesur infernus")
    private val STOMP_LINES = listOf("Ven, umbra eclipta", "Umbra, proriumpse", "Umbra apprerendehe")
    val TRANSITION_LINES = listOf("Begone", "You bore me.", "Enough.")
}
