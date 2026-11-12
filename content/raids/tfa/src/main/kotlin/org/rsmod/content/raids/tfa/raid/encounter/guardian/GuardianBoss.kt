package org.rsmod.content.raids.tfa.raid.encounter.guardian

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
import org.rsmod.api.bosses.spec.ImpactRounding
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.BATTERY_HIT
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.BATTERY_HP
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.ENRAGED
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.GROUND_MAX
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.HEALTH
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.LANDINGS
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianVarns.SHIELD_MAX
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.LineValidator

class GuardianBoss
@Inject
constructor(deps: BossDeps, private val fights: GuardianEncounter) : BossPluginScript(deps) {

    private val lines by lazy { LineValidator(deps.collision) }

    private val batteryActive by lazy { BATTERY_ACTIVE.asRSCM(RSCMType.NPC) }
    private val flyingType by lazy { FLYING.asRSCM(RSCMType.NPC) }
    private val groundedType by lazy { GROUNDED.asRSCM(RSCMType.NPC) }

    override fun ScriptContext.startup() {
        GuardianBatteryHits(deps, fights).register(this)
        BossCombat.register(this, spec, deps, onHit = { fights.of(npc)?.updateBar() })
    }

    override val spec: BossSpec =
        boss(START, FLYING, GROUNDED) {
            stats(attackRate = ATTACK_RATE)

            val arena = GuardianArena.AREA
            val shieldMax = varn(SHIELD_MAX)
            val groundMax = varn(GROUND_MAX)
            val landedAny = landedPhases().map { Condition.InPhase(it) as Condition }.reduce { a, b -> a or b }
            val enragedNow: Condition = Condition.InPhase(ENRAGED_PHASE)
            val shieldedForm = Condition.Custom { npc, _ -> npc.visType.id == flyingType }
            val groundedForm = Condition.Custom { npc, _ -> npc.visType.id == groundedType }

            val tendrilHit =
                whenever(
                    TargetPraying(Melee),
                    hit {
                        damage(0..VINE_PRAYED_MAX).roll()
                        type(Typeless)
                        hazard()
                    },
                    hit {
                        damage(0..VINE_MAX).roll()
                        type(Typeless)
                        hazard()
                    },
                )

            val behindBody =
                playersIn(
                    area(
                        customTile { npc, target -> GuardianArena.behindSw(npc, target) },
                        customTile { npc, target -> GuardianArena.behindNe(npc, target) },
                    )
                )

            val ranged =
                ability(
                    "ranged",
                    sequence(
                        onEach(Focus, faceTarget()),
                        anim("seq.npc_archive_guardian_range_attack01"),
                        spotanim("spotanim.vfx_guardian_ranged_spotanim01"),
                        onEach(
                            Focus,
                            onEach(
                                FacingQuadrant(GuardianArena.REACH),
                                projectile(
                                    spotanim = "spotanim.vfx_guardian_ranged_projanim01",
                                    config = ProjectileConfig.fixed(0, 0, 48, 68, 127),
                                    impactRounding = ImpactRounding.Up,
                                    hit =
                                        hit {
                                            damage(0..25).roll()
                                            type(Ranged)
                                            penetration(PRAYER_PENETRATION)
                                            spotanim("spotanim.vfx_guardian_ranged_hitanim01", slot = 2)
                                        },
                                ),
                            ),
                        ),
                        onEach(Focus, onEach(behindBody, tendrilHit)),
                    ),
                )

            val burst =
                sequence(
                    replaceLoc(CurrentTile, "loc.tfa_guardian_mushroom_despawn"),
                    after(MUSHROOM_REMOVE_DELAY, removeLoc(CurrentTile)),
                    onEach(
                        AllInRadius(2, CurrentTile),
                        whenever(
                            targetWithin(1, CurrentTile),
                            sequence(
                                hit {
                                    damage(Fixed(MUSHROOM_DAMAGE))
                                    type(Typeless)
                                    hazard()
                                },
                                poison(MUSHROOM_POISON),
                                message("You have been poisoned!"),
                                external("guardian.runlock"),
                            ),
                            hit {
                                damage(MUSHROOM_SPLASH_MIN..MUSHROOM_SPLASH_MAX).roll()
                                type(Typeless)
                                hazard()
                            },
                        ),
                    ),
                    untagTile(CurrentTile, RIPE),
                    untagTile(CurrentTile, ARMED),
                )

            val grow =
                sequence(
                    spawnLoc("loc.tfa_guardian_mushroom_spawn", CurrentTile),
                    after(MUSHROOM_RIPEN_DELAY, replaceLoc(CurrentTile, "loc.tfa_guardian_mushroom_active")),
                    after(MUSHROOM_RIPEN_DELAY + 1, tagTile(CurrentTile, RIPE)),
                )

            val mushroomTick =
                whenever(
                    !Condition.InPhase(DEAD),
                    sequence(
                        onTiles(taggedTiles(ARMED), burst),
                        onTiles(
                            taggedTiles(RIPE) - taggedTiles(ARMED),
                            onEach(AllInRadius(1, CurrentTile), tagTile(CurrentTile, ARMED)),
                        ),
                    ),
                )

            val swoop =
                onEach(
                    Focus,
                    whenever(
                        !targetWithin(SWOOP_MIN_DISTANCE - 1),
                        sequence(
                            anim("seq.npc_archive_guardian_swoosh_attack01"),
                            exactMove(customTile(GuardianArena::swoopLanding), SWOOP_BASE_CYCLES, SWOOP_CYCLES_PER_TILE),
                            nextAttackIn(SWOOP_ATTACK_GAP),
                        ),
                    ),
                )

            val special =
                ability(
                    "special",
                    sequence(
                        anim("seq.npc_archive_guardian_special_attack01"),
                        spotanim("spotanim.vfx_guardian_special_attack_spotanim01"),
                        onEach(
                            playersIn(arena),
                            onTiles(
                                customTiles(arena) { _, target, random ->
                                    listOfNotNull(
                                        GuardianArena.ringTile(target.coords, 1, random, lines),
                                        GuardianArena.ringTile(target.coords, 3, random, lines),
                                    )
                                },
                                grow,
                            ),
                        ),
                        after(SWOOP_DELAY, swoop),
                    ),
                )

            val melee =
                ability(
                    "melee",
                    sequence(
                        anim("seq.npc_archive_guardian_melee_attack01"),
                        onEach(
                            playersIn(area(Self, offset(Self, MELEE_SPAN, MELEE_SPAN))),
                            hit {
                                damage(0..MELEE_MAX).roll()
                                type(Melee)
                                penetration(MELEE_PENETRATION)
                            },
                        ),
                    ),
                )

            val underBody =
                Condition.Custom { npc, _ ->
                    deps.encounterRegistry.of(npc).focus?.let { GuardianArena.isUnder(npc, it) } == true
                }

            val beamBattery =
                customNpcs { boss, target, owned ->
                    listOfNotNull(GuardianArena.beamBattery(boss, target, owned, batteryActive))
                }

            val batteryOnBeam =
                Condition.Custom { npc, target ->
                    target != null &&
                        GuardianArena.beamBattery(
                            npc,
                            target,
                            deps.encounterRegistry.of(npc).ownedNpcList(),
                            batteryActive,
                        ) != null
                }

            val breakBattery =
                sequence(
                    after(1, anim("seq.guthixian_battery_despawn")),
                    after(3, transmog("npc.tfa_guardian_battery_destroyed", Int.MAX_VALUE)),
                    camShake(CamShakeAxis.LEFT_RIGHT, 5, 0, 0),
                    after(CAM_SHAKE_TICKS, camReset(playersIn(arena)), requireAlive = false),
                )
            ability("battery_body", breakBattery)
            ability(
                "battery_shield",
                after(
                    BATTERY_SHIELD_DELAY,
                    sequence(damageSelf(varn(BATTERY_HIT)), external("guardian.bar")),
                ),
            )
            ability(
                "battery_break",
                sequence(
                    asNpc(nearestOwned(BATTERY_ACTIVE, CurrentTarget, BATTERY_CHARGE_RADIUS), run("battery_body")),
                    run("battery_shield"),
                ),
            )

            val beam =
                onEach(
                    Focus,
                    sequence(
                        faceTile(CurrentTargetTile, instant = true),
                        whenever(
                            batteryOnBeam,
                            sequence(
                                hit {
                                    damage(Fixed(BEAM_ABSORBED))
                                    type(Typeless)
                                    hazard()
                                },
                                asNpc(beamBattery, sequence(damageSelf(Hp), run("battery_body"))),
                                run("battery_shield"),
                            ),
                            hit {
                                damage(Fixed(BEAM_FULL))
                                type(Typeless)
                                hazard()
                            },
                        ),
                    ),
                )

            ability(
                "enrage_cycle",
                sequence(
                    run("special"),
                    after(
                        FOCUS_DELAY,
                        whenever(
                            enragedNow,
                            sequence(setFocus(randomPlayer(arena)), onEach(Focus, faceTarget())),
                        ),
                    ),
                    after(
                        FOCUS_ANNOUNCE_DELAY,
                        whenever(
                            enragedNow,
                            sequence(
                                anim("seq.npc_archive_guardian_enraged_phase01"),
                                spotanim("spotanim.vfx_guardian_enraged_phase_spotanim01", slot = 2),
                                messageParty(
                                    arena,
                                    "@mes_hl_ora@The Archive Guardian focuses intently on you!",
                                    "@mes_hl_ora@The Archive Guardian focuses intently on {name}!",
                                ),
                            ),
                        ),
                    ),
                    after(BEAM_DELAY, whenever(enragedNow, beam)),
                ),
            )

            val swipe = onEach(AllInRadius(1, Self), tendrilHit)

            val swipeAt: (Int) -> Effect = { delay ->
                after(delay, sequence(anim("seq.npc_guardian_archive_tentacle_attack01"), swipe))
            }

            val vineLife =
                sequence(
                    anim("seq.npc_guardian_archive_tentacle_spawn01"),
                    spotanim("spotanim.vfx_guardian_tentacle_intro_spotanim01"),
                    swipeAt(3),
                    swipeAt(6),
                    swipeAt(9),
                    after(12, anim("seq.npc_guardian_archive_tentacle_despawn01")),
                )

            val vineWave =
                onTiles(
                    customTiles(arena) { npc, _, _ -> GuardianArena.vineTiles(npc, deps.playerList) },
                    summon(
                        "npc.archive_guardian_tentacle",
                        radius = 0,
                        centeredOn = CurrentTile,
                        mode = NpcMode.None,
                        duration = VINE_LIFE_TICKS,
                        owned = true,
                        behaviour = vineLife,
                    ),
                )

            val batteriesActive =
                asNpc(
                    ownedNpcs("npc.tfa_guardian_battery_inactive"),
                    sequence(
                        transmog(BATTERY_ACTIVE, Int.MAX_VALUE),
                        setHp(varn(BATTERY_HP), varn(BATTERY_HP)),
                    ),
                )

            ability(
                "wake",
                sequence(
                    anim("seq.npc_archive_guardian_spawn01"),
                    spotanim("spotanim.vfx_guardian_spawn_spotanim01"),
                    song(WAKE_SONG, arena),
                    nextAttackIn(WAKE_FIRST_ATTACK),
                    after(
                        WAKE_TRANSFORM_DELAY,
                        sequence(
                            transitionTo(flyingPhase(1)),
                            anim("seq.npc_archive_guardian_idle01"),
                            setHp(shieldMax, shieldMax),
                            setFocus(randomPlayer(arena)),
                            batteriesActive,
                            external("guardian.bar"),
                        ),
                    ),
                ),
            )

            val takeOffFx =
                sequence(
                    setVarn(HEALTH, Hp),
                    anim("seq.npc_archive_guardian_ground_to_flying01"),
                    spotanim("spotanim.vfx_guardian_groundtoidle_spotanim01"),
                )

            ability(
                "take_off",
                sequence(
                    takeOffFx,
                    setHp(shieldMax, shieldMax),
                    setFocus(randomPlayer(arena)),
                    nextAttackIn(TAKE_OFF_FIRST_ATTACK),
                    whenever(varnIs(ENRAGED, 1), transitionTo(ENRAGED_PHASE)),
                    external("guardian.bar"),
                ),
            )

            ability(
                "enrage_takeoff",
                sequence(
                    setVarn(HEALTH, Hp),
                    transitionTo(ENRAGED_PHASE),
                    anim("seq.npc_archive_guardian_ground_to_flying01"),
                    spotanim("spotanim.vfx_guardian_groundtoidle_spotanim01"),
                    setHp(shieldMax, shieldMax),
                    clearFocus(),
                    external("guardian.bar"),
                ),
            )

            val landings = (1..LANDINGS_MAX).map { landing ->
                val name = "land_$landing"
                ability(
                    name,
                    sequence(
                        transitionTo(landedPhase(landing)),
                        anim("seq.npc_archive_guardian_idle_to_ground01"),
                        spotanim("spotanim.vfx_guardian_idletoground_spotanim01"),
                        mapSpotanim("spotanim.vfx_guardian_idletoground_spotanim01", Centre),
                        setHp(varn(HEALTH), groundMax),
                        addVarn(LANDINGS, 1),
                        clearFocus(),
                        external("guardian.bar"),
                    ),
                )
                run(name)
            }

            ability(
                "enrage_ground",
                after(
                    BREAK_DELAY,
                    sequence(
                        transmog(GROUNDED, Int.MAX_VALUE),
                        setHp(varn(HEALTH), groundMax),
                        external("guardian.bar"),
                    ),
                ),
            )

            ability(
                "break_shield",
                sequence(
                    interrupt(),
                    transitionTo(BREAKING),
                    after(BREAK_DELAY, switch(LANDINGS, landings, otherwise = landings.last())),
                ),
            )

            ability(
                "die",
                sequence(
                    interrupt(),
                    transitionTo(DEAD),
                    external("guardian.dead"),
                    anim("seq.npc_archive_guardian_death01"),
                    spotanim("spotanim.vfx_ocellus_null", height = DEATH_SPOT_HEIGHT, slot = 2),
                    after(
                        DEATH_CORPSE_DELAY,
                        sequence(
                            transmog("npc.tfa_guardian_dead", Int.MAX_VALUE),
                            asNpc(ownedNpcs(), despawnSelf()),
                            onTiles(
                                ownedLocs("loc.tfa_guardian_mushroom_spawn") +
                                    ownedLocs("loc.tfa_guardian_mushroom_active") +
                                    ownedLocs("loc.tfa_guardian_mushroom_despawn"),
                                removeLoc(CurrentTile),
                            ),
                            external("guardian.complete"),
                        ),
                    ),
                ),
            )

            phase(INERT) {}
            for (landing in 1..LANDINGS_MAX) {
                phase(
                    flyingPhase(landing),
                    transmog = FLYING,
                    lockMovement = true,
                    scriptedMovement = true,
                    interactUnderTarget = true,
                ) {
                    if (landing > 1) entry = "take_off"
                    weightedSelectorRandom { +random(ranged, weight = 1) }
                    if (landing == 1) {
                        forceEveryAttacks(FIRST_SPECIAL_MIN, FIRST_SPECIAL_MAX, special)
                    } else {
                        forceEveryAttacks(LATER_SPECIAL_MIN, LATER_SPECIAL_MAX, special)
                    }
                    forceWhen(underBody, melee)
                    every(1, whenever(Condition.HpExact(1), run("break_shield")))
                }
                phase(
                    landedPhase(landing),
                    transmog = GROUNDED,
                    lockMovement = true,
                    scriptedMovement = true,
                    interactUnderTarget = true,
                    idleAnim = "seq.npc_archive_guardian_ground_idle01",
                    exitAfter = LANDING_CAP + LANDING_CAP_STEP * (landing - 1),
                    nextPhase = flyingPhase(minOf(landing + 1, LANDINGS_MAX)),
                ) {
                    every(VINE_INTERVAL, vineWave, firstAfter = VINE_FIRST)
                    every(1, whenever(Condition.HpExact(1), run("die")))
                }
            }
            phase(BREAKING, lockMovement = true, scriptedMovement = true) {}
            phase(
                ENRAGED_PHASE,
                transmog = FLYING,
                lockMovement = true,
                scriptedMovement = true,
                interactUnderTarget = true,
            ) {
                every(ENRAGE_INTERVAL, run("enrage_cycle"), firstAfter = ENRAGE_FIRST)
            }
            phase(DEAD, lockMovement = true, scriptedMovement = true) {}

            every(1, mushroomTick)

            triggers {
                on(landedAny and HpBelow(ENRAGE_FRACTION)) runs
                    sequence(
                        setVarn(ENRAGED, 1),
                        after(ENRAGE_TAKEOFF_DELAY, whenever(landedAny, run("enrage_takeoff"))),
                    )
                on(enragedNow and shieldedForm and Condition.HpExact(1)) runs run("enrage_ground")
                on(enragedNow and groundedForm and Condition.HpExact(1)) runs run("die")
            }
        }

    private companion object {
        const val START = "npc.tfa_guardian_start"
        const val FLYING = "npc.tfa_guardian"
        const val GROUNDED = "npc.tfa_guardian_unshielded"
        const val BATTERY_ACTIVE = "npc.tfa_guardian_battery_active"

        const val INERT = "inert"
        const val BREAKING = "breaking"
        const val ENRAGED_PHASE = "enraged"
        const val DEAD = "dead"
        const val RIPE = "ripe"
        const val ARMED = "armed"

        const val LANDINGS_MAX = 6
        const val ATTACK_RATE = 5
        const val WAKE_SONG = 887
        const val WAKE_FIRST_ATTACK = 9
        const val WAKE_TRANSFORM_DELAY = 5
        const val TAKE_OFF_FIRST_ATTACK = 5
        const val BREAK_DELAY = 3
        const val FIRST_SPECIAL_MIN = 4
        const val FIRST_SPECIAL_MAX = 6
        const val LATER_SPECIAL_MIN = 2
        const val LATER_SPECIAL_MAX = 5
        const val PRAYER_PENETRATION = 40

        const val LANDING_CAP = 35
        const val LANDING_CAP_STEP = 6
        const val ENRAGE_FRACTION = 1.0 / 3
        const val ENRAGE_TAKEOFF_DELAY = 3
        const val ENRAGE_INTERVAL = 20
        const val ENRAGE_FIRST = 5
        const val FOCUS_DELAY = 3
        const val FOCUS_ANNOUNCE_DELAY = 5
        const val BEAM_DELAY = 15
        const val BEAM_ABSORBED = 10
        const val BEAM_FULL = 60
        const val BATTERY_CHARGE_RADIUS = 40
        const val BATTERY_SHIELD_DELAY = 4
        const val CAM_SHAKE_TICKS = 5

        const val VINE_FIRST = 5
        const val VINE_INTERVAL = 3
        const val VINE_LIFE_TICKS = 13
        const val VINE_MAX = 20
        const val VINE_PRAYED_MAX = 10

        const val MUSHROOM_RIPEN_DELAY = 3
        const val MUSHROOM_REMOVE_DELAY = 2
        const val MUSHROOM_DAMAGE = 10
        const val MUSHROOM_POISON = 10
        const val MUSHROOM_SPLASH_MIN = 3
        const val MUSHROOM_SPLASH_MAX = 4

        const val SWOOP_DELAY = 4
        const val SWOOP_MIN_DISTANCE = 9
        const val SWOOP_BASE_CYCLES = 30
        const val SWOOP_CYCLES_PER_TILE = 2
        const val SWOOP_ATTACK_GAP = 3

        const val MELEE_SPAN = 4
        const val MELEE_MAX = 25
        const val MELEE_PENETRATION = 50

        const val DEATH_SPOT_HEIGHT = 200
        const val DEATH_CORPSE_DELAY = 7

        fun flyingPhase(landing: Int) = "flying_$landing"

        fun landedPhase(landing: Int) = "landed_$landing"

        fun landedPhases() = (1..LANDINGS_MAX).map(::landedPhase)
    }
}
