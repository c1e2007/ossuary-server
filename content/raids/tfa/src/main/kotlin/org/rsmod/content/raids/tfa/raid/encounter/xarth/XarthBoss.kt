package org.rsmod.content.raids.tfa.raid.encounter.xarth

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
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.DEAD
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.FIRST_HEAP
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.HEALED
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.HEAL_PENDING
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.HEAPS
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.LAST_HIT
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.MAGICS
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.SLOT
import org.rsmod.content.raids.tfa.raid.encounter.xarth.XarthVarns.WAVE
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class XarthBoss @Inject constructor(deps: BossDeps, private val fights: XarthEncounter) :
    BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onHit = { fights.of(npc)?.updateBar() })
    }

    override val spec: BossSpec =
        boss(BOSS) {
            stats(attackRate = 1)

            val arena = XarthArena.AREA
            val xarth = customNpcs { boss, _, _ -> listOf(boss) }

            fun corpses(owned: List<Npc>) = owned.filter { it.visType.id in CORPSE_IDS }

            fun adjacent(boss: Npc, corpse: Npc) =
                XarthArena.adjacentToFootprint(boss.spawnCoords, corpse.coords)

            val marching =
                customNpcs { boss, _, owned ->
                    corpses(owned).filter {
                        !it.movementLocked && !CombatEffects.isFrozen(it) && !adjacent(boss, it)
                    }
                }
            val arrived =
                customNpcs { boss, _, owned ->
                    corpses(owned).filter { !it.movementLocked && adjacent(boss, it) }
                }
            val dying =
                customNpcs { _, _, owned ->
                    corpses(owned).filter { !it.movementLocked && it.hitpoints <= 1 }
                }
            val everyCorpse = customNpcs { _, _, owned -> corpses(owned) }

            fun closestWithinReach(npc: Npc): Boolean {
                val origin = npc.spawnCoords
                val nearest =
                    XarthArena.players(origin, deps.playerList).minOfOrNull {
                        XarthArena.footprintDistance(origin, it.coords)
                    }
                return nearest != null && nearest <= MELEE_REACH
            }

            // ---- magic, ranged, melee ----------------------------------------------------------

            val magic =
                sequence(
                    anim("seq.xarth_attack_magic_01"),
                    onEach(
                        randomPlayer(arena),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.luc2_undead_demon_fireball_proj_big",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        MAGIC_DELAY,
                                        FLIGHT,
                                        MAGIC_ANGLE,
                                        MAGIC_PROGRESS,
                                    ),
                                impact = "spotanim.luc2_undead_demon_fireball_impact",
                                hit =
                                    hit {
                                        damage(0..XarthArena.MAGIC_MAX).roll()
                                        type(Magic)
                                        onHit(
                                            sequence(
                                                setVarn(LAST_HIT, HitDamage),
                                                whenever(
                                                    varnAtLeast(LAST_HIT, 1),
                                                    external("xarth.ignite"),
                                                ),
                                            )
                                        )
                                    },
                            ),
                        ),
                    ),
                )

            val ranged =
                sequence(
                    anim("seq.xarth_attack_ranged_01"),
                    spotanim("spotanim.vfx_xarth_attack_ranged_spotanim_01", slot = 2),
                    onEach(
                        nearestPlayer(arena),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.vfx_xarth_attack_ranged_projectile_01",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        RANGED_DELAY,
                                        FLIGHT,
                                        RANGED_ANGLE,
                                        RANGED_PROGRESS,
                                    ),
                                impact = "spotanim.vfx_xarth_attack_ranged_hitanim_01",
                                hit =
                                    hit {
                                        damage(0..XarthArena.RANGED_MAX).roll()
                                        type(Ranged)
                                    },
                            ),
                        ),
                    ),
                )

            val melee =
                sequence(
                    anim("seq.xarth_attack_melee_01"),
                    spotanim("spotanim.luc2_undead_demon_melee_spot"),
                    onEach(
                        nearestPlayer(arena),
                        sequence(
                            faceTarget(),
                            hit {
                                damage(0..XarthArena.MELEE_MAX).roll()
                                type(Melee)
                            },
                            external("xarth.bleed"),
                        ),
                    ),
                )

            val rangedOrMelee =
                whenever(Condition.Custom { npc, _ -> closestWithinReach(npc) }, melee, ranged)

            // ---- searing AoE --------------------------------------------------------------------

            val aoe =
                sequence(
                    anim("seq.xarth_attack_aoe_01"),
                    onEach(
                        playersIn(arena),
                        parallel(
                            external("xarth.bind"),
                            mapSpotanim(
                                "spotanim.gargboss_debris_shadow_90",
                                CurrentTargetTile,
                                delay = AOE_SHADOW_DELAY,
                            ),
                            projectile(
                                spotanim = "spotanim.vfx_xarth_aoe_projanim_01",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        AOE_END_HEIGHT,
                                        AOE_DELAY,
                                        FLIGHT,
                                        AOE_ANGLE,
                                        AOE_PROGRESS,
                                    ),
                                target = CurrentTargetTile,
                                impactLead = 1,
                                onImpact =
                                    sequence(
                                        mapSpotanim("spotanim.vfx_xarth_aoe_hitanim_01", ImpactTile),
                                        onEach(
                                            playersOn(ImpactTile),
                                            sequence(
                                                hit {
                                                    damage(XarthArena.AOE_MIN..XarthArena.AOE_MAX)
                                                        .roll()
                                                    type(Typeless)
                                                    hazard()
                                                },
                                                external("xarth.ignite"),
                                            ),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                )

            // ---- the wave -----------------------------------------------------------------------

            fun collapse(heap: Int): Effect {
                val sw = XarthArena.heapSwOffset(heap)
                val at = spawnTile(sw.first, sw.second)
                return sequence(
                    asNpc(nearestOwned(RUBBLE, at, 1), despawnSelf()),
                    summon(
                        DESTROYED,
                        radius = 0,
                        centeredOn = at,
                        mode = NpcMode.None,
                        duration = COLLAPSE_TICKS,
                        owned = true,
                        behaviour =
                            sequence(
                                anim("seq.loc_xarth_rubble_collapse_01"),
                                spotanim("spotanim.vfx_xarth_rubble_collapse_01"),
                            ),
                    ),
                    replaceLoc(at, HEAP_FALLEN),
                    after(COLLAPSE_TICKS, replaceLoc(at, HEAP_GONE)),
                    setVarn(HEAPS, varn(HEAPS) + (1 shl heap)),
                )
            }

            fun coverOf(npc: Npc, shape: Int, target: Player?): Cover? {
                if (target == null) return null
                val origin = npc.spawnCoords
                if (!XarthArena.inWave(origin, shape, target.coords)) return null
                val pillars =
                    if (PLAYER_COVER) {
                        XarthArena.players(origin, deps.playerList)
                            .filter { it !== target }
                            .map { it.coords }
                    } else {
                        emptyList()
                    }
                return XarthArena.cover(origin, npc.vars[HEAPS], target.coords, pillars)
            }

            fun waveHit(min: Int, max: Int) =
                sequence(
                    hit {
                        damage(min..max).roll()
                        type(Typeless)
                        hazard()
                    },
                    external("xarth.ignite_wave"),
                )

            fun wave(shape: Int, heap: Int?, finish: Effect): Effect {
                val struck =
                    customTiles(arena) { npc, _, _ ->
                        val origin = npc.spawnCoords
                        val fallen = npc.vars[HEAPS]
                        XarthArena.waveTiles(origin, shape).filter {
                            !XarthArena.umbra(origin, fallen, it) &&
                                !XarthArena.smallerExplosion(origin, fallen, it)
                        }
                    }
                val smaller =
                    customTiles(arena) { npc, _, _ ->
                        val origin = npc.spawnCoords
                        val fallen = npc.vars[HEAPS]
                        XarthArena.waveTiles(origin, shape).filter {
                            XarthArena.smallerExplosion(origin, fallen, it)
                        }
                    }
                return sequence(
                    onTiles(
                        struck,
                        mapSpotanim(
                            "spotanim.vfx_vmq4_ennius_fire_explosion",
                            CurrentTile,
                            delay = distanceToFootprint(CurrentTile),
                        ),
                    ),
                    onTiles(
                        smaller,
                        mapSpotanim(
                            "spotanim.vfx_vmq4_ennius_fire_explosion_smaller",
                            CurrentTile,
                            delay = distanceToFootprint(CurrentTile),
                        ),
                    ),
                    onEach(
                        playersIn(arena),
                        whenever(
                            Condition.Custom { npc, target -> coverOf(npc, shape, target) == Cover.NONE },
                            waveHit(XarthArena.WAVE_MIN, XarthArena.WAVE_MAX),
                            whenever(
                                Condition.Custom { npc, target ->
                                    coverOf(npc, shape, target) == Cover.PARTIAL
                                },
                                waveHit(XarthArena.WAVE_PARTIAL_MIN, XarthArena.WAVE_PARTIAL_MAX),
                            ),
                        ),
                    ),
                    if (heap != null) collapse(heap) else Effect.NoOp,
                    finish,
                )
            }

            fun blast(landing: Effect) =
                sequence(
                    anim("seq.xarth_attack_flame_blast_01", delay = BLAST_DELAY),
                    spotanim(
                        "spotanim.vfx_xarth_attack_flame_blast_01",
                        delay = BLAST_DELAY,
                        slot = BLAST_SLOT,
                    ),
                    after(BLAST_TO_WAVE, landing),
                )

            fun phaseOneWave(index: Int, first: Int): Effect {
                val heap = XarthArena.heapForWave(index, first)
                val done =
                    if (index == LAST_WAVE) {
                        run("p2_start")
                    } else {
                        addVarn(WAVE, 1)
                    }
                return wave(XarthArena.shapeForWave(index, first), heap, done)
            }

            val waveLanding =
                switch(
                    WAVE,
                    (0..LAST_WAVE).map { index ->
                        switch(
                            FIRST_HEAP,
                            XarthArena.HEAP_NORTH to phaseOneWave(index, XarthArena.HEAP_NORTH),
                            XarthArena.HEAP_SOUTH to phaseOneWave(index, XarthArena.HEAP_SOUTH),
                        )
                    },
                )

            // ---- phase one ----------------------------------------------------------------------

            ability(
                "p1_cycle",
                sequence(
                    magic,
                    after(MAGIC_GAP, magic),
                    after(MAGIC_GAP * 2, magic),
                    after(MAGIC_GAP * 3, magic),
                    after(MAGIC_GAP * 4, magic),
                    after(AOE_AT, aoe),
                    after(BLAST_AT, blast(waveLanding)),
                    nextAttackIn(P1_CYCLE),
                ),
            )

            ability(
                "begin",
                sequence(
                    song(MUSIC, arena),
                    setHp(varn(HP_MAX), varn(HP_MAX)),
                    setVarn(WAVE, 0),
                    setVarn(HEAPS, 0),
                    setVarn(SLOT, 0),
                    setVarn(HEALED, 0),
                    setVarn(DEAD, 0),
                    oneOf(
                        setVarn(FIRST_HEAP, XarthArena.HEAP_NORTH),
                        setVarn(FIRST_HEAP, XarthArena.HEAP_SOUTH),
                    ),
                    nextAttackIn(FIRST_ATTACK),
                    external("xarth.bar"),
                ),
            )

            // ---- phase two ----------------------------------------------------------------------

            val magicCorpse =
                withTile(
                    "corpse",
                    customTile { npc, _ -> XarthArena.edgeTile(npc.spawnCoords, deps.random) },
                    sequence(
                        anim("seq.xarth_attack_magic_01"),
                        faceTile(tile("corpse"), instant = true),
                        projectile(
                            spotanim = "spotanim.luc2_undead_demon_fireball_proj_big",
                            config =
                                ProjectileConfig.fixed(
                                    PROJ_START_HEIGHT,
                                    PROJ_END_HEIGHT,
                                    MAGIC_DELAY,
                                    FLIGHT,
                                    MAGIC_ANGLE,
                                    MAGIC_PROGRESS,
                                ),
                            target = tile("corpse"),
                        ),
                        after(
                            CORPSE_RISES,
                            summon(
                                CORPSE,
                                radius = 0,
                                centeredOn = tile("corpse"),
                                mode = NpcMode.None,
                                duration = Int.MAX_VALUE,
                                owned = true,
                                behaviour =
                                    sequence(
                                        retire(),
                                        transparency(0, CORPSE_FADE_CYCLES, TRANSPARENT, 0),
                                        spotanim("spotanim.vfx_xarth_corpse_walk_fire_01", slot = 2),
                                    ),
                            ),
                        ),
                    ),
                )

            fun slots(magics: Int): List<Effect> {
                val count = XarthArena.slotCount(magics)
                return (0 until count).map { slot ->
                    sequence(
                        if (slot < magics) magicCorpse else rangedOrMelee,
                        nextAttackIn(XarthArena.slotGap(magics, slot)),
                        setVarn(SLOT, if (slot == count - 1) 0 else slot + 1),
                    )
                }
            }

            ability(
                "p2_slot",
                whenever(
                    varnAtLeast(HEALED, XarthArena.HEAL_THRESHOLD),
                    run("heal_blast"),
                    switch(
                        MAGICS,
                        3 to switch(SLOT, slots(3)),
                        4 to switch(SLOT, slots(4)),
                        otherwise = switch(SLOT, slots(2)),
                    ),
                ),
            )

            ability(
                "heal_blast",
                sequence(
                    setVarn(HEALED, 0),
                    blast(wave(XarthArena.ALL_ROUND, null, Effect.NoOp)),
                    nextAttackIn(XarthArena.BLAST_PAUSE),
                ),
            )

            val fireSpread =
                onTiles(
                    takeTiles(ownedLocs(FIRE), XarthArena.SPREAD_FIRES),
                    onTiles(
                        takeTiles(
                            customTilesAt(arena, CurrentTile) { npc, at, _ ->
                                XarthArena.neighbours(npc.spawnCoords, at)
                            },
                            1,
                        ),
                        spawnLoc(FIRE, CurrentTile),
                    ),
                )

            ability(
                "p2_open",
                sequence(
                    setVarn(SLOT, 0),
                    setVarn(HEALED, 0),
                    onTiles(
                        customTiles(arena) { npc, _, random ->
                            XarthArena.initialFires(npc.spawnCoords, random, XarthArena.INITIAL_FIRES)
                        },
                        spawnLoc(FIRE, CurrentTile),
                    ),
                    external("xarth.piles"),
                    after(P2_FIRST_RANGED, rangedOrMelee),
                    after(P2_SECOND_RANGED, rangedOrMelee),
                    nextAttackIn(P2_FIRST_CYCLE),
                ),
            )

            ability("p2_start", sequence(transitionTo(P2), run("p2_open")))

            // ---- corpses ------------------------------------------------------------------------

            val march =
                walkTo(
                    customTile { npc, _ ->
                        val origin = fights.at(npc.coords)?.origin
                        if (origin == null) npc.coords else XarthArena.stepTowardsFootprint(origin, npc.coords)
                    }
                )

            val absorb =
                sequence(
                    lockMovement(),
                    anim("seq.xarth_corpse_absorbed_01"),
                    spotanim("spotanim.vfx_xarth_corpse_absorbed_01"),
                    after(
                        1,
                        sequence(
                            setVarn(HEAL_PENDING, Hp + Hp),
                            asNpc(
                                xarth,
                                sequence(
                                    spotanim("spotanim.vfx_xarth_absorb_corpse_fire_01"),
                                    healSelf(varn(HEAL_PENDING)),
                                    setVarn(HEALED, varn(HEALED) + varn(HEAL_PENDING)),
                                    external("xarth.bar"),
                                ),
                            ),
                        ),
                    ),
                    after(ABSORB_REMOVAL, despawnSelf()),
                )

            val corpseDie =
                sequence(
                    lockMovement(),
                    anim("seq.xarth_corpse_death_01"),
                    spotanim("spotanim.vfx_xarth_corpse_death_01"),
                    spotanim("spotanim.vfx_xarth_corpse_walk_doused_01", slot = 2),
                    after(CORPSE_DEATH_REMOVAL, despawnSelf()),
                )

            // ---- death --------------------------------------------------------------------------

            ability(
                "die",
                sequence(
                    interrupt(),
                    retire(),
                    transitionTo(DEAD_PHASE),
                    setVarn(DEAD, 1),
                    anim("seq.xarth_death_01"),
                    spotanim("spotanim.vfx_xarth_death_01"),
                    external("xarth.bar_close"),
                    asNpc(everyCorpse, sequence(lockMovement(), after(DEATH_TICKS, despawnSelf()))),
                    after(DEATH_TICKS, run("complete")),
                ),
            )

            ability(
                "complete",
                sequence(setVarn(DONE, 1), external("xarth.complete"), despawnSelf()),
            )

            phase(P1, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("p1_cycle", weight = 1) }
            }
            phase(P2, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("p2_slot", weight = 1) }
                every(XarthArena.SPREAD_PERIOD, fireSpread, firstAfter = XarthArena.FIRST_SPREAD)
            }
            phase(DEAD_PHASE, scriptedMovement = true, lockMovement = true) {}

            every(1, asNpc(dying, corpseDie))
            every(1, asNpc(marching, march))
            every(1, asNpc(arrived, absorb))
            every(1, whenever(Condition.HpExact(1) and !varnIs(DEAD, 1), run("die")))
            every(
                1,
                onTiles(ownedLocs(FIRE), onEach(playersOn(CurrentTile), external("xarth.ignite"))),
            )
        }

    private companion object {
        const val BOSS = "npc.tfa_xarth"
        const val CORPSE = "npc.tfa_xarth_corpse"
        const val RUBBLE = "npc.tfa_xarth_rubble_npc"
        const val DESTROYED = "npc.tfa_xarth_rubble_npc_destroyed"
        const val FIRE = "loc.tfa_xarth_arenafire"
        const val HEAP_FALLEN = "loc.invisible_type8_blockrange_size2"
        const val HEAP_GONE = "loc.invisible_type8_nonblocking"

        const val P1 = "p1"
        const val P2 = "p2"
        const val DEAD_PHASE = "dead"

        const val MUSIC = 896
        const val PLAYER_COVER = true
        const val MELEE_REACH = 1
        const val LAST_WAVE = 2

        const val PROJ_START_HEIGHT = 470
        const val PROJ_END_HEIGHT = 120
        const val AOE_END_HEIGHT = 0
        const val FLIGHT = 60
        const val MAGIC_DELAY = 30
        const val MAGIC_ANGLE = 0
        const val MAGIC_PROGRESS = 160
        const val RANGED_DELAY = 60
        const val RANGED_ANGLE = 10
        const val RANGED_PROGRESS = 200
        const val AOE_DELAY = 60
        const val AOE_ANGLE = 30
        const val AOE_PROGRESS = 128
        const val AOE_SHADOW_DELAY = 30

        const val FIRST_ATTACK = 6
        const val MAGIC_GAP = 4
        const val AOE_AT = 20
        const val BLAST_AT = 23
        const val BLAST_TO_WAVE = 3
        const val P1_CYCLE = 31
        const val BLAST_DELAY = 30
        const val BLAST_SLOT = 5
        const val COLLAPSE_TICKS = 3

        const val P2_FIRST_RANGED = 7
        const val P2_SECOND_RANGED = 11
        const val P2_FIRST_CYCLE = 15
        const val CORPSE_RISES = 3
        const val CORPSE_FADE_CYCLES = 30
        const val TRANSPARENT = 255
        const val ABSORB_REMOVAL = 3
        const val CORPSE_DEATH_REMOVAL = 4
        const val DEATH_TICKS = 6
    }
}
