package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.disableOverheadPrayers
import org.rsmod.api.player.output.spotanimMapPrivate
import org.rsmod.game.entity.Npc
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.routefinder.flag.CollisionFlag

internal class BeeSwarm(var npc: Npc?, var respawnAt: Int = 0)

internal class SolarFlare(val npc: Npc, val path: List<RingTile>, var index: Int) {
    var restUntil: Int = 0
    var touching: Boolean = false
}

internal class TotemRecord(var totem: Npc?, var nextHeal: Int, var respawnAt: Int = 0)

@Singleton
internal class ColosseumHazards
@Inject
constructor(
    private val runs: ColosseumRuns,
    private val deps: BossDeps,
    private val sol: ColosseumSol,
) {
    fun waveStarted(run: ColosseumRun) {
        repeat(run.modifiers.tier(ColosseumModifier.BEES)) { spawnBees(run) }
        if (run.modifiers.has(ColosseumModifier.SOLARFLARE)) spawnSolarflare(run)
        for (npc in run.npcs) {
            if ("javelin" in npc.type.name.lowercase()) run.artillery[npc] = run.waveTicks + ARTILLERY_EVERY
        }
    }

    fun tick(run: ColosseumRun) {
        if (run.stage != RunStage.WAVE) return
        tendBees(run)
        tendSolarflare(run)
        tendTotems(run)
        tendArtillery(run)
        tendWarband(run)
        tendMinotaurs(run)
    }

    fun waveEnded(run: ColosseumRun) {
        for (npc in run.hazards.toList()) runs.remove(npc)
        run.hazards.clear()
        run.bees.clear()
        run.flare = null
        run.totems.clear()
        run.artillery.clear()
        val keep = run.permanentSand.toSet()
        for ((tile, loc) in run.sand.entries.toList()) {
            if (tile in keep) continue
            sol.removeSand(run, tile, loc)
        }
    }

    fun enemyDied(run: ColosseumRun, npc: Npc) {
        run.totems.remove(npc)?.totem?.let {
            run.hazards.remove(it)
            runs.remove(it)
        }
        run.artillery.remove(npc)
        val tier = run.modifiers.tier(ColosseumModifier.VOLATILITY)
        if (tier == 0) return
        val origin = run.arenaTile(npc)
        val size = npc.type.size
        val reach = minOf(tier, 2)
        val centre = ArenaTile(origin.x + size / 2, origin.z + size / 2)
        val gfx = explosionOf(npc)
        deps.worldQueues.add(EXPLOSION_DELAY) {
            if (run.stage == RunStage.ENDED) return@add
            run.player.spotanimMapPrivate(run.tile(centre.x, centre.z), gfx)
            val at = run.playerTile()
            val inside =
                at.x in origin.x - reach until origin.x + size + reach &&
                    at.z in origin.z - reach until origin.z + size + reach
            if (inside) sol.hurt(run, run.random.nextInt(1, EXPLOSION_MAX + 1), fromEnemy = false)
            if (tier >= 3 && free(run, centre.x, centre.z)) {
                sol.addSand(run, centre, permanent = run.modifiers.tier(ColosseumModifier.REENTRY) >= 2)
            }
        }
    }

    private fun explosionOf(npc: Npc): String {
        val name = npc.type.name.lowercase()
        return when {
            "manticore" in name -> "spotanim.vfx_colosseum_manticore_explosion_01"
            "colossus" in name -> "spotanim.vfx_colosseum_colossi_explosion_01"
            "minotaur" in name -> "spotanim.vfx_colosseum_minotaur_explosion_01"
            else -> "spotanim.vfx_colosseum_human_explosion_01"
        }
    }

    private fun free(run: ColosseumRun, x: Int, z: Int): Boolean {
        val at = run.tile(x, z)
        return (deps.collision[at.x, at.z, at.level] and CollisionFlag.BLOCK_WALK) == 0
    }

    private fun hazard(run: ColosseumRun, type: String, tile: ArenaTile): Npc? {
        val npc = runs.spawn(run, type, run.tile(tile.x, tile.z)) ?: return null
        run.hazards += npc
        return npc
    }

    private fun spawnBees(run: ColosseumRun) {
        val far =
            SPAWN_POINTS.filter { ColosseumSolPatterns.chebyshev(it, run.playerTile()) >= BEES_MIN_DISTANCE }
        val tile = (far.ifEmpty { SPAWN_POINTS }).random(run.random)
        run.bees += BeeSwarm(hazard(run, ColosseumNpcs.BEES, tile))
    }

    private fun stepTowards(run: ColosseumRun, npc: Npc, target: ArenaTile) {
        val origin = run.arenaTile(npc)
        val size = npc.type.size
        val dx = if (target.x < origin.x) -1 else if (target.x >= origin.x + size) 1 else 0
        val dz = if (target.z < origin.z) -1 else if (target.z >= origin.z + size) 1 else 0
        if (dx != 0 || dz != 0) {
            npc.routeRequest = RouteRequestCoord(run.tile(origin.x + dx, origin.z + dz))
        }
    }

    private fun covers(run: ColosseumRun, npc: Npc, tile: ArenaTile): Boolean {
        val origin = run.arenaTile(npc)
        val size = npc.type.size
        return tile.x in origin.x until origin.x + size && tile.z in origin.z until origin.z + size
    }

    private fun alive(npc: Npc?): Boolean = npc != null && npc.isSlotAssigned && npc.hitpoints > 0

    private fun tendBees(run: ColosseumRun) {
        val tile = run.playerTile()
        for (bee in run.bees) {
            val npc = bee.npc
            if (npc == null || !alive(npc)) {
                if (npc != null) {
                    run.hazards.remove(npc)
                    runs.remove(npc)
                    bee.npc = null
                    bee.respawnAt = run.waveTicks + BEES_RESPAWN
                }
                if (run.waveTicks >= bee.respawnAt) {
                    bee.npc = hazard(run, ColosseumNpcs.BEES, SPAWN_POINTS.random(run.random))
                }
                continue
            }
            if (run.waveTicks % BEES_MOVE_EVERY == 0) stepTowards(run, npc, tile)
            if (covers(run, npc, tile)) {
                sol.hurt(run, run.random.nextInt(1, BEES_MAX + 1), fromEnemy = false)
            }
        }
    }

    private fun ring(minX: Int, minZ: Int): List<RingTile> {
        val lowX = minX - 1
        val lowZ = minZ - 1
        val highX = minX + 3
        val highZ = minZ + 3
        val tiles = mutableListOf<RingTile>()
        for (z in lowZ until highZ) tiles += RingTile(lowX, z, 0)
        for (x in lowX until highX) tiles += RingTile(x, highZ, 0)
        for (z in highZ downTo lowZ + 1) tiles += RingTile(highX, z, 0)
        for (x in highX downTo lowX + 1) tiles += RingTile(x, lowZ, 0)
        return tiles
    }

    private fun isCorner(tile: RingTile, minX: Int, minZ: Int): Boolean =
        (tile.x == minX - 1 || tile.x == minX + 3) && (tile.z == minZ - 1 || tile.z == minZ + 3)

    private fun spawnSolarflare(run: ColosseumRun) {
        val pillars = if (run.wave + 1 >= Colosseum.FINAL_WAVE) SOL_CORNERS else PILLARS
        val pillar = pillars.random(run.random)
        val path = ring(pillar.x, pillar.z)
        val index = run.random.nextInt(path.size)
        val npc = hazard(run, ColosseumNpcs.SOLAR_FLARE, ArenaTile(path[index].x, path[index].z)) ?: return
        run.flare = SolarFlare(npc, path, index)
        run.flarePillar = pillar
    }

    private fun tendSolarflare(run: ColosseumRun) {
        val flare = run.flare ?: return
        val tier = run.modifiers.tier(ColosseumModifier.SOLARFLARE)
        if (tier == 0) return
        val moveEvery = if (tier == 3) 1 else 2
        val rest = intArrayOf(0, 7, 0, 2)[tier]
        val max = intArrayOf(0, 5, 10, 15)[tier]
        if (run.waveTicks >= flare.restUntil && run.waveTicks % moveEvery == 0) {
            flare.index = (flare.index + 1) % flare.path.size
            val next = flare.path[flare.index]
            flare.npc.routeRequest = RouteRequestCoord(run.tile(next.x, next.z))
            val pillar = run.flarePillar
            if (pillar != null && isCorner(next, pillar.x, pillar.z) && rest > 0) {
                flare.restUntil = run.waveTicks + moveEvery + rest
            }
        }
        val touching = covers(run, flare.npc, run.playerTile())
        if (touching && !flare.touching) {
            sol.hurt(run, run.random.nextInt(1, max + 1), fromEnemy = false)
            if (tier == 3) run.player.disableOverheadPrayers()
        }
        flare.touching = touching
    }

    private fun tendTotems(run: ColosseumRun) {
        if (!run.modifiers.has(ColosseumModifier.TOTEMIC)) return
        for (enemy in run.npcs.toList()) {
            if (!alive(enemy)) continue
            val max = enemy.type.hitpoints
            var record = run.totems[enemy]
            val oldTotem = record?.totem
            if (oldTotem != null && !alive(oldTotem)) {
                run.hazards.remove(oldTotem)
                runs.remove(oldTotem)
                record.totem = null
                record.respawnAt = run.waveTicks + TOTEM_RESPAWN
            }
            val wounded = enemy.hitpoints * 2 <= max
            if (record?.totem == null && wounded && run.waveTicks >= (record?.respawnAt ?: 0)) {
                val origin = run.arenaTile(enemy)
                val tile = ArenaTile(origin.x - 1, origin.z - 1)
                val spot = if (free(run, tile.x, tile.z)) tile else ArenaTile(origin.x + enemy.type.size, origin.z)
                val totem = hazard(run, ColosseumNpcs.TOTEM, spot) ?: continue
                record = TotemRecord(totem, run.waveTicks + TOTEM_FIRST_HEAL)
                run.totems[enemy] = record
            }
            val active = run.totems[enemy] ?: continue
            val totem = active.totem ?: continue
            if (run.waveTicks < active.nextHeal || enemy.hitpoints >= max) continue
            totem.anim("seq.npc_colosseum_totem_attack_01")
            val isSol = enemy == run.fight?.npc
            val amount = if (isSol) SOL_TOTEM_HEAL else (max * TOTEM_SHARE).toInt()
            enemy.hitpoints = minOf(max, enemy.hitpoints + amount)
            active.nextHeal = run.waveTicks + if (isSol) SOL_TOTEM_EVERY else TOTEM_EVERY
        }
    }

    private fun tendArtillery(run: ColosseumRun) {
        for ((npc, next) in run.artillery.entries.toList()) {
            if (!alive(npc) || run.waveTicks < next) continue
            run.artillery[npc] = run.waveTicks + ARTILLERY_EVERY
            npc.anim("seq.npc_colossi_javelin_01_artillery_attack")
            npc.spotanim("spotanim.npc_colossi_javelin_01_artillery_fire")
            val at = run.playerTile()
            deps.worldQueues.add(ARTILLERY_LANDS) {
                if (run.stage != RunStage.WAVE || !alive(npc)) return@add
                run.player.spotanimMapPrivate(run.tile(at.x, at.z), "spotanim.npc_colossi_javelin_01_artillery_slow")
                sol.landJavelin(run, at)
                if (run.playerTile() == at) {
                    val damage = run.random.nextInt(0, ARTILLERY_MAX + 1) + ColosseumEffects.maxHitBonus(run)
                    sol.hurt(run, damage, fromEnemy = false)
                }
            }
        }
    }

    private fun isTrio(npc: Npc): Boolean {
        val name = npc.type.name.lowercase()
        return TRIO.any { it in name }
    }

    private fun tendWarband(run: ColosseumRun) {
        val player = run.playerTile()
        for (npc in run.npcs.filter { isTrio(it) && alive(it) }) {
            val offset = offsetOf(npc)
            val dest = ArenaTile(player.x + offset.first, player.z + offset.second)
            if (run.arenaTile(npc) == dest || !free(run, dest.x, dest.z)) continue
            npc.routeRequest = RouteRequestCoord(run.tile(dest.x, dest.z))
        }
    }

    private fun offsetOf(npc: Npc): Pair<Int, Int> {
        val name = npc.type.name.lowercase()
        return when {
            "mage" in name || "seer" in name -> 1 to 0
            "ranged" in name || "archer" in name -> -1 to 0
            else -> 0 to 1
        }
    }

    private fun tendMinotaurs(run: ColosseumRun) {
        if (run.waveTicks % MINOTAUR_HEAL_EVERY != 0) return
        val player = run.playerTile()
        for (minotaur in run.npcs) {
            if (!alive(minotaur) || "minotaur" !in minotaur.type.name.lowercase()) continue
            val at = run.arenaTile(minotaur)
            if (ColosseumSolPatterns.chebyshev(at, player) <= 1) continue
            for (other in run.npcs) {
                if (other == minotaur || !alive(other) || "minotaur" in other.type.name.lowercase()) continue
                val max = other.type.hitpoints
                if (other.hitpoints * 4 >= max * 3) continue
                if (ColosseumSolPatterns.chebyshev(at, run.arenaTile(other)) > MINOTAUR_HEAL_RANGE) continue
                other.hitpoints = max
            }
        }
    }

    private companion object {
        const val BEES_MOVE_EVERY = 12
        const val BEES_MAX = 10
        const val BEES_RESPAWN = 50
        const val BEES_MIN_DISTANCE = 4
        const val TOTEM_FIRST_HEAL = 5
        const val TOTEM_EVERY = 5
        const val TOTEM_SHARE = 0.3
        const val TOTEM_RESPAWN = 200
        const val SOL_TOTEM_HEAL = 75
        const val SOL_TOTEM_EVERY = 7
        const val EXPLOSION_DELAY = 2
        const val EXPLOSION_MAX = 15
        const val ARTILLERY_EVERY = 25
        const val ARTILLERY_LANDS = 3
        const val ARTILLERY_MAX = 40
        const val MINOTAUR_HEAL_EVERY = 5
        const val MINOTAUR_HEAL_RANGE = 7
        val TRIO = listOf("melee", "mage", "ranged", "warband")
        val PILLARS =
            listOf(ArenaTile(1816, 3113), ArenaTile(1831, 3113), ArenaTile(1816, 3098), ArenaTile(1831, 3098))
        val SOL_CORNERS =
            listOf(ArenaTile(1819, 3101), ArenaTile(1827, 3101), ArenaTile(1819, 3109), ArenaTile(1827, 3109))
        val SPAWN_POINTS =
            listOf(
                ArenaTile(1811, 3104),
                ArenaTile(1817, 3106),
                ArenaTile(1811, 3109),
                ArenaTile(1821, 3109),
                ArenaTile(1827, 3109),
                ArenaTile(1825, 3114),
                ArenaTile(1821, 3103),
                ArenaTile(1827, 3103),
                ArenaTile(1824, 3099),
                ArenaTile(1832, 3107),
                ArenaTile(1836, 3109),
                ArenaTile(1836, 3104),
            )
    }
}
