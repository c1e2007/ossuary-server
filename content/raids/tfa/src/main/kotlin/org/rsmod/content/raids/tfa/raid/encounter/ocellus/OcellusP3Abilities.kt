package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.VarExpr
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.FADE_IN_START
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.INVISIBLE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.OPAQUE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.QUADRANTS
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.TELEPORT_SPOT_SLOT

internal class OcellusP3Abilities(private val encounter: OcellusEncounter) {
    fun phaseThree(): Effect =
        sequence(
            lockMovement(),
            nextAttackIn(ATTACK_HOLD),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = OPAQUE, to = INVISIBLE),
            wait(QUADRANT_ENTER_AT - 1),
            external(OcellusPhaseTwo.QUADRANT_FREEZE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_ENTER),
            voidBombs(),
            nextAttackIn(ENTER_TO_FIRST_ATTACK),
            enterQuadrant(0),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = INVISIBLE, to = OPAQUE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_THAW),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_SPAWN),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER),
            unlockMovement(),
        )

    /**
     * Forked off the boss's arrival in a quadrant, so it runs beside its first attacks: the marks
     * go up a tick in, hold for two, and a tick later the bombs show where they will fall and
     * land two ticks after that.
     */
    private fun voidBombs(): Effect {
        val tiles =
            customTiles(OcellusArena.FLOOR) { npc, _, _ ->
                encounter.of(npc)?.phaseTwo?.bombTiles(npc).orEmpty()
            }
        val idle =
            sequence(
                mapSpotanim(BOMB_IDLE_SPOT, CurrentTile, height = BOMB_HEIGHT),
                mapSpotanim(BOMB_IDLE_SPOT, CurrentTile, height = BOMB_HEIGHT, delay = BOMB_IDLE_LOOP_DELAY),
            )
        val land =
            sequence(
                mapSpotanim(BOMB_DROP_SPOT, CurrentTile, height = BOMB_HEIGHT),
                mapSpotanim(BOMB_IMPACT_SPOT, CurrentTile),
                external(OcellusVoid.BOMB, at = CurrentTile),
            )
        return after(
            BOMB_MARK_AT,
            sequence(
                external(OcellusPhaseTwo.BOMBS_MARK),
                wait(1),
                external(OcellusPhaseTwo.BOMBS_IDLE),
                wait(1),
                external(OcellusPhaseTwo.BOMBS_IDLE),
                wait(1),
                withTiles(
                    BOMB_TILES,
                    tiles,
                    sequence(
                        onTiles(bound(BOMB_TILES), idle),
                        wait(BOMB_IDLE_TO_DROP),
                        onTiles(bound(BOMB_TILES), land),
                    ),
                ),
            ),
        )
    }

    private fun enterQuadrant(quadrant: Int): Effect =
        sequence(
            setVarn(OcellusVarns.SPECIAL_CYCLES, 0),
            setVarn(OcellusVarns.RIFT_BUSY, 0),
            external(OcellusPhaseTwo.RIFTS_RESET, params = RIFT_KINDS[quadrant]),
            if (RIFT_KINDS[quadrant] < 0) setVarn(OcellusVarns.RIFT_NEXT, 0)
            else setVarn(OcellusVarns.RIFT_NEXT, VarExpr.Now + RIFT_FIRST_AT),
        )

    /** Opens the next pair of rifts once its time has come, but never while a special is running. */
    fun riftWave(quadrant: Int): Effect {
        val due = varnExpired(OcellusVarns.RIFT_NEXT) and varnIs(OcellusVarns.RIFT_BUSY, 0)
        val gap = if (quadrant == QUADRANTS.lastIndex) RIFT_LAST_GAP else RIFT_PAIR_GAP
        return whenever(
            due,
            sequence(
                external(OcellusPhaseTwo.RIFTS_OPEN),
                setVarn(OcellusVarns.RIFT_NEXT, VarExpr.Now + gap),
            ),
        )
    }

    fun destroyQuadrant(next: Int): Effect =
        sequence(
            lockMovement(),
            external(OcellusPhaseTwo.RIFTS_STOP),
            setVarn(OcellusVarns.RIFT_NEXT, 0),
            external(OcellusPhaseTwo.VINES_CLEAR),
            nextAttackIn(ATTACK_HOLD),
            external(OcellusSlash.RELEASE),
            anim("seq.npc_ocellus_quadrant_destroy_in"),
            idleAnim("seq.npc_ocellus_quadrant_destroy_loop"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_in", slot = TELEPORT_SPOT_SLOT),
            headbar(DESTROY_BAR, fromPercent = 100, toPercent = 0, cycles = DESTROY_BAR_CYCLES),
            oneOf(say("Run... It matters not..."), say("Obliteration...")),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_DESPAWN),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_REMOVE),
            wait(1),
            anim("seq.npc_ocellus_quadrant_destroy_loop"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_loop", slot = TELEPORT_SPOT_SLOT),
            wait(DESTROY_OUT_AT - 2),
            clearHeadbar(DESTROY_BAR),
            clearIdleAnim(),
            anim("seq.npc_ocellus_phase3_unleash_out_90f"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_out_01", slot = TELEPORT_SPOT_SLOT),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_out_02", slot = COLLAPSE_OUT_SLOT),
            oneOf(say("Everything... ends..."), say("You can't... escape... the end...")),
            wait(1),
            transparency(FADE_IN_START, DESTROY_FADE_END, from = OPAQUE, to = INVISIBLE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_COLLAPSE),
            wait(DESTROY_MOVE_AT - DESTROY_COLLAPSE_AT - 1),
            external(OcellusPhaseTwo.QUADRANT_FREEZE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_ENTER),
            voidBombs(),
            nextAttackIn(ENTER_TO_FIRST_ATTACK),
            enterQuadrant(next),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = INVISIBLE, to = OPAQUE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_THAW),
            external(OcellusPhaseTwo.QUADRANT_STONES_FALL),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_SPAWN),
            if (next == QUADRANTS.lastIndex) say("End...") else Effect.NoOp,
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_STONES_BLOCK),
            unlockMovement(),
        )

    private companion object {
        const val BOMB_TILES = "bombs"
        const val BOMB_MARK_AT = 1
        const val BOMB_IDLE_TO_DROP = 2
        const val BOMB_IDLE_LOOP_DELAY = 30
        const val BOMB_HEIGHT = 255
        const val BOMB_IDLE_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_idle_01"
        const val BOMB_DROP_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_drop_01"
        const val BOMB_IMPACT_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_impact_01"
        val RIFT_KINDS = listOf(-1, 0, 1, 2)
        const val RIFT_FIRST_AT = 3
        const val RIFT_PAIR_GAP = 48
        const val RIFT_LAST_GAP = 60
        const val QUADRANT_ENTER_AT = 2
        const val ATTACK_HOLD = 30
        const val ENTER_TO_FIRST_ATTACK = 4
        const val QUADRANT_FADE_END = 28
        const val DESTROY_BAR = "headbar.ocellus_quadrant_destroy"
        const val DESTROY_BAR_CYCLES = 330
        const val DESTROY_OUT_AT = 11
        const val DESTROY_COLLAPSE_AT = 13
        const val DESTROY_MOVE_AT = 17
        const val DESTROY_FADE_END = 30
        const val COLLAPSE_OUT_SLOT = 4
    }
}
