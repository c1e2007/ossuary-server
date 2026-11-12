package org.rsmod.content.raids.tfa.raid.encounter.guardian

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.LineValidator

internal object GuardianVarns {
    const val SHIELD_MAX = "varn.guardian_shield_max"
    const val GROUND_MAX = "varn.guardian_ground_max"
    const val HEALTH = "varn.guardian_health"
    const val LANDINGS = "varn.guardian_landings"
    const val BATTERY_HP = "varn.guardian_battery_hp"
    const val BATTERY_HIT = "varn.guardian_battery_hit"
    const val ENRAGED = "varn.guardian_enraged"
}

internal object GuardianArena {
    private const val SW_DX = -11
    private const val SW_DZ = -26
    private const val NE_DX = 15
    private const val NE_DZ = 13
    private const val SIZE = 5
    private const val HALF = SIZE / 2
    private const val SWOOP_STOP_GAP = 3
    private const val STACK_VINE_MIN = 2
    private const val BEAM_REACH = 18.0
    private const val BEAM_HALF_WIDTH = 5.0

    const val REACH = 30
    const val ENTRY_X = 4127
    const val ENTRY_Z = 5571
    const val SPAWN_X = 4125
    const val SPAWN_Z = 5597

    val AREA: Area = area(spawnTile(SW_DX, SW_DZ), spawnTile(NE_DX, NE_DZ))

    val BATTERY_OFFSETS: List<Pair<Int, Int>> = listOf(14 to 8, 14 to -5, -11 to 8, -11 to -5)

    fun contains(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(SW_DX, SW_DZ)
        val ne = origin.translate(NE_DX, NE_DZ)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    fun players(origin: CoordGrid, playerList: PlayerList): List<Player> =
        playerList.filter { it.isValidTarget() && contains(origin, it.coords) }

    fun centre(npc: Npc): CoordGrid = npc.coords.translate(HALF, HALF)

    fun ringTile(
        centre: CoordGrid,
        distance: Int,
        random: GameRandom,
        lines: LineValidator,
    ): CoordGrid? {
        val ring = buildList {
            for (dx in -distance..distance) {
                for (dz in -distance..distance) {
                    if (maxOf(abs(dx), abs(dz)) != distance) continue
                    val tile = centre.translate(dx, dz)
                    if (lines.hasLineOfWalk(centre.level, centre.x, centre.z, tile.x, tile.z)) {
                        add(dx to dz)
                    }
                }
            }
        }
        if (ring.isEmpty()) return null
        val (dx, dz) = ring[random.of(ring.size)]
        return centre.translate(dx, dz)
    }

    fun vineTiles(npc: Npc, playerList: PlayerList): List<CoordGrid> {
        val boss = centre(npc)
        return players(npc.spawnCoords, playerList)
            .groupingBy { it.coords }
            .eachCount()
            .flatMap { (tile, count) ->
                val away = tile.translate((tile.x - boss.x).sign, (tile.z - boss.z).sign)
                if (count >= STACK_VINE_MIN) listOf(tile, away) else listOf(away)
            }
    }

    fun swoopLanding(npc: Npc, target: Player): CoordGrid {
        val from = centre(npc)
        val dx = target.coords.x - from.x
        val dz = target.coords.z - from.z
        val distance = maxOf(abs(dx), abs(dz)).coerceAtLeast(1)
        val travel = (distance - HALF - SWOOP_STOP_GAP).coerceAtLeast(0)
        val stopX = from.x + (dx * travel / distance.toDouble()).roundToInt()
        val stopZ = from.z + (dz * travel / distance.toDouble()).roundToInt()
        return CoordGrid(stopX - HALF, stopZ - HALF, from.level)
    }

    fun behindSw(npc: Npc, target: Player): CoordGrid {
        val dx = target.coords.x - centre(npc).x
        val dz = target.coords.z - centre(npc).z
        if (dx == 0 && dz == 0) return npc.coords.translate(0, SIZE)
        val scale = SIZE.toDouble() / maxOf(abs(dx), abs(dz))
        return npc.coords.translate(-(dx * scale).roundToInt(), -(dz * scale).roundToInt())
    }

    fun behindNe(npc: Npc, target: Player): CoordGrid =
        behindSw(npc, target).translate(SIZE - 1, SIZE - 1)

    fun isUnder(npc: Npc, player: Player): Boolean =
        player.coords.level == npc.coords.level &&
            player.coords.x in npc.coords.x until npc.coords.x + SIZE &&
            player.coords.z in npc.coords.z until npc.coords.z + SIZE

    /**
     * The charged battery the beam hits: the one closest to the line from the Guardian's centre
     * through the target, among those in front of the Guardian and within [BEAM_REACH]. Captured
     * breaks sit up to [BEAM_HALF_WIDTH] tiles off that line and up to 18 tiles along it, and the
     * pick follows the line rather than distance to the player.
     */
    fun beamBattery(boss: Npc, target: Player, owned: List<Npc>, batteryType: Int): Npc? {
        val from = centre(boss)
        val dx = target.coords.x - from.x
        val dz = target.coords.z - from.z
        val length = hypot(dx.toDouble(), dz.toDouble())
        if (length == 0.0) return null
        return owned
            .filter { it.visType.id == batteryType }
            .mapNotNull { battery ->
                val rx = battery.coords.x + battery.size / 2 - from.x
                val rz = battery.coords.z + battery.size / 2 - from.z
                val along = (rx * dx + rz * dz) / length
                val across = abs(rx * dz - rz * dx) / length
                if (along <= 0 || along > BEAM_REACH || across > BEAM_HALF_WIDTH) null
                else battery to across
            }
            .minByOrNull { it.second }
            ?.first
    }
}
