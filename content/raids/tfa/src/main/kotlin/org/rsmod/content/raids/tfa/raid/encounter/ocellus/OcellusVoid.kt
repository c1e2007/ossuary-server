package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

internal class OcellusVoid(private val deps: BossDeps) {
    private val lingering = HashSet<LocInfo>()

    fun register() {
        deps.extensionRegistry.register(INSOLENCE) {
            val tile = checkNotNull(it.tile) { "$INSOLENCE needs a tile." }
            val loc = insolenceLoc(it.params as Int)
            linger(it.npc, tile, Void(loc, INSOLENCE_TICKS, INSOLENCE_DESPAWN))
        }
        deps.extensionRegistry.register(DEBRIS) {
            val tile = checkNotNull(it.tile) { "$DEBRIS needs a tile." }
            linger(it.npc, tile, Void(DEBRIS_LOC, DEBRIS_TICKS, DEBRIS_DESPAWN))
        }
        deps.extensionRegistry.register(BOMB) {
            val tile = checkNotNull(it.tile) { "$BOMB needs a tile." }
            lingerForever(it.npc, tile, BOMB_LOC)
        }
    }

    fun clear() {
        for (loc in lingering) deps.locRepo.del(loc, Int.MAX_VALUE)
        lingering.clear()
    }

    fun tiles(): Set<CoordGrid> = lingering.mapTo(HashSet()) { it.coords }

    fun clearOn(tiles: Set<CoordGrid>) {
        val gone = lingering.filter { it.coords in tiles }
        for (loc in gone) deps.locRepo.del(loc, Int.MAX_VALUE)
        lingering.removeAll(gone.toSet())
    }

    private class Void(val loc: String, val lifetime: Int, val despawn: String)

    private fun linger(npc: Npc, tile: CoordGrid, void: Void) {
        val angle = LocAngle[deps.random.of(ROTATIONS)]
        val loc = deps.locRepo.add(tile, void.loc, Int.MAX_VALUE, angle, LocShape.GroundDecor)
        lingering.add(loc)
        degen(npc, loc, tile, void.lifetime - 1)
        deps.worldQueues.add(void.lifetime) {
            if (loc in lingering) {
                deps.worldRepo.locAnim(loc, void.despawn)
                deps.worldQueues.add(DESPAWN_TICKS) {
                    if (lingering.remove(loc)) deps.locRepo.del(loc, Int.MAX_VALUE)
                }
            }
        }
    }

    private fun lingerForever(npc: Npc, tile: CoordGrid, loc: String) {
        val angle = LocAngle[deps.random.of(ROTATIONS)]
        val info = deps.locRepo.add(tile, loc, Int.MAX_VALUE, angle, LocShape.GroundDecor)
        lingering.add(info)
        degen(npc, info, tile, Int.MAX_VALUE)
    }

    private fun degen(npc: Npc, loc: LocInfo, tile: CoordGrid, ticksLeft: Int) {
        deps.worldQueues.add(1) {
            if (loc !in lingering || !npc.isSlotAssigned) return@add
            for (player in deps.playerList) {
                if (!player.isValidTarget() || player.coords != tile) continue
                val damage = deps.random.of(DEGEN_DAMAGE)
                player.queueHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
            }
            if (ticksLeft > 1) degen(npc, loc, tile, ticksLeft - 1)
        }
    }

    private fun insolenceLoc(step: Int): String =
        when {
            step >= INSOLENCE_FAR_STEP -> "loc.tfa_ocellus_void_degen_aoe_insolence_7"
            step >= INSOLENCE_MID_STEP -> "loc.tfa_ocellus_void_degen_aoe_insolence_4"
            else -> "loc.tfa_ocellus_void_degen_aoe_insolence_1"
        }

    companion object {
        const val INSOLENCE = "ocellus.void_insolence"
        const val DEBRIS = "ocellus.void_debris"
        const val BOMB = "ocellus.void_bomb"

        private const val ROTATIONS = 4
        private const val DESPAWN_TICKS = 1
        private val DEGEN_DAMAGE = 5..15

        private const val INSOLENCE_TICKS = 15
        private const val INSOLENCE_MID_STEP = 3
        private const val INSOLENCE_FAR_STEP = 6
        private const val INSOLENCE_DESPAWN = "seq.fx_ocellus_shadow_slash_degen_despawn"

        private const val BOMB_LOC = "loc.tfa_ocellus_void_degen_bomb"
        private const val DEBRIS_TICKS = 50
        private const val DEBRIS_LOC = "loc.tfa_ocellus_void_degen_aoe"
        private const val DEBRIS_DESPAWN = "seq.vfx_ocellus_debris_despawn"
    }
}
