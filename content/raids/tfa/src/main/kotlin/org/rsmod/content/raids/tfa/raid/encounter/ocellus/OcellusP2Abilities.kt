package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.PHASE_THREE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.QUADRANTS
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.TELEPORT_SPOT_SLOT

internal class OcellusP2Abilities {
    /** Everything is timed from the transform tick; the walls run in three windows around the pools. */
    fun phaseTwo(): Effect =
        parallel(
            sequence(
                wait(WALLS_SPAWN_AT),
                external(OcellusPhaseTwo.WALLS_SPAWN),
                toPlayers(soundTo("synth.ocellus_p2_walls_spawn")),
            ),
            sequence(wait(1), external(OcellusPhaseTwo.SET_LEVEL)),
            sequence(
                wait(OPEN_AT),
                oneOf(PHASE_TWO_LINES.map(::say)),
                external(OcellusPhaseTwo.OPEN),
                external(OcellusPhaseTwo.SWORD_RAISE),
            ),
            sequence(wait(SWORD_IDLE_AT), external(OcellusPhaseTwo.SWORD_IDLE)),
            *WALL_WINDOWS.map(::wallWindow).toTypedArray(),
            *ROTATIONS.map(::rotation).toTypedArray(),
            discharges(),
            ending(),
        )

    private fun ending(): Effect =
        sequence(
            wait(ENDING_AT),
            spotanim("spotanim.vfx_ocellus_p2_to_p3_arm_glow01", delay = ARM_GLOW_DELAY),
            spotanim("spotanim.vfx_ocellus_sword_root_fire", slot = ROOT_FIRE_SLOT),
            spotanim("spotanim.vfx_ocellus_p2_to_p3_arm01", delay = ARM_GLOW_DELAY, slot = TELEPORT_SPOT_SLOT),
            anim("seq.npc_ocellus_p2_to_p3_part1"),
            say("This... is enough..."),
            external(OcellusPhaseTwo.FINISH),
            external(OcellusPhaseTwo.SWORD_DROP),
            mapSpotanim("spotanim.vfx_ocellus_sword_drop", OcellusArena.MIDDLE),
            wait(1),
            mapSpotanim("spotanim.tfa_arena_sword_drop_explosion", OcellusArena.MIDDLE),
            wait(1),
            external(OcellusPhaseTwo.WALLS_DESPAWN),
            wait(CRACK_AT - ENDING_AT - 2),
            external(OcellusPhaseTwo.ARENA_CRACK),
            wait(PHASE_THREE_AT - CRACK_AT),
            spotanim("spotanim.vfx_ocellus_null", height = NULL_SPOT_HEIGHT, delay = NULL_SPOT_DELAY),
            spotanim(
                "spotanim.vfx_ocellus_null",
                height = NULL_SPOT_HEIGHT,
                delay = NULL_SPOT_DELAY,
                slot = TELEPORT_SPOT_SLOT,
            ),
            anim("seq.npc_ocellus_p2_to_p3_part2"),
            transmog("npc.tfa_ocellus_p3", Int.MAX_VALUE),
            external(OcellusPhaseTwo.PHASE_THREE),
            wait(1),
            external(OcellusPhaseTwo.ARENA_COLLAPSE),
            wait(GAP_WALLS_AT - PHASE_THREE_AT - 1),
            external(OcellusPhaseTwo.GAP_WALLS),
            transitionTo(QUADRANTS.first()),
            run(PHASE_THREE),
        )

    /** Testing shortcut: everything phase 2's ending leaves behind, without waiting for it. */
    fun skipToPhaseThree(): Effect =
        sequence(
            external(OcellusPhaseTwo.WALLS_CLEAR),
            external(OcellusPhaseTwo.CLOSE),
            external(OcellusPhaseTwo.FINISH),
            external(OcellusPhaseTwo.ARENA_CRACK),
            transmog("npc.tfa_ocellus_p3", Int.MAX_VALUE),
            external(OcellusPhaseTwo.PHASE_THREE),
            wait(1),
            external(OcellusPhaseTwo.ARENA_COLLAPSE),
            wait(1),
            external(OcellusPhaseTwo.GAP_WALLS),
            transitionTo(QUADRANTS.first()),
            run(PHASE_THREE),
        )

    private fun rotation(start: Int): Effect =
        parallel(
            sequence(
                wait(start),
                external(OcellusPhaseTwo.POOLS),
                toPlayers(
                    sequence(
                        soundTo("synth.ocellus_p2_pool_hit"),
                        soundTo("synth.ocellus_p2_pool_echo", delay = POOL_ECHO_DELAY),
                    )
                ),
            ),
            sequence(
                wait(start + CLOSED_TELEGRAPH_AT),
                external(OcellusPhaseTwo.WALLS_CLOSED_TELEGRAPH),
                telegraphSound(CLOSED_ECHO_DELAY),
                after(CLOSED_FIRE_DELAY, external(OcellusPhaseTwo.WALLS_FIRE)),
            ),
            sequence(wait(start + POOLS_DETONATE_AT), external(OcellusPhaseTwo.POOLS_DETONATE)),
            sequence(
                wait(start + PORTALS_AT),
                external(OcellusPhaseTwo.PORTALS_SPAWN),
                toPlayers(soundTo("synth.ocellus_p2_portal_spawn")),
            ),
            sequence(
                wait(start + PORTALS_AT + 1),
                repeat(PORTAL_TICKS, gap = 1, effect = external(OcellusPhaseTwo.PORTALS_TICK)),
            ),
            sequence(
                wait(start + PORTALS_AT + PORTAL_TICKS + 1),
                external(OcellusPhaseTwo.PORTALS_END),
            ),
        )

    private fun wallWindow(start: Int): Effect =
        sequence(
            wait(start),
            repeat(
                WALL_CYCLES,
                gap = WALL_CYCLE_GAP,
                effect =
                    sequence(
                        external(OcellusPhaseTwo.WALLS_TELEGRAPH),
                        telegraphSound(WALL_ECHO_DELAY),
                        after(WALL_FIRE_DELAY, external(OcellusPhaseTwo.WALLS_FIRE)),
                    ),
            ),
        )

    private fun telegraphSound(echoDelay: Int): Effect =
        toPlayers(
            sequence(
                soundTo("synth.ocellus_p2_telegraph"),
                soundTo("synth.ocellus_p2_telegraph_echo", delay = echoDelay),
            )
        )

    private fun toPlayers(effect: Effect): Effect = onEach(playersIn(OcellusArena.AREA), effect)

    private fun discharges(): Effect {
        val vfx =
            sequence(
                spotanim("spotanim.vfx_ocellus_p2_sword_discharge", delay = DISCHARGE_VFX_DELAY),
                sound("synth.ocellus_sword_discharge_vfx", radius = DISCHARGE_VFX_RADIUS, at = OcellusArena.MIDDLE),
            )
        val discharge =
            sequence(
                sound("synth.ocellus_sword_discharge_hit", radius = DISCHARGE_HIT_RADIUS, at = OcellusArena.MIDDLE),
                external(OcellusPhaseTwo.DISCHARGE),
            )
        return sequence(
            wait(FIRST_DISCHARGE_VFX),
            vfx,
            wait(1),
            discharge,
            wait(DISCHARGE_GAP - 1),
            vfx,
            wait(1),
            discharge,
            wait(LAST_DISCHARGE_GAP),
            external(OcellusPhaseTwo.CLOSE),
            sound("synth.ocellus_sword_discharge_vfx", radius = DISCHARGE_VFX_RADIUS, at = OcellusArena.MIDDLE),
            wait(1),
            discharge,
        )
    }

    private companion object {
        val PHASE_TWO_LINES =
            List(3) { "Your time... comes..." } + "Pointless..."
        const val WALLS_SPAWN_AT = 2
        const val OPEN_AT = 7
        val WALL_WINDOWS = listOf(11, 53, 95)
        val ROTATIONS = listOf(39, 81)
        const val SWORD_IDLE_AT = 39
        const val ENDING_AT = 123
        const val CRACK_AT = 127
        const val PHASE_THREE_AT = 131
        const val GAP_WALLS_AT = 135
        const val ARM_GLOW_DELAY = 150
        const val ROOT_FIRE_SLOT = 5
        const val NULL_SPOT_HEIGHT = 154
        const val NULL_SPOT_DELAY = 300
        const val WALL_ECHO_DELAY = 120
        const val CLOSED_ECHO_DELAY = 270
        const val POOL_ECHO_DELAY = 90
        const val DISCHARGE_VFX_RADIUS = 15
        const val DISCHARGE_HIT_RADIUS = 10
        const val CLOSED_TELEGRAPH_AT = 3
        const val CLOSED_FIRE_DELAY = 9
        const val POOLS_DETONATE_AT = 4
        const val PORTALS_AT = 5
        const val PORTAL_TICKS = 6
        const val WALL_CYCLES = 6
        const val WALL_CYCLE_GAP = 5
        const val WALL_FIRE_DELAY = 4
        const val FIRST_DISCHARGE_VFX = 45
        const val DISCHARGE_VFX_DELAY = 30
        const val DISCHARGE_GAP = 42
        const val LAST_DISCHARGE_GAP = 35
    }
}
