package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

internal class OcellusDeflect(private val deps: BossDeps) {
    private val carriers = HashMap<Npc, Carrier>()

    fun register() {
        deps.extensionRegistry.register(START) { start(it.npc, it.params as Style) }
        deps.extensionRegistry.register(END) { end(it.npc) }
    }

    fun clear() {
        for (carrier in carriers.values) remove(carrier)
        carriers.clear()
    }

    enum class Style(val spot: String) {
        Ranged("range"),
        Melee("melee"),
    }

    private class Carrier(val npc: Npc, val style: Style, val suffix: String)

    private fun start(boss: Npc, style: Style) {
        carriers.remove(boss)?.let(::remove)
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        val npc = Npc(CARRIER, centre)
        npc.moveRestrict = MoveRestrict.NoMove
        npc.mode = NpcMode.None
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        val carrier = Carrier(npc, style, suffix(boss))
        carriers[boss] = carrier
        npc.spotanim(spot(carrier, "start"))
        loop(boss, carrier)
        burn(boss, carrier)
    }

    private fun burn(boss: Npc, carrier: Carrier) {
        deps.worldQueues.add(1) {
            if (carriers[boss] !== carrier || !boss.isSlotAssigned) return@add
            for (player in OcellusArena.quadrantPlayers(boss, deps.playerList)) {
                if (inVoid(boss, carrier.style, player.coords)) {
                    val damage = deps.random.of(VOID_DAMAGE)
                    player.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
                }
            }
            burn(boss, carrier)
        }
    }

    private fun inVoid(boss: Npc, style: Style, tile: CoordGrid): Boolean {
        val near =
            tile.x in boss.coords.x - RING..boss.coords.x + boss.size - 1 + RING &&
                tile.z in boss.coords.z - RING..boss.coords.z + boss.size - 1 + RING
        return when (style) {
            Style.Ranged -> near
            Style.Melee -> !near
        }
    }

    private fun loop(boss: Npc, carrier: Carrier) {
        deps.worldQueues.add(1) {
            if (carriers[boss] !== carrier) return@add
            if (!boss.isSlotAssigned || !carrier.npc.isSlotAssigned) {
                carriers.remove(boss)
                remove(carrier)
                return@add
            }
            carrier.npc.spotanim(spot(carrier, "loop"))
            deps.worldQueues.add(LOOP_GAP - 1) { loop(boss, carrier) }
        }
    }

    private fun end(boss: Npc) {
        val carrier = carriers.remove(boss) ?: return
        if (!carrier.npc.isSlotAssigned) return
        carrier.npc.spotanim(spot(carrier, "end"))
        deps.worldQueues.add(END_TICKS) { remove(carrier) }
    }

    private fun remove(carrier: Carrier) {
        if (carrier.npc.isSlotAssigned) deps.npcRepo.del(carrier.npc, Int.MAX_VALUE)
    }

    private fun spot(carrier: Carrier, phase: String): String =
        "spotanim.vfx_ocellus_deflect_arena_${carrier.style.spot}_$phase${carrier.suffix}"

    private fun suffix(boss: Npc): String {
        val middle = OcellusArena.middle(boss)
        val east = boss.coords.x > middle.x
        val north = boss.coords.z > middle.z
        return when {
            !east && !north -> ""
            east && !north -> "_270"
            east -> "_180"
            else -> "_90"
        }
    }

    companion object {
        const val START = "ocellus.deflect_start"
        const val END = "ocellus.deflect_end"

        private const val CARRIER = "npc.tfa_invisible_npc"
        private const val LOOP_GAP = 3
        private const val END_TICKS = 3
        private const val RING = 1

        // Guides: void on the floor hits for 6-7 a tick.
        private val VOID_DAMAGE = 6..7
    }
}
