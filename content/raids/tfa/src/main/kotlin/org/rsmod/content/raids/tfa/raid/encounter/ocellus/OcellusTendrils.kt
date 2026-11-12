package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import kotlin.math.sign
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.soundSynth
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType

internal class OcellusTendrils(private val deps: BossDeps, private val meter: OcellusMeter) {
    fun register() {
        deps.extensionRegistry.register(CORNER) { corner(it.npc) }
        deps.extensionRegistry.register(CENTRE) { centre(it.npc) }
    }

    private fun corner(npc: Npc) {
        val centre = OcellusArena.centre(npc)
        val sx = (centre.x - npc.coords.x).sign
        val sz = (centre.z - npc.coords.z).sign
        val lines = OcellusTendril.corner(npc.coords, sx, sz)
        val active = npc.vars[ACTIVE]
        val occupied = lines.filterIndexed { i, _ -> active and (1 shl i) != 0 }.flatMap { it.tiles }
        val free = lines.indices.filter { i -> lines[i].tiles.none(occupied::contains) }
        val index = deps.random.pick(free)
        val bit = 1 shl index
        npc.vars[ACTIVE] = active or bit
        launch(npc, lines[index], DELAYED_DRAW, CORNER_RESOLVE) {
            npc.vars[ACTIVE] = npc.vars[ACTIVE] and bit.inv()
        }
    }

    private fun centre(npc: Npc) {
        val side = npc.vars[OcellusVarns.CENTRE_SIDE] % OcellusTendril.SIDES.size
        val (first, second) = OcellusTendril.centre(npc.coords, side)
        launch(npc, first, 0, CENTRE_FIRST_RESOLVE)
        launch(npc, second, DELAYED_DRAW, CENTRE_SECOND_RESOLVE)
    }

    private fun launch(
        npc: Npc,
        tendril: OcellusTendril,
        drawDelay: Int,
        resolveTicks: Int,
        onResolved: () -> Unit = {},
    ) {
        tendril.draw(deps.worldRepo, drawDelay)
        val chargeSound = deps.random.pick(CHARGE_SOUNDS)
        for (player in OcellusArena.players(npc, deps.playerList)) {
            player.soundSynth(chargeSound, delay = drawDelay)
        }
        val encounter = deps.encounterRegistry.of(npc)
        val epoch = encounter.epoch
        deps.worldQueues.add(resolveTicks) {
            onResolved()
            val live = deps.encounterRegistry.isActive(encounter) && encounter.epoch == epoch
            if (npc.isSlotAssigned && live) resolve(npc, tendril)
        }
    }

    private fun resolve(npc: Npc, tendril: OcellusTendril) {
        tendril.resolve(deps.worldRepo, deps.random.pick(RESOLVE_SOUNDS))
        npc.spotanim("spotanim.vfx_ocellus_p1_charge")
        meter.charge(npc, tendril)
        for (player in OcellusArena.players(npc, deps.playerList)) {
            if (player.coords !in tendril.tiles) continue
            val damage = deps.random.of(DAMAGE)
            player.queueHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
    }

    companion object {
        const val CORNER = "ocellus.tendril_corner"
        const val CENTRE = "ocellus.tendril_centre"

        private const val ACTIVE = OcellusVarns.ACTIVE_TENDRILS
        private const val DELAYED_DRAW = 60
        private const val CORNER_RESOLVE = 5
        private const val CENTRE_FIRST_RESOLVE = 4
        private const val CENTRE_SECOND_RESOLVE = 6
        private val DAMAGE = 3..5
        private val CHARGE_SOUNDS =
            listOf("synth.ocellus_tendril_charge_01", "synth.ocellus_tendril_charge_02")
        private val RESOLVE_SOUNDS =
            listOf("synth.ocellus_tendril_resolve_01", "synth.ocellus_tendril_resolve_02")
    }
}
