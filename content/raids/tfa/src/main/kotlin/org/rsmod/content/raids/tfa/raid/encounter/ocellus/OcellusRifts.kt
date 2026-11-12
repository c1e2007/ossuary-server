package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import kotlin.math.abs
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statHeal
import org.rsmod.game.entity.Npc
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

internal class OcellusRifts(private val deps: BossDeps) {
    private var kind: Kind? = null
    private var opened = 0
    private val alive = ArrayList<Rift>()

    fun register() {
        deps.extensionRegistry.register(RESET) { reset(it.params as Int) }
        deps.extensionRegistry.register(OPEN) { open(it.npc) }
        deps.extensionRegistry.register(STOP) { stop() }
    }

    fun clear() {
        stop()
    }

    fun clearOn(tiles: Set<CoordGrid>) {
        for (rift in alive.filter { it.npc.coords in tiles }) remove(rift)
    }

    private enum class Kind(
        val npc: String,
        val spawnSeq: String,
        val detonateSeq: String,
        val detonateSpot: String,
        val forcedMaxHitStyle: Int,
        val maxCount: Int = COUNT,
    ) {
        Siphon(
            "npc.tfa_ocellus_p3_bombportal_heal",
            "seq.vfx_ocellus_voidflare_heal_spawn",
            "seq.vfx_ocellus_voidflare_heal_detonate",
            "spotanim.vfx_human_ocellus_voidflare_heal",
            FORCED_RANGED,
        ),
        Augmenter(
            "npc.tfa_ocellus_p3_bombportal_buff",
            "seq.vfx_ocellus_voidflare_buff_spawn",
            "seq.vfx_ocellus_voidflare_buff_detonate",
            "spotanim.vfx_human_ocellus_voidflare_buff",
            FORCED_MAGIC,
        ),
        Rift(
            "npc.tfa_ocellus_p3_bombportal_basic",
            "seq.vfx_ocellus_voidflare_default_spawn",
            "seq.vfx_ocellus_voidflare_default_detonate",
            "spotanim.vfx_human_ocellus_voidflare_default",
            FORCED_MELEE,
            maxCount = RIFT_MAX_COUNT,
        ),
    }

    private class Rift(val npc: Npc, val kind: Kind, var age: Int = 0)

    private fun reset(kindIndex: Int) {
        stop()
        kind = Kind.entries.getOrNull(kindIndex)
        opened = 0
    }

    private fun stop() {
        kind = null
        for (rift in alive.toList()) remove(rift)
    }

    private fun open(boss: Npc) {
        val kind = kind ?: return
        if (kind == Kind.Rift) {
            for (old in alive.toList()) remove(old)
        }
        val count = minOf(COUNT + opened++, kind.maxCount)
        for (tile in pickTiles(boss, count)) {
            val npc = Npc(kind.npc, tile)
            npc.moveRestrict = MoveRestrict.NoMove
            npc.mode = NpcMode.None
            npc.ignoreCombatInteractions = true
            npc.vars[FORCED_MAX_HIT_STYLES] = kind.forcedMaxHitStyle
            deps.npcRepo.add(npc, Int.MAX_VALUE)
            npc.respawns = false
            npc.hideAllOps()
            npc.anim(kind.spawnSeq)
            npc.showHeadbar(Headbar.fromNoSource(CHARGE_BAR, CHARGE_BAR, 0, CHARGE_SEGMENTS, 0, CHARGE_CYCLES))
            showHealth(npc)
            val rift = Rift(npc, kind)
            alive.add(rift)
            deps.worldQueues.add(1) { if (rift in alive) npc.showAllOps() }
            watch(boss, rift)
        }
    }

    private fun pickTiles(boss: Npc, count: Int): List<CoordGrid> {
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        val taken = deps.playerList.map { it.coords }.toSet() + alive.map { it.npc.coords }
        val free = ArrayList<CoordGrid>()
        for (dx in -REACH..REACH) {
            for (dz in -REACH..REACH) {
                val tile = centre.translate(dx, dz)
                if (maxOf(abs(dx), abs(dz)) < MIN_DISTANCE) continue
                if (!OcellusArena.sameQuadrantFloor(boss, tile) || tile in taken) continue
                if (deps.collision[tile.x, tile.z, tile.level] and BLOCKED != 0) continue
                free.add(tile)
            }
        }
        return List(minOf(count, free.size)) { free.removeAt(deps.random.of(free.size)) }
    }

    private fun watch(boss: Npc, rift: Rift) {
        deps.worldQueues.add(1) {
            if (rift !in alive) return@add
            val npc = rift.npc
            when {
                !npc.isSlotAssigned || !boss.isSlotAssigned -> {
                    remove(rift)
                    return@add
                }
                npc.hitpoints <= 0 -> {
                    showHealth(npc)
                    killed(rift)
                    return@add
                }
            }
            showHealth(npc)
            rift.age++
            if (rift.kind == Kind.Siphon && rift.age % SIPHON_GAP == 0) siphon(boss, npc)
            if (rift.age == DETONATE_AT - 1) overload(boss)
            if (rift.age == DETONATE_AT) {
                detonate(boss, rift)
                return@add
            }
            watch(boss, rift)
        }
    }

    private fun showHealth(npc: Npc) {
        val bar = checkNotNull(ServerCacheManager.getHealthBar(HEALTH_BAR.asRSCM(RSCMType.HEADBAR)))
        val max = npc.baseHitpointsLvl
        val fill = (npc.hitpoints * bar.segments + max - 1) / max
        npc.showHeadbar(Headbar.fromNoSource(bar.id, bar.id, fill, fill, 0, 0))
    }

    private fun siphon(boss: Npc, from: Npc) {
        val start = from.coords
        val end = boss.coords.translate(boss.size / 2, boss.size / 2)
        deps.worldRepo.projAnim(
            ProjAnim(
                spotanim = SIPHON_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
                startHeight = SIPHON_START_HEIGHT,
                endHeight = SIPHON_END_HEIGHT,
                startTime = 0,
                endTime = SIPHON_CYCLES,
                angle = SIPHON_ANGLE,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = start,
                endCoord = end,
            )
        )
        deps.worldRepo.soundArea(boss.coords, SIPHON_SOUND, radius = SIPHON_SOUND_RADIUS)
        boss.heal(SIPHON_HEAL, showHitsplat = true)
    }

    private fun overload(boss: Npc) {
        for (player in OcellusArena.quadrantPlayers(boss, deps.playerList)) {
            val damage = deps.random.of(DETONATE_DAMAGE)
            player.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
    }

    private fun detonate(boss: Npc, rift: Rift) {
        val npc = rift.npc
        npc.hideAllOps()
        npc.anim(rift.kind.detonateSeq)
        for (player in OcellusArena.quadrantPlayers(boss, deps.playerList)) {
            player.spotanim(rift.kind.detonateSpot, delay = DETONATE_SPOT_DELAY)
        }
        deps.worldQueues.add(DETONATE_REMOVAL) { remove(rift) }
    }

    private fun killed(rift: Rift) {
        val killer = rift.npc.findHero(deps.playerList)
        alive.remove(rift)
        if (killer == null || !killer.isValidTarget()) return
        when (rift.kind) {
            Kind.Siphon -> killer.statHeal(HITPOINTS, SIPHON_KILL_HEAL, 0)
            Kind.Augmenter -> {
                killer.statBoost(ATTACK, MELEE_CONSTANT, MELEE_PERCENT)
                killer.statBoost(STRENGTH, MELEE_CONSTANT, MELEE_PERCENT)
                killer.statBoost(RANGED, MAGIC_CONSTANT, MAGIC_PERCENT)
                killer.statBoost(MAGIC, MAGIC_CONSTANT, MAGIC_PERCENT)
            }
            Kind.Rift -> killer.actionDelay = killer.currentMapClock
        }
    }

    private fun remove(rift: Rift) {
        alive.remove(rift)
        if (rift.npc.isSlotAssigned) deps.npcRepo.del(rift.npc, Int.MAX_VALUE)
    }

    companion object {
        const val RESET = "ocellus.rifts_reset"
        const val OPEN = "ocellus.rifts_open"
        const val STOP = "ocellus.rifts_stop"

        private const val FORCED_MAX_HIT_STYLES = "varn.forced_max_hit_styles"
        private const val FORCED_MELEE = 1
        private const val FORCED_RANGED = 2
        private const val FORCED_MAGIC = 4

        private const val COUNT = 2
        private const val RIFT_MAX_COUNT = 4
        private const val MIN_DISTANCE = 3
        private const val REACH = 12
        private const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        private const val HEALTH_BAR = "headbar.health_40"
        private val CHARGE_BAR = "headbar.ocellus_rift_charge".asRSCM(RSCMType.HEADBAR)
        private const val CHARGE_SEGMENTS = 30
        private const val CHARGE_CYCLES = 840
        private const val DETONATE_AT = 30
        private const val DETONATE_REMOVAL = 2
        private const val DETONATE_SPOT_DELAY = 30
        private val DETONATE_DAMAGE = 35..55

        private const val SIPHON_GAP = 4
        private const val SIPHON_HEAL = 14
        private const val SIPHON_KILL_HEAL = 22
        private const val SIPHON_PROJECTILE = "spotanim.vfx_ocellus_voidflare_healing_projanim"
        private const val SIPHON_START_HEIGHT = 160
        private const val SIPHON_END_HEIGHT = 140
        private const val SIPHON_CYCLES = 30
        private const val SIPHON_ANGLE = 2
        private const val SIPHON_SOUND = "synth.ocellus_siphon_heal"
        private const val SIPHON_SOUND_RADIUS = 8

        private const val HITPOINTS = "stat.hitpoints"
        private const val ATTACK = "stat.attack"
        private const val STRENGTH = "stat.strength"
        private const val RANGED = "stat.ranged"
        private const val MAGIC = "stat.magic"
        private const val MELEE_CONSTANT = 5
        private const val MELEE_PERCENT = 13
        private const val MAGIC_CONSTANT = 4
        private const val MAGIC_PERCENT = 10
    }
}
