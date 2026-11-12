package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.soundSynth
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.BARRAGE_WAVES
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ScriptedProjAnim

internal class OcellusBarrage(private val deps: BossDeps, private val meter: OcellusMeter) {
    fun register() {
        deps.extensionRegistry.register(RELEASE) { meter.release(it.npc) }
        deps.extensionRegistry.register(FIRE) { fire(it.npc) }
    }

    private fun fire(npc: Npc) {
        val waves = npc.vars[BARRAGE_WAVES]
        val source = npc.coords.translate(1, 1)
        for (player in OcellusArena.players(npc, deps.playerList)) {
            val travel = travelCycles(source.chebyshevDistance(player.coords))
            repeat(waves) { wave -> fireWave(npc, player, wave, travel) }
        }
    }

    private fun fireWave(npc: Npc, player: Player, wave: Int, travel: Int) {
        val start = FIRST_WAVE + wave * WAVE_GAP
        val end = start + travel
        val proj =
            ScriptedProjAnim.fromNpcToPlayer(
                slot = deps.worldRepo.nextScriptedProjSlot(),
                spotanim = PROJ,
                source = npc,
                start = npc.coords.translate(1, 1),
                sourceHeight = SOURCE_HEIGHT,
                target = player,
                targetHeight = TARGET_HEIGHT,
                startTime = start,
                endTime = end,
                curveScripts = CURVE_SCRIPTS,
            )
        deps.worldRepo.scriptedProjAnim(proj)
        player.soundSynth(CAST_SOUND, delay = start)
        player.spotanim(IMPACT, delay = end, slot = wave)
        // Fired from a world queue, where a hit queued with delay N lands N - 1 ticks later.
        val landing = (end + CYCLES_PER_TICK / 2) / CYCLES_PER_TICK + 1
        player.queueHit(npc, landing, HitType.Typeless, deps.random.of(DAMAGE), deps.playerHitModifier)
    }

    companion object {
        const val RELEASE = "ocellus.meter_release"
        const val FIRE = "ocellus.barrage"

        private const val FIRST_WAVE = 2
        private const val WAVE_GAP = 6
        private const val CYCLES_PER_TICK = 30
        private const val SOURCE_HEIGHT = 500
        private const val TARGET_HEIGHT = 124
        private const val NEAR_RANGE = 4
        private const val NEAR_TRAVEL = 37
        private const val TRAVEL_PER_TILE = 15
        private val DAMAGE = 17..19

        private const val IMPACT = "spotanim.vfx_human_ocellus_voidflare_heal"
        private const val CAST_SOUND = "synth.ancient_spell_activate"
        private val PROJ = "spotanim.ocellus_proj_test".asRSCM(RSCMType.SPOTANIM)
        private val CURVE_SCRIPTS =
            Triple(
                "clientscript.ocellus_proj_curve_height".asRSCM(RSCMType.CLIENTSCRIPT),
                "clientscript.ocellus_proj_curve_angle".asRSCM(RSCMType.CLIENTSCRIPT),
                "clientscript.ocellus_proj_curve_time".asRSCM(RSCMType.CLIENTSCRIPT),
            )

        /** 37 cycles out to 4 tiles (seen up to 5: 52); beyond that extrapolated per tile. */
        fun travelCycles(distance: Int): Int =
            NEAR_TRAVEL + TRAVEL_PER_TILE * maxOf(0, distance - NEAR_RANGE)
    }
}
