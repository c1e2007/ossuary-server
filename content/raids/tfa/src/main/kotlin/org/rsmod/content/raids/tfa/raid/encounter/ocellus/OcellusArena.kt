package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal object OcellusArena {
    val CORNERS: List<Pair<Int, Int>> = listOf(6 to 9, -6 to 9, -6 to -3, 6 to -3)

    private const val CENTRE_DX = 0
    private const val CENTRE_DZ = 3
    private const val MIDDLE_DX = 1
    private const val MIDDLE_DZ = 4
    private const val ARENA_RADIUS = 13
    private const val SW_DX = MIDDLE_DX - ARENA_RADIUS
    private const val SW_DZ = MIDDLE_DZ - ARENA_RADIUS
    private const val NE_DX = MIDDLE_DX + ARENA_RADIUS
    private const val NE_DZ = MIDDLE_DZ + ARENA_RADIUS
    private const val FLOOR_MIN = 2
    private const val FLOOR_MAX = 12
    private const val FLOOR_REACH = 16

    val CENTRE: TargetExpr.Single = spawnTile(CENTRE_DX, CENTRE_DZ)

    val MIDDLE: TargetExpr.Single = spawnTile(MIDDLE_DX, MIDDLE_DZ)

    val AREA: Area = area(spawnTile(SW_DX, SW_DZ), spawnTile(NE_DX, NE_DZ))

    val FLOOR: Area =
        area(
            spawnTile(MIDDLE_DX - FLOOR_MAX, MIDDLE_DZ - FLOOR_MAX),
            spawnTile(MIDDLE_DX + FLOOR_MAX, MIDDLE_DZ + FLOOR_MAX),
        )

    fun centre(npc: Npc): CoordGrid = npc.spawnCoords.translate(CENTRE_DX, CENTRE_DZ)

    fun middle(npc: Npc): CoordGrid = npc.spawnCoords.translate(MIDDLE_DX, MIDDLE_DZ)

    fun quadrantPlayers(npc: Npc, playerList: PlayerList): List<Player> =
        players(npc, playerList).filter { sameQuadrantFloor(npc, it.coords) }

    fun sameQuadrantFloor(npc: Npc, tile: CoordGrid): Boolean {
        val middle = npc.spawnCoords.translate(MIDDLE_DX, MIDDLE_DZ)
        val boss = npc.coords.translate(npc.size / 2, npc.size / 2)
        val dx = tile.x - middle.x
        val dz = tile.z - middle.z
        return abs(dx) in FLOOR_MIN..FLOOR_MAX &&
            abs(dz) in FLOOR_MIN..FLOOR_MAX &&
            abs(dx) + abs(dz) <= FLOOR_REACH &&
            !(abs(dx) == FLOOR_MIN && abs(dz) == FLOOR_MIN) &&
            dx.sign == (boss.x - middle.x).sign &&
            dz.sign == (boss.z - middle.z).sign
    }

    fun contains(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(SW_DX, SW_DZ)
        val ne = origin.translate(NE_DX, NE_DZ)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    fun players(npc: Npc, playerList: PlayerList): List<Player> {
        val sw = npc.spawnCoords.translate(SW_DX, SW_DZ)
        val ne = npc.spawnCoords.translate(NE_DX, NE_DZ)
        return playerList.filter {
            it.isValidTarget() &&
                it.coords.level == sw.level &&
                it.coords.x in sw.x..ne.x &&
                it.coords.z in sw.z..ne.z
        }
    }
}

internal object OcellusVarns {
    const val ARRIVED_AT = "varn.ocellus_arrived_at"
    const val VOLLEY_STYLE = "varn.ocellus_volley_style"
    const val CORNER = "varn.ocellus_corner"
    const val CENTRE_SIDE = "varn.ocellus_centre_side"
    const val THRESHOLD = "varn.ocellus_threshold"

    /** Written to [THRESHOLD] by `::ocellus p3` to jump straight to phase 3 from any earlier phase. */
    const val SKIP_TO_PHASE_THREE = 99
    const val ACTIVE_TENDRILS = "varn.ocellus_active_tendrils"
    const val METER = "varn.ocellus_meter"
    const val BARRAGE_WAVES = "varn.ocellus_barrage_waves"
    const val SPECIAL_CYCLES = "varn.ocellus_special_cycles"
    const val RIFT_NEXT = "varn.ocellus_rift_next"
    const val RIFT_BUSY = "varn.ocellus_rift_busy"
}
