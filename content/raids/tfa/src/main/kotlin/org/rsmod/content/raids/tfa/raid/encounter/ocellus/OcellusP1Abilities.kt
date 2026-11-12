package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.VarExpr
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.BOSS_SIZE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.BROKEN
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.CENTRE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.CORNER_CYCLE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.FADE_IN_START
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.HEADICON_GRAPHIC
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.HEADICON_SLOT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.INVISIBLE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.NEXT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.OPAQUE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.PHASE_TWO
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.TELEPORT_SPOT_SLOT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.THRESHOLDS
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.VOLLEY_END
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.VOLLEY_END_SEQ
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.VOLLEY_START
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.WALK
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.WALKING
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.ARRIVED_AT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.CENTRE_SIDE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.CORNER
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.METER
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.THRESHOLD
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.VOLLEY_STYLE
import org.rsmod.map.CoordGrid

internal class OcellusP1Abilities {
    fun start(): Effect =
        sequence(
            oneOf(OcellusArena.CORNERS.indices.map { setVarn(CORNER, it) }),
            oneOf(VolleyStyle.entries.map { setVarn(VOLLEY_STYLE, it.ordinal) }),
            run(WALK),
        )

    fun walk(): Effect {
        val arrive = sequence(setVarn(ARRIVED_AT, VarExpr.Now + 1), faceTile(OcellusArena.MIDDLE))
        val routes =
            OcellusArena.CORNERS.map { (dx, dz) -> walkTo(spawnTile(dx, dz), onArrival = arrive) }
        return sequence(
            setVarn(ARRIVED_AT, NOT_ARRIVED),
            transitionTo(WALKING),
            protectMelee(),
            run(VOLLEY_START),
            switch(CORNER, routes),
        )
    }

    /**
     * Each 5-tick slot of a walk: a volley while walking (closing the cast if it arrived this
     * tick), and on the first slot it is already standing at the corner, close the cast if the
     * arrival slot didn't and start charging the tick after.
     */
    fun volleySlot(): Effect {
        val settle =
            sequence(
                whenever(varnAtLeast(ARRIVED_AT, CAST_OPEN), anim(VOLLEY_END_SEQ)),
                transitionTo(CORNER_CYCLE),
                dropProtection(),
                wait(1),
                run(CORNER_CYCLE),
            )
        return whenever(
            varnIs(ARRIVED_AT, NOT_ARRIVED),
            run(VOLLEY_START),
            otherwise =
                whenever(
                    Condition.Not(varnExpired(ARRIVED_AT)),
                    sequence(run(VOLLEY_END), setVarn(ARRIVED_AT, CAST_CLOSED)),
                    otherwise = settle,
                ),
        )
    }

    fun volley(seq: String): Effect {
        val swaps = VolleyStyle.entries.map { setVarn(VOLLEY_STYLE, it.other.ordinal) }
        val swapStyle = switch(VOLLEY_STYLE, swaps)
        return sequence(
            chance(STYLE_SWITCH_ONE_IN, swapStyle),
            anim(seq),
            switch(VOLLEY_STYLE, VolleyStyle.entries.map(::volleyOf)),
        )
    }

    private fun volleyOf(style: VolleyStyle): Effect =
        sequence(
            spotanim(style.cast),
            onEach(
                playersIn(OcellusArena.AREA),
                sequence(
                    soundTo(style.castSound),
                    soundTo(style.travelSound, delay = VOLLEY_TRAVEL_SOUND_DELAY),
                    whenever(
                        targetWithin(VOLLEY_CLOSE_RANGE),
                        volleyProjectile(style, VOLLEY_CLOSE_TRAVEL),
                        otherwise = volleyProjectile(style, VOLLEY_FAR_TRAVEL),
                    ),
                ),
            ),
        )

    private fun volleyProjectile(style: VolleyStyle, travel: Int): Effect =
        projectile(
            spotanim = style.projanim,
            config =
                ProjectileConfig.fixed(
                    startHeight = VOLLEY_START_HEIGHT,
                    endHeight = VOLLEY_END_HEIGHT,
                    delay = VOLLEY_DELAY,
                    travel = travel,
                    angle = VOLLEY_ANGLE,
                ),
            hit =
                hit {
                    damage(0..VOLLEY_MAX_HIT).roll()
                    type(style.hitType)
                    spotanim(style.impact)
                },
            resolveOnImpact = true,
            onImpact = soundTo(style.impactSound),
        )

    fun cornerCycle(): Effect =
        sequence(
            repeat(
                CORNER_CHARGES,
                gap = CHARGE_GAP,
                effect = sequence(anim(CHARGE_SEQ), external(OcellusTendrils.CORNER)),
            ),
            wait(CORNER_EXPLODE - (CORNER_CHARGES - 1) * CHARGE_GAP),
            explode(),
            wait(EXPLODE_TO_MOVE),
            switch(CORNER, OcellusArena.CORNERS.indices.map { setVarn(CORNER, nextCorner(it)) }),
            run(NEXT),
        )

    fun centre(): Effect {
        val round =
            sequence(
                faceSide(),
                anim(CHARGE_SEQ),
                external(OcellusTendrils.CENTRE),
                wait(CENTRE_CHARGE_TO_EXPLODE),
                explode(),
                addVarn(CENTRE_SIDE, 1),
            )
        return sequence(
            transitionTo(CENTRE),
            dropProtection(),
            teleportToCentre(),
            oneOf(OcellusTendril.SIDES.indices.map { setVarn(CENTRE_SIDE, it) }),
            faceSide(instant = true),
            wait(CENTRE_FIRST_CHARGE),
            repeat(CENTRE_ROUNDS, gap = CENTRE_ROUND_GAP - CENTRE_CHARGE_TO_EXPLODE, round),
            wait(EXPLODE_TO_RELEASE),
            whenever(
                varnAtLeast(METER, OcellusMeter.SINGLE_WAVE_MAX + 1),
                say("Embrace... annihilation..."),
                otherwise = say("Death... awaits..."),
            ),
            external(OcellusBarrage.RELEASE),
            wait(RELEASE_TO_EXPEND),
            anim(EXPEND_SEQ),
            external(OcellusBarrage.FIRE),
            wait(EXPEND_TO_MOVE),
            run(NEXT),
        )
    }

    fun next(): Effect {
        val (first, second) = THRESHOLDS
        return whenever(
            varnIs(THRESHOLD, 0) and Condition.HpBelow(first, inclusive = true),
            enterCentre(1),
            otherwise =
                whenever(
                    varnIs(THRESHOLD, 1) and Condition.HpBelow(second, inclusive = true),
                    enterCentre(2),
                    otherwise = run(WALK),
                ),
        )
    }

    fun enterCentre(stage: Int): Effect = sequence(setVarn(THRESHOLD, stage), run(CENTRE))

    fun shieldBreak(): Effect {
        val atCentre = Condition.Custom { npc, _ -> npc.coords == OcellusArena.centre(npc) }
        return sequence(
            interrupt(),
            transitionTo(BROKEN),
            dropProtection(),
            whenever(atCentre, Effect.NoOp, otherwise = teleportToCentre()),
            anim("seq.npc_ocellus_phase1_transformation"),
            wait(TRANSFORM_TO_P2),
            spotanim("spotanim.vfx_ocellus_p1_to_p2_transformation", slot = TELEPORT_SPOT_SLOT),
            anim("seq.npc_ocellus_phase2_transformation"),
            transmog("npc.tfa_ocellus_p2", Int.MAX_VALUE),
            external(OcellusPhaseTwo.ENTER),
            transitionTo(PHASE_TWO),
            run(PHASE_TWO),
        )
    }

    private fun protectMelee(): Effect =
        sequence(
            headIcon(HEADICON_SLOT, HEADICON_GRAPHIC, HEADICON_MELEE),
            setVarn(IMMUNE_MELEE, 1),
        )

    fun dropProtection(): Effect =
        sequence(clearHeadIcon(HEADICON_SLOT), setVarn(IMMUNE_MELEE, 0))

    private fun teleportToCentre(): Effect =
        sequence(
            anim("seq.npc_ocellus_phase1_teleport_out"),
            spotanim("spotanim.vfx_ocellus_p1_teleport_out", slot = TELEPORT_SPOT_SLOT),
            transparency(FADE_OUT_START, FADE_OUT_END, from = OPAQUE, to = INVISIBLE),
            wait(TELEPORT_TICKS),
            teleport(OcellusArena.CENTRE),
            anim("seq.npc_ocellus_phase1_teleport_in"),
            spotanim("spotanim.vfx_ocellus_p1_teleport_in", slot = TELEPORT_SPOT_SLOT),
            transparency(FADE_IN_START, FADE_IN_END, from = INVISIBLE, to = OPAQUE),
        )

    private fun faceSide(instant: Boolean = false): Effect {
        val sides = OcellusTendril.SIDES
        val faces =
            List(sides.size * 2) {
                val (nx, nz) = sides[it % sides.size]
                val tile = offset(OcellusArena.MIDDLE, nx * FACE_DISTANCE, nz * FACE_DISTANCE)
                faceTile(tile, instant)
            }
        return switch(CENTRE_SIDE, faces)
    }

    private fun explode(): Effect =
        sequence(
            anim("seq.npc_ocellus_phase1_explode"),
            spotanim("spotanim.vfx_ocellus_p1_explode_npc"),
            after(
                SLAM_LAND,
                whenever(
                    Condition.Not(Condition.InPhase(BROKEN)),
                    sequence(slamRing(1, delay = 0), slamRing(2, SLAM_OUTER_DELAY)),
                ),
            ),
        )

    private fun slamRing(distance: Int, delay: Int): Effect {
        val tiles = customTiles(OcellusArena.AREA) { npc, _, _ -> ring(npc.coords, distance) }
        val floor = SLAM_FLOOR.map { mapSpotanim(it, CurrentTile, delay = delay) }
        val hit =
            hit {
                damage(SLAM_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        return onTiles(tiles, sequence(oneOf(floor), onEach(playersOn(CurrentTile), hit)))
    }

    private fun ring(sw: CoordGrid, distance: Int): List<CoordGrid> {
        val min = -distance
        val max = BOSS_SIZE - 1 + distance
        return buildList {
            for (dx in min..max) {
                for (dz in min..max) {
                    if (dx == min || dx == max || dz == min || dz == max) add(sw.translate(dx, dz))
                }
            }
        }
    }

    private fun nextCorner(corner: Int): Int = (corner + 1) % OcellusArena.CORNERS.size

    private enum class VolleyStyle(
        val hitType: HitType,
        val cast: String,
        val projanim: String,
        val impact: String,
        val castSound: String,
        val travelSound: String,
        val impactSound: String,
    ) {
        Magic(
            HitType.Magic,
            "spotanim.vfx_ocellus_water_attack01_spotanim",
            "spotanim.vfx_ocellus_water_attack01_projanim",
            "spotanim.vfx_ocellus_water_attack01_impactanim",
            "synth.ocellus_water_attack_cast",
            "synth.ocellus_water_attack_travel",
            "synth.mark_of_darkness_hit",
        ),
        Ranged(
            HitType.Ranged,
            "spotanim.vfx_ocellus_fire_attack01_spotanim",
            "spotanim.vfx_ocellus_fire_attack01_projanim",
            "spotanim.vfx_ocellus_fire_attack01_impactanim",
            "synth.ocellus_fire_attack_cast",
            "synth.ocellus_fire_attack_travel",
            "synth.leather_hit",
        );

        val other: VolleyStyle
            get() = if (this == Magic) Ranged else Magic
    }

    private companion object {
        const val CHARGE_SEQ = "seq.npc_ocellus_phase1_charge"
        const val EXPEND_SEQ = "seq.npc_ocellus_phase1_expend"
        const val IMMUNE_MELEE = "varn.immune_melee"
        const val HEADICON_MELEE = 0
        const val NOT_ARRIVED = 0
        const val CAST_CLOSED = 1
        const val CAST_OPEN = 2
        const val TELEPORT_TICKS = 4
        const val TRANSFORM_TO_P2 = 7
        const val FADE_OUT_START = 30
        const val FADE_OUT_END = 45
        const val FADE_IN_END = 20
        const val FACE_DISTANCE = 10
        const val VOLLEY_CLOSE_RANGE = 4
        const val VOLLEY_MAX_HIT = 30
        const val STYLE_SWITCH_ONE_IN = 4
        const val VOLLEY_TRAVEL_SOUND_DELAY = 30
        const val VOLLEY_START_HEIGHT = 578
        const val VOLLEY_END_HEIGHT = 124
        const val VOLLEY_DELAY = 30
        const val VOLLEY_ANGLE = 40
        const val VOLLEY_CLOSE_TRAVEL = 65
        const val VOLLEY_FAR_TRAVEL = 95
        const val CORNER_CHARGES = 7
        const val CHARGE_GAP = 2
        const val CORNER_EXPLODE = 18
        const val EXPLODE_TO_MOVE = 3
        const val CENTRE_FIRST_CHARGE = 4
        const val CENTRE_ROUNDS = 4
        const val CENTRE_ROUND_GAP = 8
        const val CENTRE_CHARGE_TO_EXPLODE = 6
        const val EXPLODE_TO_RELEASE = 2
        const val RELEASE_TO_EXPEND = 2
        const val EXPEND_TO_MOVE = 7
        const val SLAM_LAND = 1
        const val SLAM_OUTER_DELAY = 2
        val SLAM_DAMAGE = 31..35
        val SLAM_FLOOR =
            listOf(
                "spotanim.vfx_ocellus_p1_explode_floor",
                "spotanim.vfx_ocellus_p1_explode_floor_90",
                "spotanim.vfx_ocellus_p1_explode_floor_180",
                "spotanim.vfx_ocellus_p1_explode_floor_270",
            )
    }
}
