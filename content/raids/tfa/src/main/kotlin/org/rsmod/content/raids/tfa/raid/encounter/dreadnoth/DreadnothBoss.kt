package org.rsmod.content.raids.tfa.raid.encounter.dreadnoth

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.bosses.spec.VarExpr
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.ABSORBED
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.DEALT
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.SPAWN_BUDGET
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.SPLIT
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothVarns.WAVES_DONE
import org.rsmod.game.entity.npc.NpcApproach
import org.rsmod.plugin.scripts.ScriptContext

class DreadnothBoss
@Inject
constructor(deps: BossDeps, private val fights: DreadnothEncounter) : BossPluginScript(deps) {

    private val jellyType by lazy { "npc.tfa_jelly_minion".asRSCM(RSCMType.NPC) }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("dreadnoth.hunt") { ctx ->
            fights.at(ctx.npc.coords)?.hunt(ctx.npc, ctx.params as? Int ?: 0)
        }
        BossCombat.register(this, spec, deps, onHit = { fights.of(npc)?.updateBar() })
    }

    override val spec: BossSpec =
        boss(BOSS) {
            stats(attackRate = MELEE_RATE)

            val arena = DreadnothArena.AREA
            val footprint = area(Self, offset(Self, FOOTPRINT_SPAN, FOOTPRINT_SPAN))
            val ring = area(offset(Self, -1, -1), offset(Self, FOOTPRINT_SPAN + 1, FOOTPRINT_SPAN + 1))
            val footprintTiles = customTiles(arena) { npc, _, _ -> DreadnothArena.footprint(npc) }
            val dyingJellies = customNpcs { _, _, owned -> DreadnothArena.dyingJellies(owned, jellyType) }
            val jelliesUnder =
                customNpcs { boss, _, owned -> DreadnothArena.jelliesUnder(boss, owned, jellyType) }
            val noOwnedJelly =
                Condition.Custom { npc, _ ->
                    deps.encounterRegistry.of(npc).ownedNpcList().none { it.visType.id == jellyType }
                }

            val splat = mapSpotanim("spotanim.tob_bloat_blood_splat", CurrentTile)
            val placeTile =
                sequence(
                    spawnLoc(DAMAGE_TILE, CurrentTile, randomAngle = true),
                    splat,
                    sound("synth.tfa_dreadnoth_tile", radius = SOUND_RADIUS, at = CurrentTile),
                )

            val melee =
                ability(
                    "melee",
                    sequence(
                        anim("seq.dreadnoth_melee01"),
                        onEach(
                            playersIn(ring),
                            whenever(
                                Condition.Custom { npc, target ->
                                    target != null && DreadnothArena.edgeAdjacent(npc, target)
                                },
                                hit {
                                    damage(0..MELEE_MAX).roll()
                                    type(Melee)
                                },
                            ),
                        ),
                    ),
                )

            fun spit(start: Int, end: Int, onLand: Effect? = null) =
                projectile(
                    spotanim = "spotanim.vfx_dreadnoth_ranged_proj01",
                    config = ProjectileConfig.fixed(SPIT_START_HEIGHT, SPIT_END_HEIGHT, start, end - start, SPIT_ANGLE, SPIT_PROGRESS),
                    from = Centre,
                    hit =
                        hit {
                            damage(0..SPIT_MAX).roll()
                            type(Ranged)
                        },
                    onImpact = onLand,
                    impactLead = IMPACT_LEAD,
                )

            val mark =
                whenever(
                    varnAtLeast(SPAWN_BUDGET, 1),
                    sequence(
                        addVarn(SPAWN_BUDGET, -1),
                        onTiles(
                            takeTiles(
                                randomFreeTiles(
                                    area(
                                        offset(CurrentTargetTile, -MARKER_REACH, -MARKER_REACH),
                                        offset(CurrentTargetTile, MARKER_REACH, MARKER_REACH),
                                    ),
                                    MARKER_WINDOW..MARKER_WINDOW,
                                ) - taggedTiles(SPAWNING) - footprintTiles,
                                1,
                            ),
                            sequence(splat, tagTile(CurrentTile, SPAWNING)),
                        ),
                    ),
                )

            fun volley(perVolley: Int) =
                sequence(
                    anim("seq.dreadnoth_jump01"),
                    after(
                        1,
                        sequence(
                            setVarn(SPAWN_BUDGET, perVolley),
                            sound("synth.tfa_dreadnoth_slam", radius = SOUND_RADIUS, at = Centre),
                            onEach(
                                playersIn(arena),
                                hit {
                                    damage(0..VOLLEY_MAX).roll()
                                    type(Melee)
                                },
                            ),
                            onTiles(
                                randomFreeTiles(ring, TILES_PER_VOLLEY..TILES_PER_VOLLEY),
                                placeTile,
                            ),
                            onEach(
                                playersIn(arena),
                                parallel(spit(10, 90, mark), spit(12, 120, mark), spit(14, 150, mark)),
                            ),
                        ),
                    ),
                )

            val rise =
                onTiles(
                    taggedTiles(SPAWNING),
                    sequence(
                        summon(
                            JELLY,
                            radius = 0,
                            centeredOn = CurrentTile,
                            mode = NpcMode.None,
                            duration = Int.MAX_VALUE,
                            owned = true,
                            onSummon = HUNT_HANDLER,
                            onSummonParams = RISE_HUNT_DELAY,
                            behaviour = anim("seq.jelly_spawn"),
                        ),
                        untagTile(CurrentTile, SPAWNING),
                    ),
                )

            for (wave in 1..WAVES) {
                val volleys = (1..wave).flatMap { listOf(volley(MAX_JELLIES / wave), wait(VOLLEY_GAP)) }.dropLast(1)
                ability(
                    "wave_$wave",
                    sequence(
                        lockMovement(),
                        *volleys.toTypedArray(),
                        wait(RISE_DELAY),
                        rise,
                        unlockMovement(),
                        addVarn(WAVES_DONE, 1),
                    ),
                )
            }

            fun placeAt(at: TargetExpr.Single) =
                sequence(
                    spawnLoc(DAMAGE_TILE, at, randomAngle = true),
                    mapSpotanim("spotanim.tob_bloat_blood_splat", at),
                    sound("synth.tfa_dreadnoth_tile", radius = SOUND_RADIUS, at = at),
                )

            ability(
                "land",
                sequence(
                    sound("synth.tfa_dreadnoth_slam", radius = SOUND_RADIUS, at = Centre),
                    onEach(
                        playersIn(footprint),
                        hit {
                            damage(0..LAND_SLAM_MAX).roll()
                            type(Melee)
                            onHit(setVarn(DEALT, varn(DEALT) + HitDamage), immediately = true)
                        },
                    ),
                    asNpc(
                        jelliesUnder,
                        sequence(setVarn(ABSORBED, varn(ABSORBED) + Hp), despawnSelf()),
                    ),
                    healSelf(varn(DEALT) + varn(ABSORBED)),
                    setVarn(DEALT, 0),
                    setVarn(ABSORBED, 0),
                    onTiles(randomFreeTiles(ring, LAND_TILES), placeTile),
                    onEach(
                        playersIn(arena),
                        parallel(
                            spit(10, 90),
                            projectile(
                                spotanim = "spotanim.vfx_dreadnoth_ranged_proj02",
                                target = CurrentTargetTile,
                                config = ProjectileConfig.fixed(PROJ02_START_HEIGHT, PROJ02_END_HEIGHT, 10, 170, PROJ02_ANGLE),
                                from = Centre,
                                onImpact = placeAt(ImpactTile),
                                impactLead = IMPACT_LEAD,
                            ),
                            sound("synth.tfa_dreadnoth_spit", radius = SOUND_RADIUS, at = Centre),
                        ),
                    ),
                ),
            )

            ability(
                "jump",
                sequence(
                    setFocus(randomPlayer(arena)),
                    onEach(
                        Focus,
                        sequence(
                            anim("seq.dreadnoth_jump02"),
                            mapSpotanim(
                                "spotanim.tfa_boss_jelly_5x5_jump",
                                customTile { npc, target -> DreadnothArena.landingCentre(npc, target) },
                            ),
                            exactMove(
                                customTile { npc, target -> DreadnothArena.landing(npc, target) },
                                JUMP_CYCLES,
                                teleport = true,
                            ),
                        ),
                    ),
                    after(JUMP_LAND_DELAY, run("land")),
                ),
            )

            val jellyDie =
                sequence(
                    retire(),
                    anim("seq.jelly_death"),
                    onTiles(customTiles(arena) { npc, _, _ -> listOf(npc.coords) }, placeTile),
                    after(JELLY_REMOVE_DELAY, despawnSelf()),
                )

            val split =
                sequence(
                    setVarn(SPLIT, 1),
                    onTiles(
                        takeTiles(footprintTiles, SPLIT_COUNT),
                        summon(
                            JELLY,
                            radius = 0,
                            centeredOn = CurrentTile,
                            mode = NpcMode.None,
                            duration = Int.MAX_VALUE,
                            owned = true,
                            onSummon = HUNT_HANDLER,
                            onSummonParams = SPLIT_HUNT_DELAY,
                            behaviour =
                                sequence(
                                    teleport(CurrentTile),
                                    setHp(VarExpr.Const(SPLIT_HP), VarExpr.Const(SPLIT_HP)),
                                    anim("seq.npc_jelly_jump_1"),
                                    exactMove(
                                        customTile { npc, _ -> DreadnothArena.hop(npc) },
                                        HOP_CYCLES,
                                        teleport = true,
                                    ),
                                ),
                        ),
                    ),
                )

            ability(
                "die",
                sequence(
                    interrupt(),
                    retire(),
                    transitionTo(DEAD),
                    anim("seq.dreadnoth_death01"),
                    after(SPLIT_DELAY, split),
                    after(HOLD_DELAY, transmog("npc.tfa_invisible_npc", Int.MAX_VALUE)),
                    after(BAR_FADE_DELAY, external("dreadnoth.bar_fade")),
                    after(BAR_CLOSE_DELAY, external("dreadnoth.bar_close")),
                ),
            )

            ability(
                "complete",
                sequence(
                    setVarn(DONE, 1),
                    onTiles(ownedLocs(DAMAGE_TILE), removeLoc(CurrentTile)),
                    external("dreadnoth.complete"),
                ),
            )

            ability(
                "begin",
                sequence(
                    song(WAKE_SONG, arena),
                    setHp(varn(HP_MAX), varn(HP_MAX)),
                    external("dreadnoth.bar"),
                ),
            )

            phase("crawling", approach = NpcApproach.ChaseToMelee) {
                weightedSelectorRandom { +random(melee, weight = 1, requires = WithinMeleeRange) }
            }
            phase("enraged", scriptedMovement = true, lockMovement = true) {
                every(JUMP_INTERVAL, run("jump"), firstAfter = JUMP_FIRST)
            }
            phase(DEAD, scriptedMovement = true, lockMovement = true) {}

            every(1, onTiles(taggedTiles(SPAWNING), mapSpotanim("spotanim.jelly_spawning_idle", CurrentTile)))
            every(
                1,
                whenever(
                    !varnIs(DONE, 1),
                    onTiles(
                        ownedLocs(DAMAGE_TILE),
                        onEach(
                            playersOn(CurrentTile),
                            hit {
                                damage(BURN_MIN..BURN_MAX).roll()
                                type(Typeless)
                                hazard()
                            },
                        ),
                    ),
                ),
            )
            every(1, asNpc(dyingJellies, jellyDie))
            every(1, whenever(Condition.HpExact(1) and !Condition.InPhase(DEAD), run("die")))
            every(
                1,
                whenever(
                    Condition.InPhase(DEAD) and varnIs(SPLIT, 1) and noOwnedJelly and !varnIs(DONE, 1),
                    run("complete"),
                ),
            )

            triggers {
                on(HpBelow(WAVE_FRACTIONS[0]) and varnIs(WAVES_DONE, 0)) runs run("wave_1")
                on(HpBelow(WAVE_FRACTIONS[1]) and varnIs(WAVES_DONE, 1)) runs run("wave_2")
                on(HpBelow(WAVE_FRACTIONS[2]) and varnIs(WAVES_DONE, 2)) runs run("wave_3")
                on(HpBelow(ENRAGE_FRACTION) and varnIs(WAVES_DONE, WAVES)) runs
                    sequence(say("*glub glub*"), transitionTo("enraged"))
            }
        }

    private companion object {
        const val BOSS = "npc.tfa_jelly_boss"
        const val JELLY = "npc.tfa_jelly_minion"
        const val DAMAGE_TILE = "loc.tfa_jelly_boss_damage_tile"
        const val HUNT_HANDLER = "dreadnoth.hunt"
        const val SPAWNING = "spawning"
        const val DEAD = "dead"

        const val MELEE_RATE = 4
        const val MELEE_MAX = 45
        const val VOLLEY_MAX = 45
        const val LAND_SLAM_MAX = 51
        const val SPIT_MAX = 25
        const val FOOTPRINT_SPAN = 4
        const val SOUND_RADIUS = 25

        const val SPIT_START_HEIGHT = 450
        const val SPIT_END_HEIGHT = 30
        const val SPIT_ANGLE = 25
        const val SPIT_PROGRESS = 200
        const val PROJ02_START_HEIGHT = 650
        const val PROJ02_END_HEIGHT = 60
        const val PROJ02_ANGLE = 30

        const val WAVES = 3
        val WAVE_FRACTIONS = doubleArrayOf(0.85, 0.65, 0.45)
        const val VOLLEY_GAP = 6
        const val RISE_DELAY = 8
        const val MAX_JELLIES = 12
        const val TILES_PER_VOLLEY = 3
        const val MARKER_REACH = 2
        const val MARKER_WINDOW = 25
        const val RISE_HUNT_DELAY = 2

        const val ENRAGE_FRACTION = 0.26
        const val JUMP_FIRST = 5
        const val JUMP_INTERVAL = 8
        const val JUMP_CYCLES = 90
        const val JUMP_LAND_DELAY = 3
        const val IMPACT_LEAD = 1
        val LAND_TILES = 1..3

        const val BURN_MIN = 6
        const val BURN_MAX = 8
        const val JELLY_REMOVE_DELAY = 3

        const val SPLIT_COUNT = 7
        const val SPLIT_HP = 37
        const val SPLIT_DELAY = 3
        const val HOLD_DELAY = 4
        const val HOP_CYCLES = 30
        const val SPLIT_HUNT_DELAY = 1

        const val WAKE_SONG = 895
        const val BAR_FADE_DELAY = 8
        const val BAR_CLOSE_DELAY = 12
    }
}
