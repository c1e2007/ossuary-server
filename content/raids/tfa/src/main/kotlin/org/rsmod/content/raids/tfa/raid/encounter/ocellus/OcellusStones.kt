package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class OcellusStones @Inject constructor(private val encounter: OcellusEncounter) : PluginScript() {
    override fun ScriptContext.startup() {
        for (stone in STONES) onApLoc1(stone) { apJump(it.loc) }
    }

    private suspend fun ProtectedAccess.apJump(stone: BoundLocInfo) {
        if (isWithinApRange(stone, distance = STONE_AP_RANGE)) jump(stone.coords)
    }

    private suspend fun ProtectedAccess.jump(stone: CoordGrid) {
        val centre = encounter.at(coords)?.centre ?: return
        val alongZ = stone.x == centre.x
        val offset = { tile: CoordGrid -> if (alongZ) tile.z - centre.z else tile.x - centre.x }
        val onArm = if (alongZ) coords.x == centre.x else coords.z == centre.z
        when {
            !onArm -> enter(stone, centre, alongZ, offset)
            coords == stone -> mes("You are already standing on this stone.")
            else -> advance(stone, centre, alongZ, offset(coords), offset)
        }
    }

    /**
     * The side of the arm a crossing starts from. It runs round the room one way: west to east on
     * the south arm, south to north on the east arm, east to west on the north arm and north to
     * south on the west arm.
     */
    private fun entrySide(alongZ: Boolean, offset: Int): Int =
        if (alongZ) (if (offset < 0) -1 else 1) else (if (offset < 0) 1 else -1)

    private suspend fun ProtectedAccess.enter(
        stone: CoordGrid,
        centre: CoordGrid,
        alongZ: Boolean,
        offset: (CoordGrid) -> Int,
    ) {
        val side = if (alongZ) (coords.x - centre.x).sign else (coords.z - centre.z).sign
        if (abs(offset(stone)) != INNER_STONE) {
            mes("You need to start from the stone nearest the middle.")
            return
        }
        if (side != entrySide(alongZ, offset(stone))) {
            mes("You can only cross the gap from the other side.")
            return
        }
        hop(stone)
    }

    private suspend fun ProtectedAccess.advance(
        stone: CoordGrid,
        centre: CoordGrid,
        alongZ: Boolean,
        here: Int,
        offset: (CoordGrid) -> Int,
    ) {
        val target = offset(stone)
        if (target.sign != here.sign || abs(target) != abs(here) + STONE_SPACING) {
            mes("You can't reach that stone.")
            return
        }
        hop(stone)
        if (abs(target) != OUTER_STONE) return
        delay(HOP_TICKS)
        val side = -entrySide(alongZ, target) * LANDING_OFFSET
        hop(
            if (alongZ) CoordGrid(centre.x + side, stone.z, stone.level)
            else CoordGrid(stone.x, centre.z + side, stone.level)
        )
    }

    private fun ProtectedAccess.hop(to: CoordGrid) {
        exactMove(coords, to, delay1 = 0, delay2 = HOP_CYCLES, dir = facing(coords, to))
        anim("seq.human_yama_shortjump01")
        soundSynth(JUMP_SOUND)
    }

    private fun facing(from: CoordGrid, to: CoordGrid): Int {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return when {
            abs(dx) >= abs(dz) -> if (dx > 0) Constants.em_face_east else Constants.em_face_west
            else -> if (dz > 0) Constants.em_face_north else Constants.em_face_south
        }
    }

    private companion object {
        val STONES =
            listOf(
                "loc.tfa_ocellus_platform_jump_middle",
                "loc.tfa_ocellus_platform_jump_outer_east",
                "loc.tfa_ocellus_platform_jump_outer_north",
                "loc.tfa_ocellus_platform_jump_outer_south",
                "loc.tfa_ocellus_platform_jump_outer_west",
            )

        const val STONE_AP_RANGE = 4
        const val STONE_SPACING = 3
        const val LANDING_OFFSET = 2
        const val INNER_STONE = 4
        const val OUTER_STONE = 10

        const val HOP_TICKS = 1
        const val HOP_CYCLES = 27

        const val JUMP_SOUND = "synth.varrock_fence_jump"
    }
}
