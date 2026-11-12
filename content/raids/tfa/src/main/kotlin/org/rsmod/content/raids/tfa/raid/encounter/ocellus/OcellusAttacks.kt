package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.BOSS_SIZE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.SPREAD_IMPACT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.UNTETHER_IMPACT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.UNTETHER_PERSONAL
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class OcellusAttacks(private val deps: BossDeps, private val spin: OcellusSpin) {
    /**
     * Two slots, each a melee swing when the target is in reach and the magic spread when not,
     * then the shadow slash. A swing leaves 3 ticks before the next slot and a spread 6; the
     * slash is aimed a tick before it starts, so the last slot's wait is a tick short.
     */
    fun attackCycle(): Effect {
        val swing =
            sequence(
                anim(MELEE_BASIC_SEQ),
                hit {
                    damage(Accuracy(npcMaxHit()))
                    type(Melee)
                },
            )
        fun slot(aimLead: Int) =
            whenever(
                WithinMeleeRange,
                sequence(swing, wait(MELEE_SWING_GAP - aimLead)),
                otherwise = sequence(magicSpread(), wait(SPREAD_GAP - aimLead)),
            )
        return sequence(
            slot(0),
            slot(1),
            shadowSlash(),
            nextAttackIn(SLASH_RECOVERY),
            addVarn(OcellusVarns.SPECIAL_CYCLES, 1),
        )
    }

    fun special(quadrant: Int): Effect {
        val steps =
            mapOf(
                DEBRIS to debris(quadrant),
                INSOLENCE to insolence(),
                UNTETHER to sequence(untether(), wait(UNTETHER_RIFT_DELAY)),
            )
        val script = SCRIPTS[quadrant]
        return sequence(
            setVarn(OcellusVarns.SPECIAL_CYCLES, 0),
            choose(
                rotation { for (step in script) +then(step) },
                steps.mapValues { (_, effect) ->
                    sequence(
                        setVarn(OcellusVarns.RIFT_BUSY, 1),
                        effect,
                        setVarn(OcellusVarns.RIFT_BUSY, 0),
                    )
                },
            ),
        )
    }

    /**
     * The spin attack. The boss opens a purple portal (no overhead prayer; it takes everything), then spins once
     * plus once more for each quadrant already taken ([loops]). Each spin is a fade (protection
     * prayers must be down), the spin animation (Protect from Melee must be back up) and, a tick
     * later, a hit to everyone; a player who missed either step takes it unprotected
     * ([OcellusSpin]). Blades also mark every player's tile and 5 ticks later void takes the
     * marked tiles, whatever the spin count.
     */
    private fun debris(loops: Int): Effect {
        val marks = if (loops == 0) DEBRIS_MARKS_SHORT else DEBRIS_MARKS_LOOPED
        val out = DEBRIS_OUT + DEBRIS_LOOP * loops
        val prayed =
            hit {
                damage(DEBRIS_DAMAGE).roll()
                type(Melee)
            }
        val unprayed =
            hit {
                damage(DEBRIS_DAMAGE).roll()
                type(HitType.Typeless)
            }
        val blades =
            onEach(
                playersIn(OcellusArena.FLOOR),
                whenever(
                    Condition.Custom { _, player -> player != null && spin.isProtected(player) },
                    prayed,
                    otherwise = unprayed,
                ),
            )
        fun spin(index: Int): Effect {
            val last = index == loops
            return sequence(
                spotanim(if (last) PRAYER_OUT_SPOT else PRAYER_LOOP_SPOT, slot = PRAYER_SLOT),
                external(OcellusSpin.CHECK_OFF),
                wait(DEBRIS_OUT - DEBRIS_LOOP_SPOT),
                anim(if (last) PRAYER_OUT_SEQ else PRAYER_LOOP_SEQ),
                external(OcellusSpin.CHECK_ON),
                wait(SPIN_HIT_DELAY),
                blades,
            )
        }
        val spins = sequence(wait(DEBRIS_LOOP_SPOT), *Array(loops + 1, ::spin))
        val tiles =
            customTiles(OcellusArena.FLOOR) { npc, _, _ ->
                OcellusArena.quadrantPlayers(npc, deps.playerList).map { it.coords }
            }
        val falling =
            sequence(
                wait(marks),
                withTiles(
                    DEBRIS_TILES,
                    tiles,
                    parallel(
                        onTiles(
                            bound(DEBRIS_TILES),
                            sequence(
                                mapSpotanim(DEBRIS_SPOT, CurrentTile),
                                sound(DEBRIS_SOUND, radius = DEBRIS_SOUND_RADIUS, at = CurrentTile),
                            ),
                        ),
                        sequence(
                            wait(DEBRIS_FALL),
                            onTiles(bound(DEBRIS_TILES), external(OcellusVoid.DEBRIS, at = CurrentTile)),
                            after(DEBRIS_VINE_DELAY, external(OcellusVine.SPAWN)),
                        ),
                    ),
                ),
            )
        return sequence(
            lockMovement(),
            parallel(
                anim(PRAYER_IN_SEQ),
                spotanim(PRAYER_IN_SPOT, slot = PRAYER_IN_SLOT),
                spins,
                falling,
                sequence(
                    wait(out + DEBRIS_RECOVERY),
                    say("Your panic... is futile..."),
                    nextAttackIn(0),
                    unlockMovement(),
                ),
            ),
        )
    }

    /**
     * The boss glides home and hangs in the air while the void builds on the quadrant's floor in
     * four waves, each going off as the next opens like phase 2's pools. Then the whole quadrant
     * erupts except the boss's footprint, and a tick later the boss slams down on the footprint.
     * The footprint is only safe from the eruption: stand under it for the eruption, be out for the
     * slam. Hits land a tick after they are queued, so these are queued a tick ahead of the splats.
     */
    private fun untether(): Effect {
        val dots =
            customTiles(OcellusArena.FLOOR) { npc, _, random ->
                val underfoot =
                    OcellusArena.quadrantPlayers(npc, deps.playerList).map { it.coords }.distinct()
                val free = quadrantFloor(npc).apply { removeAll(underfoot) }
                val extra = minOf(random.of(UNTETHER_DOTS) - underfoot.size, free.size).coerceAtLeast(0)
                underfoot + List(extra) { free.removeAt(random.of(free.size)) }
            }
        val pool =
            hit {
                damage(UNTETHER_POOL_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        val bolt =
            hit {
                damage(UNTETHER_MAGIC_DAMAGE).roll()
                type(HitType.Magic)
            }
        val eruption =
            hit {
                damage(UNTETHER_ERUPTION_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        val slam =
            hit {
                damage(UNTETHER_SLAM_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        fun underBoss(npc: Npc, player: Player) =
            player.coords.x - npc.coords.x in 0 until BOSS_SIZE &&
                player.coords.z - npc.coords.z in 0 until BOSS_SIZE
        val erupting =
            Condition.Custom { npc, player ->
                player != null &&
                    OcellusArena.sameQuadrantFloor(npc, player.coords) &&
                    !underBoss(npc, player)
            }
        val slamming = Condition.Custom { npc, player -> player != null && underBoss(npc, player) }
        val inQuadrant =
            Condition.Custom { npc, player ->
                player != null && OcellusArena.sameQuadrantFloor(npc, player.coords)
            }
        fun pools(index: Int): Effect {
            val name = "untether_$index"
            return sequence(
                wait(UNTETHER_FIRST_WAVE + UNTETHER_WAVE_GAP * index),
                withTiles(
                    name,
                    dots,
                    sequence(
                        onTiles(bound(name), mapSpotanim(UNTETHER_DOT_SPOT, CurrentTile)),
                        wait(UNTETHER_WAVE_GAP),
                        onTiles(bound(name), onEach(playersOn(CurrentTile), pool)),
                    ),
                ),
            )
        }
        fun spell(index: Int): Effect {
            val targets = playersIn(OcellusArena.FLOOR)
            return sequence(
                wait(UNTETHER_FIRST_WAVE + UNTETHER_WAVE_GAP * index - UNTETHER_PERSONAL_LEAD),
                onEach(targets, whenever(inQuadrant, external(UNTETHER_PERSONAL))),
                wait(UNTETHER_PERSONAL_LEAD),
                onEach(targets, whenever(inQuadrant, external(UNTETHER_IMPACT))),
                wait(UNTETHER_WAVE_GAP - 1),
                onEach(targets, whenever(inQuadrant, bolt)),
            )
        }
        val waves = (0 until UNTETHER_WAVES).flatMap { listOf(pools(it), spell(it)) }
        return parallel(
            sequence(
                lockMovement(),
                external(OcellusPhaseTwo.UNTETHER_GLIDE),
                say("Untether..."),
                anim(UNTETHER_IN_SEQ),
                idleAnim(UNTETHER_LOOP_SEQ),
            ),
            sequence(wait(UNTETHER_LOOP_AT), anim(UNTETHER_LOOP_SEQ)),
            sequence(wait(UNTETHER_GLOW_AT), spotanim(UNTETHER_GLOW_SPOT, delay = UNTETHER_GLOW_DELAY)),
            sequence(wait(UNTETHER_BORDER_AT), slamBurst()),
            sequence(
                wait(UNTETHER_ERUPT_AT),
                onEach(playersIn(OcellusArena.FLOOR), whenever(erupting, eruption)),
            ),
            sequence(
                wait(UNTETHER_SLAM_AT),
                onEach(playersIn(OcellusArena.FLOOR), whenever(slamming, slam)),
            ),
            sequence(
                wait(UNTETHER_OUT_AT),
                clearIdleAnim(),
                anim(UNTETHER_OUT_SEQ),
                nextAttackIn(UNTETHER_RECOVERY),
                unlockMovement(),
            ),
            *waves.toTypedArray(),
        )
    }

    private fun slamBurst(): Effect {
        fun inCorner(east: Boolean, north: Boolean) =
            Condition.Custom { npc, _ ->
                val middle = OcellusArena.middle(npc)
                (npc.coords.x > middle.x) == east && (npc.coords.z > middle.z) == north
            }
        val (southWest, southEast, northEast, northWest) = UNTETHER_ARENA_SPOTS.map { mapSpotanim(it, Centre) }
        return whenever(
            inCorner(east = false, north = false),
            southWest,
            otherwise =
                whenever(
                    inCorner(east = true, north = false),
                    southEast,
                    otherwise = whenever(inCorner(east = true, north = true), northEast, otherwise = northWest),
                ),
        )
    }

    private fun quadrantFloor(npc: Npc): MutableList<CoordGrid> {
        val centre = npc.coords.translate(BOSS_SIZE / 2, BOSS_SIZE / 2)
        val tiles = ArrayList<CoordGrid>()
        for (dx in -UNTETHER_REACH..UNTETHER_REACH) {
            for (dz in -UNTETHER_REACH..UNTETHER_REACH) {
                val tile = centre.translate(dx, dz)
                if (OcellusArena.sameQuadrantFloor(npc, tile)) tiles.add(tile)
            }
        }
        return tiles
    }

    private fun magicSpread(): Effect =
        sequence(
            anim(SPREAD_SEQ),
            spotanim(SPREAD_CAST_SPOT, delay = SPREAD_CAST_DELAY, slot = SPREAD_CAST_SLOT),
            sound("synth.ocellus_magic_spread_cast", delay = SPREAD_SOUND_DELAY),
            onEach(
                playersIn(OcellusArena.FLOOR),
                projectile(
                    spotanim = SPREAD_PROJECTILE,
                    config =
                        ProjectileConfig.fixed(
                            startHeight = SPREAD_START_HEIGHT,
                            endHeight = SPREAD_END_HEIGHT,
                            delay = SPREAD_DELAY,
                            travel = SPREAD_TRAVEL,
                            angle = SPREAD_ANGLE,
                        ),
                    hit =
                        hit {
                            damage(SPREAD_DAMAGE).roll()
                            type(HitType.Magic)
                            spotanim(SPREAD_IMPACT)
                        },
                    resolveOnImpact = true,
                ),
            ),
        )

    private fun shadowSlash(): Effect {
        val strike =
            hit {
                damage(Fixed(SLASH_DAMAGE))
                type(HitType.Typeless)
                hazard()
            }
        val steps = 1..OcellusSlash.STEPS
        val sweeps =
            steps.map { step ->
                val sweep =
                    mapSpotanim(SLASH_SPOT, CurrentTile, delay = step * OcellusSlash.STEP_DELAY)
                onTiles(bound("slash_$step"), sequence(sweep, onEach(playersOn(CurrentTile), strike)))
            }
        val slash =
            sequence(
                lockMovement(),
                external(OcellusSlash.AIM),
                wait(1),
                anim(SLASH_SEQ),
                wait(1),
                *sweeps.toTypedArray(),
                wait(1),
                external(OcellusSlash.RELEASE),
                unlockMovement(),
            )
        return withBands("slash", OcellusSlash::band, slash)
    }

    private fun insolence(): Effect {
        val strike =
            hit {
                damage(Fixed(INSOLENCE_DAMAGE))
                type(HitType.Typeless)
                hazard()
            }
        val sweeps =
            (1..OcellusSlash.STEPS).map { step ->
                val sweep =
                    mapSpotanim(
                        INSOLENCE_SPOT,
                        CurrentTile,
                        delay = INSOLENCE_SWEEP_DELAY + step * OcellusSlash.STEP_DELAY,
                    )
                onTiles(
                    bound("insolence_$step"),
                    sequence(
                        sweep,
                        external(OcellusVoid.INSOLENCE, params = step, at = CurrentTile),
                        onEach(playersOn(CurrentTile), strike),
                    ),
                )
            }
        val swing =
            sequence(
                external(OcellusSlash.AIM_AT),
                wait(1),
                anim(INSOLENCE_SEQ, delay = INSOLENCE_ANIM_DELAY),
                wait(INSOLENCE_CAST_TO_SWEEP),
                *sweeps.toTypedArray(),
                wait(1),
                external(OcellusSlash.RELEASE),
            )
        val aim = customTile { npc, _ -> OcellusSlash.pickAim(npc, deps) }
        val cast = onEach(playersOn(aim), withBands("insolence", OcellusSlash::superiorBand, swing))
        return sequence(
            lockMovement(),
            say("Insolence..."),
            wait(1),
            cast,
            cast,
            cast,
            nextAttackIn(INSOLENCE_RECOVERY),
            unlockMovement(),
        )
    }

    /** Binds each step's band of tiles up front, so what later hits is what was aimed at. */
    private fun withBands(
        prefix: String,
        band: (Npc, Player, Int) -> List<CoordGrid>,
        inner: Effect,
    ): Effect =
        (1..OcellusSlash.STEPS).reversed().fold(inner) { acc, step ->
            val tiles = customTiles(OcellusArena.FLOOR) { npc, target, _ -> band(npc, target, step) }
            withTiles("${prefix}_$step", tiles, acc)
        }

    private companion object {
        const val DEBRIS = "debris"
        const val INSOLENCE = "insolence"
        const val UNTETHER = "untether"
        val SCRIPTS =
            listOf(
                listOf(DEBRIS, UNTETHER),
                listOf(DEBRIS, INSOLENCE, DEBRIS, UNTETHER),
                listOf(DEBRIS, INSOLENCE, DEBRIS),
            )
        const val DEBRIS_TILES = "debris"

        // PLACEHOLDER: an estimate; no source for the real figure.
        val DEBRIS_DAMAGE = 35..55
        const val DEBRIS_MARKS_SHORT = 3
        const val DEBRIS_MARKS_LOOPED = 6
        const val DEBRIS_FALL = 5
        const val DEBRIS_OUT = 5
        const val DEBRIS_LOOP_SPOT = 3
        const val DEBRIS_RECOVERY = 4
        const val SPIN_HIT_DELAY = 1
        const val DEBRIS_LOOP = DEBRIS_OUT - DEBRIS_LOOP_SPOT + SPIN_HIT_DELAY
        const val DEBRIS_VINE_DELAY = 4
        const val PRAYER_IN_SLOT = 4
        const val PRAYER_SLOT = 2
        const val PRAYER_IN_SEQ = "seq.npc_ocellus_phase3_prayer_in"
        const val PRAYER_LOOP_SEQ = "seq.npc_ocellus_phase3_prayer_loop"
        const val PRAYER_OUT_SEQ = "seq.npc_ocellus_phase3_prayer_out"
        const val PRAYER_IN_SPOT = "spotanim.vfx_ocellus_phase3_melee_prayer_in"
        const val PRAYER_LOOP_SPOT = "spotanim.vfx_ocellus_phase3_melee_prayer"
        const val PRAYER_OUT_SPOT = "spotanim.vfx_ocellus_phase3_melee_prayer_out"
        const val DEBRIS_SPOT = "spotanim.vfx_ocellus_falling_debris"
        const val DEBRIS_SOUND = "synth.ocellus_debris_fall"
        const val DEBRIS_SOUND_RADIUS = 1
        const val UNTETHER_IN_SEQ = "seq.npc_ocellus_phase3_unleash_in"
        const val UNTETHER_LOOP_SEQ = "seq.npc_ocellus_phase3_unleash_loop"
        const val UNTETHER_OUT_SEQ = "seq.npc_ocellus_phase3_unleash_out"
        const val UNTETHER_DOT_SPOT = "spotanim.vfx_ocellus_p2_aoe_dodge"
        const val UNTETHER_GLOW_SPOT = "spotanim.vfx_ocellus_p3_unleash_spotanim"
        const val UNTETHER_GLOW_DELAY = 360
        val UNTETHER_ARENA_SPOTS =
            listOf(
                "spotanim.vfx_ocellus_unleash_arena",
                "spotanim.vfx_ocellus_unleash_arena_270",
                "spotanim.vfx_ocellus_unleash_arena_180",
                "spotanim.vfx_ocellus_unleash_arena_90",
            )
        const val UNTETHER_LOOP_AT = 2
        const val UNTETHER_FIRST_WAVE = 6
        const val UNTETHER_WAVE_GAP = 4
        const val UNTETHER_WAVES = 4
        const val UNTETHER_GLOW_AT = 18
        const val UNTETHER_BORDER_AT = 25
        const val UNTETHER_ERUPT_AT = 28
        const val UNTETHER_OUT_AT = 29
        const val UNTETHER_SLAM_AT = 29
        const val UNTETHER_PERSONAL_LEAD = 1
        const val UNTETHER_RECOVERY = 8
        const val UNTETHER_RIFT_DELAY = 3
        const val UNTETHER_REACH = 12
        val UNTETHER_DOTS = 12..15

        // PLACEHOLDER: every capture shows the magic hit fully prayed against (0); this is the spread's figure.
        val UNTETHER_MAGIC_DAMAGE = 20..22
        val UNTETHER_POOL_DAMAGE = 5..25
        val UNTETHER_ERUPTION_DAMAGE = 35..45
        val UNTETHER_SLAM_DAMAGE = 18..45
        const val INSOLENCE_SEQ = "seq.npc_ocellus_phase3_melee_superior_slash"
        const val INSOLENCE_SPOT = "spotanim.vfx_ocellus_p3_shadow_slash_superior_spotanim01"
        const val INSOLENCE_ANIM_DELAY = 30
        const val INSOLENCE_CAST_TO_SWEEP = 3
        const val INSOLENCE_SWEEP_DELAY = 2
        const val INSOLENCE_RECOVERY = 4
        const val INSOLENCE_DAMAGE = 43
        const val MELEE_BASIC_SEQ = "seq.npc_ocellus_phase3_melee_basic"
        const val MELEE_SWING_GAP = 3
        const val SLASH_RECOVERY = 2
        const val SPREAD_GAP = 6
        const val SPREAD_SEQ = "seq.npc_ocellus_phase3_basic_projectile_all"
        const val SPREAD_CAST_SPOT = "spotanim.vfx_ocellus_magic_spotanim01"
        const val SPREAD_CAST_SLOT = 4
        const val SPREAD_CAST_DELAY = 30
        const val SPREAD_SOUND_DELAY = 25
        const val SPREAD_PROJECTILE = "spotanim.vfx_ocellus_magic_projanim01"
        const val SPREAD_START_HEIGHT = 435
        const val SPREAD_END_HEIGHT = 124
        const val SPREAD_DELAY = 45
        const val SPREAD_TRAVEL = 15
        const val SPREAD_ANGLE = 100
        val SPREAD_DAMAGE = 20..22
        const val SLASH_SEQ = "seq.npc_ocellus_phase3_melee_slash"
        const val SLASH_SPOT = "spotanim.vfx_ocellus_p3_shadow_slash_spotanim01"
        const val SLASH_DAMAGE = 27
    }
}
