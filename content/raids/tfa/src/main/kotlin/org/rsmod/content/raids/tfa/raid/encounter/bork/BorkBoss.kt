package org.rsmod.content.raids.tfa.raid.encounter.bork

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.bosses.spec.VarExpr
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.ARMOUR
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.BORK_DEAD
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.BORK_REMOVED
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.DAZED
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.GUARANTEED_HIT
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.MAGIC_ARMOUR
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.SMORK_DEAD
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.SMORK_MAX
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.SMORK_PCT
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.SMORK_REMOVED
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.STOMP_SIDE
import org.rsmod.content.raids.tfa.raid.encounter.bork.BorkVarns.TEMPO
import org.rsmod.game.entity.npc.NpcApproach
import org.rsmod.game.hit.HitType as EngineHitType
import org.rsmod.plugin.scripts.ScriptContext

class BorkBoss
@Inject
constructor(deps: BossDeps, private val fights: BorkEncounter) : BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = {
                npc.vars["varn.flat_armour"] =
                    when {
                        npc.vars[DAZED] == 1 -> 0
                        hit.type == EngineHitType.Magic -> npc.vars[MAGIC_ARMOUR]
                        else -> npc.vars[ARMOUR]
                    }
            },
            onHit = { fights.of(npc)?.updateBar() },
        )
        SmorkHits(fights).register(this)
    }

    private enum class Step {
        Volley,
        Napalm,
        Ball,
    }

    override val spec: BossSpec =
        boss(BOSS) {
            stats(attackRate = 1)

            val arena = BorkArena.AREA
            val smork = ownedNpcs(SMORK)
            val bork = customNpcs { boss, _, _ -> listOf(boss) }

            fun fighters(npc: org.rsmod.game.entity.Npc) = BorkArena.players(npc.spawnCoords, deps.playerList)

            fun alive(): Condition =
                !varnIs(DONE, 1) and !varnIs(SMORK_DEAD, 1) and !varnIs(BORK_DEAD, 1)

            fun smorkAlive(): Condition = !varnIs(DONE, 1) and !varnIs(SMORK_DEAD, 1)

            fun meleeHit(max: Int) =
                hit {
                    damage(BorkArena.GONG_MIN..max).roll()
                    type(Melee)
                    penetration(BorkArena.PRAYER_LEAK_PERCENT)
                }

            fun dust(origin: TargetExpr.Single) =
                mapSpotanim("spotanim.tfa_wave_dust_short", CurrentTile, delay = manhattan(CurrentTile, origin))

            val gongOrigin = customTile { npc, _ -> BorkArena.dustOrigin(npc) }

            fun strike(first: Boolean): Effect {
                val reach: (org.rsmod.game.entity.Npc) -> Int = { npc ->
                    BorkArena.gongReach(npc, fighters(npc), first)
                }
                return sequence(
                    anim("seq.npc_bork_gong_melee01"),
                    sound("synth.l5_combat_mastery_ui_sparks_silence_05", radius = SOUND_RADIUS),
                    onTiles(
                        customTiles(arena) { npc, _, _ -> BorkArena.gongTiles(npc, reach(npc)) },
                        dust(gongOrigin),
                    ),
                    onEach(
                        playersIn(arena),
                        whenever(
                            Condition.Custom { npc, target ->
                                target != null &&
                                    target.coords in BorkArena.gongTiles(npc, reach(npc))
                            },
                            meleeHit(BorkArena.GONG_MAX),
                        ),
                    ),
                )
            }

            val gong =
                ability(
                    "gong",
                    sequence(
                        strike(true),
                        after(2, whenever(Condition.InPhase(CHASE), strike(false))),
                        switch(TEMPO, BorkArena.SPACING.map { nextAttackIn(it) }),
                    ),
                )

            fun shot(spot: String, type: HitType, flight: Int, max: Int, targets: TargetExpr) =
                onEach(
                    targets,
                    projectile(
                        spotanim = spot,
                        config = ProjectileConfig.fixed(SHOT_START_HEIGHT, SHOT_END_HEIGHT, SHOT_DELAY, flight - SHOT_DELAY, SHOT_ANGLE),
                        hit =
                            hit {
                                damage(0..max).roll()
                                type(type)
                                penetration(BorkArena.PRAYER_LEAK_PERCENT)
                            },
                    ),
                )

            fun volleyAt(flight: Int, max: Int, targets: TargetExpr) =
                oneOf(
                    shot("spotanim.vfx_smork_range_attack_projanim01", Ranged, flight, max, targets),
                    shot("spotanim.vfx_smork_mage_attack_projanim01", Magic, flight, max, targets),
                )

            val volley =
                sequence(
                    faceTile(
                        customTile { npc, _ -> fights.at(npc.coords)?.ballTarget() ?: npc.coords },
                        instant = true,
                    ),
                    anim("seq.npc_smork_cannon_attack01"),
                    switch(
                        TEMPO,
                        BorkArena.FLIGHT.map {
                            volleyAt(it, BorkArena.VOLLEY_MAX, playersInExcept(arena, Focus))
                        },
                    ),
                )

            val volleyAll =
                sequence(
                    faceTile(CurrentTargetTile, instant = true),
                    anim("seq.npc_smork_cannon_attack01"),
                    volleyAt(FRENZY_FLIGHT, BorkArena.FRENZY_VOLLEY_MAX, playersIn(arena)),
                )

            val splash =
                withTiles(
                    SPLASH,
                    customTilesAt(arena, ImpactTile) { npc, at, random ->
                        BorkArena.napalmSplash(npc.spawnCoords, at, random)
                    },
                    sequence(
                        sound("synth.tfa_smork_aoe_att_land_impact_01", radius = SOUND_RADIUS, at = ImpactTile),
                        asNpc(
                            bork,
                            whenever(
                                tilesInFootprint(SPLASH),
                                sequence(
                                    oneOf(
                                        (BorkArena.NAPALM_BORK_MIN..BorkArena.NAPALM_BORK_MAX).map {
                                            damageSelf(VarExpr.Const(it))
                                        }
                                    ),
                                    external("bork.hp"),
                                ),
                            ),
                        ),
                        onTiles(
                            bound(SPLASH),
                            sequence(
                                mapSpotanim(
                                    "spotanim.vfx_smork_napalm_fire_spotanim01",
                                    CurrentTile,
                                    delay = manhattan(CurrentTile, ImpactTile),
                                ),
                                oneOf(
                                    mapSpotanim("spotanim.vfx_smork_napalm_splat_spotanim01", CurrentTile),
                                    mapSpotanim("spotanim.vfx_smork_napalm_splat_spotanim01_90", CurrentTile),
                                    mapSpotanim("spotanim.vfx_smork_napalm_splat_spotanim01_180", CurrentTile),
                                    mapSpotanim("spotanim.vfx_smork_napalm_splat_spotanim01_270", CurrentTile),
                                ),
                                onEach(
                                    playersOn(CurrentTile),
                                    hit {
                                        damage(BorkArena.NAPALM_MIN..BorkArena.NAPALM_MAX).roll()
                                        type(Typeless)
                                        hazard()
                                    },
                                ),
                                after(
                                    1,
                                    sequence(
                                        spawnLoc(OIL, CurrentTile, randomAngle = true),
                                        after(OIL_TICKS, replaceLoc(CurrentTile, OIL_GONE)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

            val napalm =
                withTile(
                    "napalm",
                    randomPlayer(arena),
                    sequence(
                        faceTile(tile("napalm"), instant = true),
                        oneOf(say("Burny gloop!"), say("Fireeeeeeeeee!"), say("Hot stuff!")),
                        oneOf(
                            (1..VOICES).map { sound("synth.tfa_smork_voice_$it", radius = SOUND_RADIUS) },
                        ),
                        after(
                            1,
                            sequence(
                                anim("seq.npc_smork_cannon_attack01"),
                                sound("synth.tfa_smork_big_cannon_att_aoe_projectile_01", radius = SOUND_RADIUS),
                                mapSpotanim("spotanim.gargboss_debris_shadow_120", tile("napalm")),
                                projectile(
                                    spotanim = "spotanim.vfx_smork_napalm_fire_projanim01",
                                    config = ProjectileConfig.fixed(SHOT_START_HEIGHT, SHOT_END_HEIGHT, SHOT_DELAY, NAPALM_FLIGHT - SHOT_DELAY, SHOT_ANGLE),
                                    target = tile("napalm"),
                                    onImpact = splash,
                                ),
                            ),
                        ),
                    ),
                )

            val ballLand =
                sequence(
                    onEach(
                        AllInRadius(1, ImpactTile),
                        hit {
                            damage(0..BorkArena.BALL_MAX).roll()
                            type(Typeless)
                            hazard()
                        },
                    ),
                    whenever(
                        tileInFootprint(ImpactTile),
                        whenever(alive(), run("daze")),
                        after(2, whenever(alive(), run("stomp_missed"))),
                    ),
                    after(CYCLE_GAP, run("pick_cycle")),
                )

            val ball =
                sequence(
                    say("Big bomb! Bork watch out!"),
                    after(
                        2,
                        withTile(
                            "ball",
                            customTile { npc, _ -> fights.at(npc.coords)?.ballTarget() ?: npc.coords },
                            sequence(
                                faceTile(tile("ball"), instant = true),
                                anim("seq.npc_smork_big_cannon_attack01"),
                                sound("synth.tfa_smork_big_cannon_big_shot_projectile_01", radius = SOUND_RADIUS, delay = BALL_SOUND_DELAY),
                                mapSpotanim("spotanim.gargboss_debris_shadow_150", tile("ball")),
                                projectile(
                                    spotanim = "spotanim.cannonball_travel_big",
                                    config = ProjectileConfig.fixed(SHOT_START_HEIGHT, SHOT_END_HEIGHT, SHOT_DELAY, BALL_FLIGHT - SHOT_DELAY, SHOT_ANGLE),
                                    target = tile("ball"),
                                    onImpact = asNpc(bork, ballLand),
                                ),
                            ),
                        ),
                    ),
                )

            fun chain(steps: List<Step>, i: Int = 0): Effect {
                val step =
                    whenever(
                        alive(),
                        when (steps[i]) {
                            Step.Volley -> volley
                            Step.Napalm -> napalm
                            Step.Ball -> ball
                        },
                    )
                if (i == steps.lastIndex) return step
                val factor = if (steps[i] == Step.Napalm) 2 else 1
                return sequence(
                    step,
                    switch(TEMPO, BorkArena.SPACING.map { after(it * factor, chain(steps, i + 1)) }),
                )
            }

            val v = Step.Volley
            val n = Step.Napalm
            val b = Step.Ball
            val firstShapes = listOf(listOf(v, n, v, b), listOf(v, n, v, b), listOf(v, n, v, v, b))
            val laterShapes =
                listOf(
                    listOf(v, v, n, v, v, b),
                    listOf(v, v, n, v, v, b),
                    listOf(v, v, n, v, v, b),
                    listOf(v, v, v, n, v, v, b),
                    listOf(v, v, n, v, b),
                    listOf(v, v, n, b),
                )
            val firstCycles =
                firstShapes.mapIndexed { i, steps -> ability("cycle_first_$i", asNpc(smork, chain(steps))) }
            val laterCycles =
                laterShapes.mapIndexed { i, steps -> ability("cycle_$i", asNpc(smork, chain(steps))) }
            ability("pick_first", oneOf(firstCycles.map { run(it) }))
            ability("pick_cycle", whenever(alive(), oneOf(laterCycles.map { run(it) })))

            ability(
                "daze",
                sequence(
                    transitionTo(DAZED_PHASE),
                    setVarn(DAZED, 1),
                    setVarn(GUARANTEED_HIT, 1),
                    anim("seq.npc_bork_14_cycle_stun01"),
                    spotanim("spotanim.vfx_bork_14_cycle_stun_spotanim01"),
                    oneOf((1..DAZE_SOUNDS).map { sound("synth.tfa_bork_daze_$it", radius = SOUND_RADIUS) }),
                    say("Oof."),
                    asNpc(
                        smork,
                        oneOf(
                            say("Bork! We sorry! No go!"),
                            say("No! Fearless leader!"),
                            say("That you fault!"),
                        ),
                    ),
                    after(
                        DAZE_TICKS,
                        whenever(
                            !varnIs(BORK_DEAD, 1),
                            sequence(
                                setVarn(DAZED, 0),
                                setVarn(GUARANTEED_HIT, 0),
                                whenever(!varnIs(SMORK_DEAD, 1), run("stomp")),
                            ),
                        ),
                    ),
                ),
            )

            fun wave(index: Int): Effect {
                val foot = customTile { npc, _ -> BorkArena.foot(npc, npc.vars[STOMP_SIDE], index) }
                return sequence(
                    sound("synth.l5_combat_mastery_ui_sparks_silence_05", radius = SOUND_RADIUS),
                    onTiles(customTiles(arena) { npc, _, _ -> BorkArena.stompTiles(npc) }, dust(foot)),
                    onEach(
                        playersIn(arena),
                        whenever(
                            Condition.Custom { npc, target ->
                                target != null && !BorkArena.shadowed(npc, target.coords)
                            },
                            meleeHit(BorkArena.GONG_MAX),
                        ),
                    ),
                )
            }

            fun stomp(line: String) =
                sequence(
                    transitionTo(STOMPING),
                    external("bork.stomp_side"),
                    say(line),
                    anim("seq.npc_bork_enraged_stomp_attack_in01"),
                    after(
                        2,
                        sequence(
                            anim("seq.npc_bork_enraged_stomp_attack01"),
                            spotanim("spotanim.vfx_bork_enraged_stomp_spotanim01"),
                        ),
                    ),
                    after(STOMP_WAVE_GAP, wave(0)),
                    after(STOMP_WAVE_GAP * 2, wave(1)),
                    after(STOMP_WAVE_GAP * 3, wave(2)),
                    after(STOMP_WAVE_GAP * 4, wave(3)),
                    after(STOMP_OUT, anim("seq.npc_bork_enraged_stomp_attack_cycle_and_out01")),
                    after(
                        STOMP_RETARGET,
                        whenever(
                            !varnIs(BORK_DEAD, 1),
                            sequence(
                                setFocus(randomPlayer(arena)),
                                onEach(Focus, external("bork.smash")),
                            ),
                        ),
                    ),
                    after(
                        STOMP_END,
                        whenever(alive(), transitionTo(CHASE)),
                    ),
                )

            ability("stomp", stomp("Bork very angry!!"))
            ability("stomp_missed", stomp("Bork think it stomp time now!"))

            val tempoLines =
                listOf(
                    "Bork get into it!" to "Smorks go faster!",
                    "Bork make fast drum!" to "Smorks feeling drum beat now!",
                    "Bork maximum crush!" to "Smorks go fastest!",
                )
            for ((i, lines) in tempoLines.withIndex()) {
                ability(
                    "tempo_${i + 1}",
                    sequence(
                        setVarn(TEMPO, i + 1),
                        whenever(!varnIs(BORK_DEAD, 1), say(lines.first)),
                        whenever(smorkAlive(), asNpc(smork, say(lines.second))),
                    ),
                )
            }

            ability(
                "begin",
                sequence(
                    song(MUSIC, arena),
                    sound("synth.tfa_bork_enraged_laughter_01", radius = SOUND_RADIUS),
                    setHp(varn(HP_MAX), varn(HP_MAX)),
                    asNpc(smork, setHp(varn(SMORK_MAX), varn(SMORK_MAX))),
                    setVarn(SMORK_PCT, 100),
                    setVarn(ARMOUR, ARMOUR_START),
                    setVarn(MAGIC_ARMOUR, 0),
                    setVarn(TEMPO, 0),
                    setFocus(CurrentTarget),
                    nextAttackIn(FIRST_GONG),
                    external("bork.bar"),
                    after(FIRST_SMORK, run("pick_first")),
                ),
            )

            val frenzyVolley = whenever(smorkAlive(), volleyAll)
            val frenzyNapalm = whenever(smorkAlive(), sequence(napalm, run("frenzy_cycle")))
            ability(
                "frenzy_open_a",
                asNpc(
                    smork,
                    sequence(frenzyVolley, after(2, frenzyVolley), after(4, frenzyNapalm)),
                ),
            )
            ability(
                "frenzy_open_b",
                asNpc(
                    smork,
                    sequence(
                        frenzyVolley,
                        after(2, frenzyVolley),
                        after(4, frenzyVolley),
                        after(6, frenzyNapalm),
                    ),
                ),
            )
            ability("frenzy_open", oneOf(run("frenzy_open_a"), run("frenzy_open_b")))
            ability(
                "frenzy_cycle",
                asNpc(
                    smork,
                    whenever(
                        smorkAlive(),
                        sequence(
                            *FRENZY_OFFSETS.map { after(it, frenzyVolley) }.toTypedArray(),
                            after(FRENZY_PERIOD, frenzyNapalm),
                        ),
                    ),
                ),
            )

            ability(
                "bork_die",
                sequence(
                    interrupt(),
                    retire(),
                    transitionTo(DEAD),
                    setVarn(BORK_DEAD, 1),
                    anim("seq.npc_bork_death01"),
                    spotanim("spotanim.vfx_bork_death_spotanim01"),
                    say("Smorks... avenge... Bork."),
                    after(
                        DEATH_VANISH,
                        sequence(
                            transmog("npc.tfa_invisible_npc", Int.MAX_VALUE),
                            setVarn(BORK_REMOVED, 1),
                            whenever(smorkAlive(), asNpc(smork, say("Human die for that!"))),
                        ),
                    ),
                    after(FRENZY_START, whenever(smorkAlive(), run("frenzy_open"))),
                ),
            )

            ability(
                "smork_die",
                sequence(
                    setVarn(SMORK_DEAD, 1),
                    asNpc(
                        smork,
                        sequence(
                            retire(),
                            whenever(varnIs(BORK_DEAD, 1), say("Uh oh."), say("No, Smorks fail Bork!")),
                            after(
                                2,
                                sequence(
                                    spotanim("spotanim.vfx_smork_tower_death_spotanim01"),
                                    spotanim("spotanim.vfx_smork_death_explosion_spotanim01", slot = 1),
                                    spotanim("spotanim.vfx_smork_goblins_death_spotanim01", slot = 2),
                                    sound("synth.tfa_smork_turret_explosion_01", radius = SOUND_RADIUS),
                                    transparency(0, SMORK_FADE_CYCLES, 0, TRANSPARENT),
                                ),
                            ),
                            after(SMORK_VANISH, transmog("npc.tfa_invisible_npc", Int.MAX_VALUE)),
                        ),
                    ),
                    after(SMORK_VANISH, setVarn(SMORK_REMOVED, 1)),
                    whenever(!varnIs(BORK_DEAD, 1), run("enraged_open")),
                ),
            )

            fun arenaWave() =
                sequence(
                    anim("seq.npc_bork_enraged_gong_attack01"),
                    sound("synth.l5_combat_mastery_ui_sparks_silence_05", radius = SOUND_RADIUS),
                    onTiles(
                        customTiles(arena) { npc, _, _ -> BorkArena.arenaTiles(npc.spawnCoords) },
                        dust(gongOrigin),
                    ),
                    onEach(playersIn(arena), meleeHit(BorkArena.ENRAGED_GONG_MAX)),
                )

            ability(
                "enraged_open",
                sequence(
                    transitionTo(ENRAGED),
                    after(
                        ENRAGED_SAY,
                        whenever(
                            !varnIs(BORK_DEAD, 1),
                            sequence(
                                say("Bork kill human for that!"),
                                anim("seq.npc_bork_idle_to_enraged_gong_attack_in01"),
                            ),
                        ),
                    ),
                ),
            )
            ability(
                "enraged_pair",
                whenever(
                    !varnIs(BORK_DEAD, 1) and !varnIs(DONE, 1),
                    sequence(
                        arenaWave(),
                        after(2, arenaWave()),
                        after(4, anim("seq.npc_bork_enraged_gong_attack_out01")),
                    ),
                ),
            )

            ability(
                "complete",
                sequence(
                    setVarn(DONE, 1),
                    onTiles(
                        ownedLocs(OIL),
                        sequence(
                            oneOf(
                                mapSpotanim("spotanim.vfx_smork_napalm_despawn_spotanim01", CurrentTile),
                                mapSpotanim("spotanim.vfx_smork_napalm_despawn_spotanim01_90", CurrentTile),
                                mapSpotanim("spotanim.vfx_smork_napalm_despawn_spotanim01_180", CurrentTile),
                                mapSpotanim("spotanim.vfx_smork_napalm_despawn_spotanim01_270", CurrentTile),
                            ),
                            removeLoc(CurrentTile),
                        ),
                    ),
                    external("bork.complete"),
                ),
            )

            phase(CHASE, approach = NpcApproach.ChaseToMelee) {
                weightedSelectorRandom { +random(gong, weight = 1) }
            }
            phase(DAZED_PHASE, scriptedMovement = true, lockMovement = true) {}
            phase(STOMPING, scriptedMovement = true, lockMovement = true) {}
            phase(ENRAGED, scriptedMovement = true, lockMovement = true) {
                every(ENRAGED_PERIOD, run("enraged_pair"), firstAfter = ENRAGED_FIRST)
            }
            phase(DEAD, scriptedMovement = true, lockMovement = true) {}

            every(
                1,
                whenever(
                    Condition.VarnIn(SMORK_PCT, 0..TEMPO_PCTS[0]) and varnIs(TEMPO, 0),
                    run("tempo_1"),
                ),
            )
            every(
                1,
                whenever(
                    Condition.VarnIn(SMORK_PCT, 0..TEMPO_PCTS[1]) and varnIs(TEMPO, 1),
                    run("tempo_2"),
                ),
            )
            every(
                1,
                whenever(
                    Condition.VarnIn(SMORK_PCT, 0..TEMPO_PCTS[2]) and varnIs(TEMPO, 2),
                    run("tempo_3"),
                ),
            )
            every(1, whenever(Condition.HpExact(1) and !varnIs(BORK_DEAD, 1), run("bork_die")))
            every(
                1,
                whenever(
                    Condition.VarnIn(SMORK_PCT, 0..0) and !varnIs(SMORK_DEAD, 1) and !varnIs(DONE, 1),
                    run("smork_die"),
                ),
            )
            every(
                1,
                whenever(
                    !varnIs(DONE, 1),
                    onTiles(
                        ownedLocs(OIL),
                        onEach(
                            playersOn(CurrentTile),
                            sequence(
                                hit {
                                    damage(BorkArena.OIL_MIN..BorkArena.OIL_MAX).roll()
                                    type(Typeless)
                                    hazard()
                                },
                                external("bork.ignite"),
                            ),
                        ),
                    ),
                ),
            )
            every(
                1,
                whenever(
                    varnIs(BORK_REMOVED, 1) and varnIs(SMORK_REMOVED, 1) and !varnIs(DONE, 1),
                    run("complete"),
                ),
            )
        }

    private companion object {
        const val BOSS = "npc.tfa_bork"
        const val SMORK = "npc.tfa_smork"
        const val OIL = "loc.tfa_bork_ragged_tile"
        const val OIL_GONE = "loc.invisible_type8_nonblocking"
        const val OIL_TICKS = 199
        const val SPLASH = "splash"

        const val CHASE = "chase"
        const val DAZED_PHASE = "dazed"
        const val STOMPING = "stomping"
        const val ENRAGED = "enraged"
        const val DEAD = "dead"

        const val MUSIC = 888
        const val SOUND_RADIUS = 25
        const val VOICES = 9
        const val DAZE_SOUNDS = 4

        const val ARMOUR_START = 8
        const val FIRST_GONG = 8
        const val FIRST_SMORK = 13

        const val SHOT_START_HEIGHT = 100
        const val SHOT_END_HEIGHT = 31
        const val SHOT_DELAY = 20
        const val SHOT_ANGLE = 15
        const val NAPALM_FLIGHT = 120
        const val BALL_FLIGHT = 150
        const val BALL_SOUND_DELAY = 15
        const val FRENZY_FLIGHT = 90

        const val CYCLE_GAP = 39
        const val DAZE_TICKS = 14
        const val STOMP_WAVE_GAP = 3
        const val STOMP_OUT = 8
        const val STOMP_RETARGET = 15
        const val STOMP_END = 19

        val TEMPO_PCTS = intArrayOf(75, 50, 25)

        const val DEATH_VANISH = 7
        const val FRENZY_START = 11
        const val SMORK_VANISH = 9
        const val SMORK_FADE_CYCLES = 210
        const val TRANSPARENT = 255
        val FRENZY_OFFSETS = intArrayOf(4, 6, 8, 10, 12)
        const val FRENZY_PERIOD = 14

        const val ENRAGED_SAY = 10
        const val ENRAGED_FIRST = 12
        const val ENRAGED_PERIOD = 8
    }
}
