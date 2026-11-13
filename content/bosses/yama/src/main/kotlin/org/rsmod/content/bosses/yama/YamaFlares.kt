package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Void flares (Flares.Yama.js): a pair spawns on one of two pairs of tiles, charging for 26 ticks;
 * one still standing then explodes, heals Yama and hits the challenger a tick later, then is gone.
 * In phases 1 and 2 one is fire and one shadow; phase 3's are shadow, with half the hitpoints and
 * half the blast. How hard they hit and heal shrinks with the hitpoints they have left, and a
 * glyph of their element blocks them. Flares are pinned, never respawn and are tracked in the
 * fight's set, so every one is deleted on the phase change and on Yama's death.
 */
internal object YamaFlares {
    class Flare(val npc: Npc, val tile: Pair<Int, Int>, val element: String, val hitpoints: Int)

    private val PAIRS =
        listOf(
            listOf(1495 to 10081, 1507 to 10073),
            listOf(1510 to 10081, 1498 to 10073),
        )

    private const val EXPLODE_TICKS = 26
    private const val REMOVE_AFTER_TICKS = 2
    private const val MAX_ACTIVE = 4
    private val HITPOINTS = intArrayOf(140, 140, 71)
    private val DAMAGE = listOf(42..45, 42..45, 23..25)
    private val HEAL = intArrayOf(36, 36, 18)

    /** Spawns [count] flares on free flare tiles (a pair's tiles first). */
    fun spawn(fight: YamaFight, count: Int) {
        val phase = fight.phase - 1
        val pair = PAIRS[fight.deps.random.of(PAIRS.size)]
        val taken = fight.flares.map { it.tile }.toSet()
        val tiles = (pair + PAIRS.flatten()).distinct()
        val free = tiles.filter { it !in taken }.take(minOf(count, MAX_ACTIVE - fight.flares.size))
        val elements =
            if (fight.phase == 3) free.map { "shadow" }
            else if (fight.deps.random.randomBoolean(2)) listOf("fire", "shadow")
            else listOf("shadow", "fire")
        free.forEachIndexed { i, tile ->
            val npc = fight.spawn(YamaIds.VOID_FLARE, fight.arena.tile(tile.first, tile.second)) ?: return@forEachIndexed
            val hitpoints = HITPOINTS[phase]
            npc.hitpoints = hitpoints
            npc.anim(YamaIds.SEQ_FLARE_SPAWN)
            val flare = Flare(npc, tile, elements.getOrElse(i) { "shadow" }, hitpoints)
            fight.flares += flare
            fight.at(EXPLODE_TICKS) { explode(fight, flare) }
        }
    }

    private fun explode(fight: YamaFight, flare: Flare) {
        val npc = flare.npc
        if (!npc.isSlotAssigned || npc.hitpoints <= 0 || flare !in fight.flares) return
        val phase = fight.phase - 1
        val left = maxOf(0, npc.hitpoints).toDouble() / flare.hitpoints
        val range = DAMAGE[phase]
        val fire = flare.element == "fire"
        npc.anim(YamaIds.SEQ_FLARE_EXPLODE)
        npc.spotanim(if (fire) YamaIds.SPOT_FLARE_EXPLODE_FIRE else YamaIds.SPOT_FLARE_EXPLODE_SHADOW)
        val heal = (HEAL[phase] * left).toInt()
        fight.yama?.let { if (heal > 0) it.heal(heal) }
        fight.player.spotanimSpecific(
            if (fire) YamaIds.SPOT_FLARE_IMPACT_FIRE else YamaIds.SPOT_FLARE_IMPACT_SHADOW,
            delay = 30,
        )
        fight.at(1) {
            if (YamaGlyphs.immune(fight, flare.element)) {
                YamaGlyphs.blocked(fight, flare.element)
            } else {
                val roll = range.first + fight.deps.random.of(range.last - range.first + 1)
                YamaHits.damage(fight, Math.round(roll * left).toInt())
            }
        }
        fight.at(REMOVE_AFTER_TICKS) { remove(fight, flare) }
    }

    private fun remove(fight: YamaFight, flare: Flare) {
        fight.flares.remove(flare)
        fight.remove(flare.npc)
    }

    /** All flares go without exploding (the phase change, the end of the fight). */
    fun clear(fight: YamaFight) {
        for (flare in fight.flares.toList()) remove(fight, flare)
    }

    fun registerHooks(ctx: ScriptContext, fights: YamaFights) {
        val type = ServerCacheManager.getNpc(YamaIds.VOID_FLARE.asRSCM(RSCMType.NPC)) ?: return
        with(ctx) { onModifyNpcHit(type) { neverMiss(fights, this) } }
    }

    /**
     * Magic and standard ranged weapons always land on a flare (Wiki). The engine's modify hook
     * does not expose the attacker's maximum, so a would-be miss lands as 1 (see YAMA-DEVIATIONS).
     */
    private fun neverMiss(fights: YamaFights, event: NpcHitEvents.Modify) {
        val hit = event.hit
        if (!hit.isFromPlayer) return
        if (hit.type != HitType.Magic && hit.type != HitType.Ranged) return
        if (hit.damage <= 0) hit.damage = 1
    }

    @Suppress("unused") private fun unusedCoord(c: CoordGrid) = c
}
