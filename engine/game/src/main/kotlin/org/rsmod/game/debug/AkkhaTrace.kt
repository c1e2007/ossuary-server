package org.rsmod.game.debug

import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * TEMPORARY debug trace for Akkha's chase (one test run). Prints `[akkha-trace]` lines to the
 * server log. Remove this file and its call sites after the test.
 */
public object AkkhaTrace {
    private fun Npc.tracked(): Boolean = visType.name == "Akkha"

    private fun Npc.footprint(at: CoordGrid): String =
        "x${at.x}..${at.x + size - 1} z${at.z}..${at.z + size - 1}"

    private fun Npc.playerTile(): CoordGrid? =
        (interaction as? InteractionPlayer)?.target?.coords

    /** One call per movement step Akkha attempts (`to` is NULL when the step was refused). */
    public fun step(npc: Npc, from: CoordGrid, to: CoordGrid, flags: CollisionFlagMap) {
        if (!npc.tracked()) return
        val player = npc.playerTile()
        val kind =
            if (to == CoordGrid.NULL) {
                "BLOCKED"
            } else {
                val dx = to.x - from.x
                val dz = to.z - from.z
                if (dx != 0 && dz != 0) "diagonal($dx,$dz)" else "cardinal($dx,$dz)"
            }
        val playerFlag =
            player?.let { (flags[it.x, it.z, it.level] and CollisionFlag.BLOCK_NPCS) != 0 }
        val after = if (to == CoordGrid.NULL) from else to
        println(
            "[akkha-trace] t=${npc.currentMapClock} STEP $kind akkha=(${from.x},${from.z})" +
                "->(${after.x},${after.z}) footprintBefore=[${npc.footprint(from)}] " +
                "footprintAfter=[${npc.footprint(after)}] player=${player?.let { "(${it.x},${it.z})" }} " +
                "playerTileHasBLOCK_NPCS=$playerFlag"
        )
    }

    /** Which interaction phase fired: pre, post, post-skipped, under-target. */
    public fun phase(npc: Npc, phase: String, detail: String) {
        if (!npc.tracked()) return
        val player = npc.playerTile()
        println(
            "[akkha-trace] t=${npc.currentMapClock} PHASE $phase $detail akkha=(${npc.coords.x}," +
                "${npc.coords.z}) player=${player?.let { "(${it.x},${it.z})" }} " +
                "movedThisCycle=${npc.hasMovedThisCycle}"
        )
    }

    /** A melee hit was rolled and queued against [target]. */
    public fun hit(npc: Npc, target: CoordGrid, damage: Int) {
        if (!npc.tracked()) return
        println(
            "[akkha-trace] t=${npc.currentMapClock} HIT-QUEUED akkha=(${npc.coords.x},${npc.coords.z}) " +
                "target=(${target.x},${target.z}) damage=$damage"
        )
    }
}
