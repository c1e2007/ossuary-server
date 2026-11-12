package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spotanimMapPrivate
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid

private var Player.protectFromMelee by intVarBit("varbit.prayer_protectfrommelee")

internal class CrystalPatrol(
    val npc: Npc,
    val ends: List<ArenaTile>,
    val fireDirection: Pair<Int, Int>,
    val charge: String,
    val attack: String,
) {
    var end: Int = 0
    var busyUntil: Int = 0
}

internal class SolFight(val npc: Npc) {
    var phase: Int = 0
    var forced: String? = "spear"
    var last: String? = null
    var second: Boolean = false
    var specialCooldown: Int = 0
    var nextAttackAt: Int = 0
    var laserCooldown: Int = ColosseumSol.LASER_MIN
    var enragedSandIn: Int = ColosseumSol.SAND_ENRAGED_EVERY
    val crystals: MutableList<CrystalPatrol> = mutableListOf()
}

@Singleton
internal class ColosseumSol
@Inject
constructor(
    private val runs: ColosseumRuns,
    private val deps: BossDeps,
    private val locRepo: LocRepository,
    private val rewards: ColosseumRewards,
) {
    fun begin(run: ColosseumRun) {
        run.stage = RunStage.SOL
        run.cleanup += { clear(run) }
        run.player.mes("Sol Heredit jumps down from his seat...")
        deps.worldQueues.add(LAND_DELAY) { land(run) }
    }

    private fun land(run: ColosseumRun) {
        if (run.stage != RunStage.SOL) return
        run.sol?.let { runs.remove(it) }
        run.sol = null
        run.barricade += ColosseumSolPatterns.barricade().mapIndexed { index, (x, z, rotation) ->
            val loc = BARRICADE_LOCS[index % BARRICADE_LOCS.size]
            locRepo.add(run.tile(x, z), loc, Int.MAX_VALUE, angleOf(rotation), LocShape.CentrepieceStraight)
        }
        run.player.coords = run.tile(PLAYER_START.x, PLAYER_START.z)
        val npc = runs.spawn(run, ColosseumNpcs.SOL, run.tile(LANDING.x, LANDING.z)) ?: return
        npc.anim("seq.npc_colossi_finalboss_01_arena_land")
        npc.spotanim("spotanim.npc_colossi_colossi_01_land")
        freeze(npc, LANDING_FREEZE)
        val fight = SolFight(npc)
        fight.nextAttackAt = run.waveTicks + LANDING_FREEZE
        run.fight = fight
        run.npcs += npc
        runs.engage(run, npc)
        npc.say(PHASE_LINES[0])
        run.stage = RunStage.WAVE
        run.waveTicks = 0
        fight.nextAttackAt = LANDING_FREEZE
    }

    fun tick(run: ColosseumRun) {
        val fight = run.fight ?: return
        val sol = fight.npc
        if (sol.hitpoints <= 0 || !sol.isSlotAssigned) {
            defeated(run)
            return
        }
        while (fight.phase + 1 < PHASE_HP.size && sol.hitpoints <= PHASE_HP[fight.phase + 1]) {
            fight.phase++
            sol.say(PHASE_LINES[fight.phase])
            transition(run, fight)
        }
        if (fight.phase == ENRAGED && --fight.enragedSandIn <= 0) {
            fight.enragedSandIn = SAND_ENRAGED_EVERY
            val targets =
                ColosseumSolPatterns.sandTargets(run.playerTile(), 1, run.random) { run.sand.containsKey(it) }
            placeSand(run, targets)
        }
        for (crystal in fight.crystals) patrol(run, crystal)
        if (fight.crystals.isNotEmpty() && --fight.laserCooldown <= 0) {
            for (crystal in fight.crystals) fireCrystal(run, fight, crystal)
            fight.laserCooldown =
                if (fight.phase == ENRAGED) LASER_ENRAGED else run.random.nextInt(LASER_MIN, LASER_MAX + 1)
        }
        if (run.waveTicks >= fight.nextAttackAt && besidePlayer(run, sol)) {
            fight.nextAttackAt = run.waveTicks + attack(run, fight)
        }
    }

    private fun besidePlayer(run: ColosseumRun, sol: Npc): Boolean {
        val player = run.playerTile()
        val origin = arenaTileOf(run, sol)
        val closestX = player.x.coerceIn(origin.x, origin.x + ColosseumSolPatterns.SIZE - 1)
        val closestZ = player.z.coerceIn(origin.z, origin.z + ColosseumSolPatterns.SIZE - 1)
        return ColosseumSolPatterns.chebyshev(player, ArenaTile(closestX, closestZ)) <= 1
    }

    private fun arenaTileOf(run: ColosseumRun, npc: Npc): ArenaTile =
        ArenaTile(
            npc.coords.x - run.tile(Colosseum.ARENA_BASE_X, Colosseum.ARENA_BASE_Z).x + Colosseum.ARENA_BASE_X,
            npc.coords.z - run.tile(Colosseum.ARENA_BASE_X, Colosseum.ARENA_BASE_Z).z + Colosseum.ARENA_BASE_Z,
        )

    private fun nextAttack(run: ColosseumRun, fight: SolFight): String {
        fight.forced?.let { return it }
        val pool = mutableListOf("spear", "spear", "shield", "shield")
        if (fight.specialCooldown <= 0) {
            if (fight.phase >= 1) pool += "triple"
            if (fight.phase >= 2) pool += "grapple"
        }
        return pool.random(run.random)
    }

    private fun attack(run: ColosseumRun, fight: SolFight): Int {
        val choice = nextAttack(run, fight)
        fight.forced = null
        if (choice == "spear" || choice == "shield") {
            fight.specialCooldown--
            return aoe(run, fight, choice)
        }
        fight.specialCooldown = SPECIAL_COOLDOWN
        fight.second = false
        fight.last = null
        return if (choice == "triple") triple(run, fight) else grapple(run, fight)
    }

    private fun aoe(run: ColosseumRun, fight: SolFight, style: String): Int {
        val second = fight.last == style && !fight.second
        fight.second = second
        fight.last = style
        val npc = fight.npc
        npc.anim(if (style == "spear") "seq.npc_colossi_finalboss_01_melee_attack_telegraph" else "seq.npc_colossi_finalboss_01_shieldslam_telegraph")
        freeze(npc, AOE_FROZEN)
        val sol = arenaTileOf(run, npc)
        deps.worldQueues.add(AOE_LANDS_AFTER) {
            if (run.stage != RunStage.WAVE || npc.hitpoints <= 0) return@add
            val tiles =
                if (style == "spear") {
                    ColosseumSolPatterns.spear(sol, run.playerTile(), second)
                } else {
                    ColosseumSolPatterns.shield(sol, second)
                }
            smoke(run, tiles)
            val at = run.playerTile()
            if (tiles.any { it.x == at.x && it.z == at.z }) {
                hurt(run, run.random.nextInt(1, AOE_MAX + 1) + ColosseumEffects.maxHitBonus(run))
            }
        }
        val faster = if (fight.phase >= 2) 1 else 0
        return (if (style == "spear") SPEED_SPEAR else SPEED_SHIELD) - faster
    }

    private fun triple(run: ColosseumRun, fight: SolFight): Int {
        val late = fight.phase >= 3
        fight.npc.anim(if (late) "seq.npc_colossi_finalboss_tripleattack" else "seq.npc_colossi_finalboss_tripleattack_shorter")
        fight.npc.spotanim(if (late) "spotanim.npc_colossi_colossi_tripleattack_01_telegraph" else "spotanim.npc_colossi_colossi_tripleattack_01_telegraph_shorter")
        val damage = if (late) TRIPLE_LATE else TRIPLE_EARLY
        val hits = if (late) TRIPLE_HITS_LATE else TRIPLE_HITS_EARLY
        val player = run.player
        for ((index, at) in hits.withIndex()) {
            var early = false
            for (before in intArrayOf(at - 2, at - 1)) {
                deps.worldQueues.add(before) {
                    if (run.stage == RunStage.WAVE && player.protectFromMelee != 0) {
                        early = true
                        player.protectFromMelee = 0
                        player.mes("You prayed too early; Sol Heredit's attack ignores it.")
                    }
                }
            }
            deps.worldQueues.add(at) {
                if (run.stage != RunStage.WAVE || fight.npc.hitpoints <= 0) return@add
                if (early || player.protectFromMelee == 0) {
                    hurt(run, damage[index] + ColosseumEffects.maxHitBonus(run))
                }
            }
        }
        return TRIPLE_SPEED - (if (!late && fight.phase >= 2) 1 else 0)
    }

    private fun grapple(run: ColosseumRun, fight: SolFight): Int {
        val choice = GRAPPLES.random(run.random)
        fight.npc.say(choice)
        fight.npc.anim("seq.npc_colossi_finalboss_01_grapple_attack_telegraph")
        freeze(fight.npc, GRAPPLE_FROZEN)
        deps.worldQueues.add(GRAPPLE_WINDOW) {
            if (run.stage != RunStage.WAVE || fight.npc.hitpoints <= 0) return@add
            hurt(run, run.random.nextInt(GRAPPLE_MIN, GRAPPLE_MAX + 1) + ColosseumEffects.maxHitBonus(run))
            run.player.mes("Sol Heredit grabs hold of you and deals massive damage!")
        }
        return SPEED_GRAPPLE
    }

    private fun transition(run: ColosseumRun, fight: SolFight) {
        val at = run.playerTile()
        val others = if (fight.phase == ENRAGED) SAND_ENRAGED - 1 else SAND_TRANSITION - 1
        val taken = { tile: ArenaTile -> run.sand.containsKey(tile) || tile == at }
        placeSand(run, listOf(at) + ColosseumSolPatterns.sandTargets(at, others, run.random, taken))
        if (fight.phase < ENRAGED && fight.crystals.size < CRYSTALS.size) spawnCrystal(run, fight)
        if (fight.phase == ENRAGED) fight.laserCooldown = LASER_ENRAGED
        freeze(fight.npc, TRANSITION_FROZEN)
        fight.nextAttackAt = run.waveTicks + SPEED_TRANSITION
        fight.forced = "spear"
    }

    private fun placeSand(run: ColosseumRun, tiles: List<ArenaTile>) {
        for (tile in tiles) {
            run.player.spotanimMapPrivate(run.tile(tile.x, tile.z), "spotanim.vfx_colosseum_sunfire_lightning_01_beam_01")
        }
        deps.worldQueues.add(SAND_LANDS_AFTER) {
            if (run.stage != RunStage.WAVE) return@add
            for (tile in tiles) addSand(run, tile)
        }
    }

    fun addSand(run: ColosseumRun, tile: ArenaTile) {
        if (run.sand.containsKey(tile)) return
        val loc = locRepo.add(run.tile(tile.x, tile.z), SAND_LOC, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        run.sand[tile] = loc
    }

    fun clearSand(run: ColosseumRun) {
        for (loc in run.sand.values) locRepo.del(loc, 0)
        run.sand.clear()
    }

    fun tendSand(run: ColosseumRun) {
        if (run.sand.containsKey(run.playerTile()) && run.waveTicks % SAND_EVERY == 0) {
            hurt(run, run.random.nextInt(SAND_MIN, SAND_MAX + 1))
        }
    }

    private fun spawnCrystal(run: ColosseumRun, fight: SolFight) {
        val data = CRYSTALS[fight.crystals.size]
        val npc = runs.spawn(run, ColosseumNpcs.CRYSTAL, run.tile(data.spawn.x, data.spawn.z)) ?: return
        npc.anim("seq.npc_colosseum_crystal_spawn_01")
        run.hazards += npc
        val patrol = CrystalPatrol(npc, data.ends, data.fire, data.charge, data.attack)
        patrol.busyUntil = run.waveTicks + CRYSTAL_SPAWN_LOCK
        fight.crystals += patrol
    }

    private fun patrol(run: ColosseumRun, crystal: CrystalPatrol) {
        if (run.waveTicks < crystal.busyUntil || run.waveTicks % CRYSTAL_STEP_TICKS != 0) return
        val here = arenaTileOf(run, crystal.npc)
        var target = crystal.ends[crystal.end]
        if (here == target) {
            crystal.end = (crystal.end + 1) % crystal.ends.size
            target = crystal.ends[crystal.end]
        }
        val dest = run.tile(here.x + Integer.signum(target.x - here.x), here.z + Integer.signum(target.z - here.z))
        crystal.npc.routeRequest = RouteRequestCoord(dest)
    }

    private fun fireCrystal(run: ColosseumRun, fight: SolFight, crystal: CrystalPatrol) {
        val (dx, dz) = crystal.fireDirection
        val here = arenaTileOf(run, crystal.npc)
        val path = mutableListOf<ArenaTile>()
        var n = 1
        while (n <= LASER_LENGTH && ColosseumSolPatterns.inside(here.x + dx * n, here.z + dz * n)) {
            path += ArenaTile(here.x + dx * n, here.z + dz * n)
            n++
        }
        crystal.npc.anim("seq.npc_colosseum_crystal_charge_01")
        for ((index, tile) in path.withIndex()) {
            run.player.spotanimMapPrivate(run.tile(tile.x, tile.z), crystal.charge, index * 2, 128)
        }
        val charge = if (fight.phase == ENRAGED) LASER_CHARGE_ENRAGED else LASER_CHARGE
        crystal.busyUntil = run.waveTicks + charge + 2
        deps.worldQueues.add(charge) {
            if (run.stage != RunStage.WAVE || !crystal.npc.isSlotAssigned) return@add
            crystal.npc.anim("seq.npc_colosseum_crystal_attack_01")
            for ((index, tile) in path.withIndex()) {
                run.player.spotanimMapPrivate(run.tile(tile.x, tile.z), crystal.attack, index * 2, 128)
            }
            path.lastOrNull()?.let {
                run.player.spotanimMapPrivate(run.tile(it.x, it.z), "spotanim.vfx_colosseum_crystal_attack_01_impact_01", path.size * 2, 80)
            }
            val at = run.playerTile()
            if (path.any { it.x == at.x && it.z == at.z }) {
                hurt(run, run.random.nextInt(LASER_DAMAGE_MIN, LASER_DAMAGE_MAX + 1))
            }
        }
    }

    private fun smoke(run: ColosseumRun, tiles: List<RingTile>) {
        for (tile in tiles) {
            run.player.spotanimMapPrivate(run.tile(tile.x, tile.z), SMOKE.random(run.random), tile.ring * 2)
        }
    }

    fun hurt(run: ColosseumRun, amount: Int, fromEnemy: Boolean = true) {
        if (amount <= 0 || run.stage == RunStage.ENDED) return
        val source = run.fight?.npc ?: run.npcs.firstOrNull() ?: return
        run.player.queueHit(source, 1, HitType.Typeless, amount, NoopPlayerHitModifier)
        ColosseumEffects.damageTaken(run, amount, fromEnemy)
    }

    private fun freeze(npc: Npc, ticks: Int) {
        npc.vars["varn.freeze_end_clock"] = npc.currentMapClock + ticks
    }

    private fun defeated(run: ColosseumRun) {
        if (run.stage != RunStage.WAVE) return
        run.stage = RunStage.WON
        run.fight?.npc?.say(DEATH_LINE)
        run.wave = Colosseum.FINAL_WAVE
        run.totalTicks += run.waveTicks
        deps.worldQueues.add(DEATH_TICKS) { finish(run) }
    }

    private fun finish(run: ColosseumRun) {
        if (run.stage != RunStage.WON) return
        clear(run)
        rewards.waveCompleted(run)
        run.stage = RunStage.WON
        rewards.finishRun(run)
    }

    fun clear(run: ColosseumRun) {
        for (loc in run.barricade) locRepo.del(loc, 0)
        run.barricade.clear()
        clearSand(run)
        run.fight?.crystals?.forEach { runs.remove(it.npc) }
        run.fight = null
    }

    private fun angleOf(rotation: Int): LocAngle =
        when (rotation and 3) {
            0 -> LocAngle.West
            1 -> LocAngle.North
            2 -> LocAngle.East
            else -> LocAngle.South
        }

    private class CrystalData(
        val spawn: ArenaTile,
        val ends: List<ArenaTile>,
        val fire: Pair<Int, Int>,
        val charge: String,
        val attack: String,
    )

    companion object {
        const val LASER_MIN = 25
        const val LASER_MAX = 35
        const val SAND_ENRAGED_EVERY = 3
        private const val LASER_ENRAGED = 12
        private const val LASER_CHARGE = 3
        private const val LASER_CHARGE_ENRAGED = 2
        private const val LASER_LENGTH = 14
        private const val LASER_DAMAGE_MIN = 60
        private const val LASER_DAMAGE_MAX = 75
        private const val ENRAGED = 5
        private const val LAND_DELAY = 3
        private const val LANDING_FREEZE = 3
        private const val AOE_MAX = 44
        private const val AOE_LANDS_AFTER = 2
        private const val AOE_FROZEN = 4
        private const val SPEED_SPEAR = 7
        private const val SPEED_SHIELD = 6
        private const val SPEED_GRAPPLE = 7
        private const val SPEED_TRANSITION = 7
        private const val TRIPLE_SPEED = 12
        private const val SPECIAL_COOLDOWN = 2
        private val TRIPLE_EARLY = intArrayOf(15, 25, 35)
        private val TRIPLE_LATE = intArrayOf(15, 30, 45)
        private val TRIPLE_HITS_EARLY = intArrayOf(3, 6, 9)
        private val TRIPLE_HITS_LATE = intArrayOf(3, 6, 10)
        private const val GRAPPLE_WINDOW = 4
        private const val GRAPPLE_FROZEN = 5
        private const val GRAPPLE_MIN = 20
        private const val GRAPPLE_MAX = 45
        private const val TRANSITION_FROZEN = 5
        private const val SAND_TRANSITION = 6
        private const val SAND_ENRAGED = 5
        private const val SAND_LANDS_AFTER = 2
        private const val SAND_EVERY = 1
        private const val SAND_MIN = 6
        private const val SAND_MAX = 8
        private const val CRYSTAL_STEP_TICKS = 2
        private const val CRYSTAL_SPAWN_LOCK = 4
        private const val DEATH_TICKS = 5
        private const val SAND_LOC = "loc.colosseum_molten_pool_1"
        private const val DEATH_LINE = "You have bested me... for now."

        private val LANDING = ArenaTile(1823, 3108)
        private val PLAYER_START = ArenaTile(1825, 3103)
        private val PHASE_HP = intArrayOf(1500, 1350, 1125, 750, 375, 150)
        private val PHASE_LINES =
            arrayOf(
                "Let the games begin!",
                "You fight well, little one!",
                "The crowd demands more!",
                "You will not leave this arena alive!",
                "I am Sol Heredit, and I do not lose!",
                "ENOUGH! FEEL THE SUN'S WRATH!",
            )
        private val GRAPPLES =
            listOf(
                "I'LL CRUSH YOUR BODY!",
                "I'LL BREAK YOUR BACK!",
                "I'LL TWIST YOUR HANDS OFF!",
                "I'LL BREAK YOUR LEGS!",
                "I'LL CUT YOUR FEET OFF!",
            )
        private val SMOKE =
            listOf(
                "spotanim.spotanim_colossi_finalboss_01_melee",
                "spotanim.spotanim_colossi_finalboss_02_melee",
                "spotanim.spotanim_colossi_finalboss_03_melee",
                "spotanim.spotanim_colossi_finalboss_04_melee",
            )
        private val BARRICADE_LOCS =
            listOf(
                "loc.shield_combatant_1",
                "loc.shield_combatant_2",
                "loc.shield_combatant_3",
                "loc.shield_combatant_4",
                "loc.shield_combatant_5",
                "loc.shield_combatant_6",
            )

        private val CRYSTALS: List<CrystalData> =
            ColosseumSolPatterns.let {
                val minX = ColosseumSolPatterns.MIN_X
                val maxX = ColosseumSolPatterns.MAX_X
                val minZ = ColosseumSolPatterns.MIN_Z
                val maxZ = ColosseumSolPatterns.MAX_Z
                listOf(
                    CrystalData(ArenaTile(minX + 1, maxZ), listOf(ArenaTile(maxX - 1, maxZ), ArenaTile(minX + 1, maxZ)), 0 to -1, "spotanim.vfx_colosseum_crystal_charge_01_beam_01", "spotanim.vfx_colosseum_crystal_attack_01_beam_01"),
                    CrystalData(ArenaTile(maxX, minZ + 2), listOf(ArenaTile(maxX, maxZ - 1), ArenaTile(maxX, minZ + 1)), -1 to 0, "spotanim.vfx_colosseum_crystal_charge_01_beam_02", "spotanim.vfx_colosseum_crystal_attack_01_beam_02"),
                    CrystalData(ArenaTile(minX + 1, minZ), listOf(ArenaTile(maxX - 1, minZ), ArenaTile(minX + 1, minZ)), 0 to 1, "spotanim.vfx_colosseum_crystal_charge_01_beam_03", "spotanim.vfx_colosseum_crystal_attack_01_beam_03"),
                    CrystalData(ArenaTile(minX, minZ + 1), listOf(ArenaTile(minX, maxZ - 1), ArenaTile(minX, minZ + 1)), 1 to 0, "spotanim.vfx_colosseum_crystal_charge_01_beam_04", "spotanim.vfx_colosseum_crystal_attack_01_beam_04"),
                )
            }
    }
}
