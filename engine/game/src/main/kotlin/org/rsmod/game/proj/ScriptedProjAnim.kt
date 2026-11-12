package org.rsmod.game.proj

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid

/**
 * A projectile whose flight path is computed client-side by the [curveScriptH] (height),
 * [curveScriptA] (angle) and [curveScriptT] (time) clientscripts instead of the fixed arc of
 * [ProjAnim]. [slot] identifies it for later changes and must be unique among live projectiles.
 */
public data class ScriptedProjAnim(
    public val slot: Int,
    public val spotanim: Int,
    public val startCoord: CoordGrid,
    public val sourceHeight: Int,
    public val sourceIndex: Int,
    public val endCoord: CoordGrid,
    public val targetHeight: Int,
    public val targetIndex: Int,
    public val startTime: Int,
    public val endTime: Int,
    public val curveScriptH: Int,
    public val curveScriptA: Int,
    public val curveScriptT: Int,
) {
    public val zoneGridX: Int
        get() = startCoord.x and ZoneGrid.X_BIT_MASK

    public val zoneGridZ: Int
        get() = startCoord.z and ZoneGrid.Z_BIT_MASK

    public companion object {
        public fun fromNpcToPlayer(
            slot: Int,
            spotanim: Int,
            source: Npc,
            start: CoordGrid,
            sourceHeight: Int,
            target: Player,
            targetHeight: Int,
            startTime: Int,
            endTime: Int,
            curveScripts: Triple<Int, Int, Int>,
        ): ScriptedProjAnim =
            ScriptedProjAnim(
                slot = slot,
                spotanim = spotanim,
                startCoord = start,
                sourceHeight = sourceHeight,
                sourceIndex = source.slotId + 1,
                endCoord = target.coords,
                targetHeight = targetHeight,
                targetIndex = -(target.slotId + 1),
                startTime = startTime,
                endTime = endTime,
                curveScriptH = curveScripts.first,
                curveScriptA = curveScripts.second,
                curveScriptT = curveScripts.third,
            )
    }
}
