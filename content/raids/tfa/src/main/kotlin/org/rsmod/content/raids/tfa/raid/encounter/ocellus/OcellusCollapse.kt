package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.add
import org.rsmod.game.map.collision.remove
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

internal class OcellusCollapse(
    private val deps: BossDeps,
    private val clearWalls: () -> Unit,
) {
    private val spawned = ArrayList<LocInfo>()
    private val ends = ArrayList<Pair<ArmEnd, LocInfo>>()
    val stoneLocs = HashMap<CoordGrid, LocInfo>()

    fun register() {
        deps.extensionRegistry.register(SWORD_RAISE) { sword(centre(it.npc), SWORD_IDLE_FIRST) }
        deps.extensionRegistry.register(SWORD_IDLE) { sword(centre(it.npc), SWORD_IDLE_SECOND) }
        deps.extensionRegistry.register(SWORD_DROP) { sword(centre(it.npc), SWORD_GONE) }
        deps.extensionRegistry.register(CRACK) { crack(centre(it.npc)) }
        deps.extensionRegistry.register(COLLAPSE) { collapse(it.npc, centre(it.npc)) }
        deps.extensionRegistry.register(GAP_WALLS) { gapWalls(centre(it.npc)) }
    }

    fun clear() {
        for (loc in spawned) deps.locRepo.del(loc, Int.MAX_VALUE)
        spawned.clear()
        ends.clear()
        for (tile in stoneLocs.keys) deps.collision.remove(tile, CollisionFlag.BLOCK_WALK)
        stoneLocs.clear()
    }

    /**
     * A ray cast fails outright when it starts on a tile with the `LOC` flag, which would stop a
     * player on a stone clicking the next one. The arm-end locs cover every stone with it, so this
     * runs after they are swapped for their empty variants. The stone stays unwalkable.
     */
    private fun makeStandable(tile: CoordGrid) {
        deps.collision.remove(tile, CollisionFlag.LOC or CollisionFlag.LOC_ROUTE_BLOCKER)
        deps.collision.add(tile, CollisionFlag.BLOCK_WALK)
    }

    fun record(info: LocInfo) {
        spawned.add(info)
    }

    private fun centre(npc: Npc): CoordGrid = npc.spawnCoords.translate(CENTRE_DX, CENTRE_DZ)

    private fun add(
        at: CoordGrid,
        loc: String,
        shape: LocShape = LocShape.CentrepieceStraight,
        rotation: Int = 0,
    ): LocInfo {
        val info = deps.locRepo.add(at, loc, Int.MAX_VALUE, LocAngle[rotation], shape)
        spawned.add(info)
        return info
    }

    private fun sword(centre: CoordGrid, loc: String) {
        add(centre, loc)
    }

    private fun crack(centre: CoordGrid) {
        ends.clear()
        for (end in ARM_ENDS) ends.add(end to add(centre.translate(end.dx, end.dz), end.loc))
        add(centre.translate(BLOCKER_DX, BLOCKER_DZ), BLOCKER)
        add(centre.translate(NONBLOCKING_DX, NONBLOCKING_DZ), NONBLOCKING_TYPE0, LocShape.GroundDecor)
    }

    private fun collapse(npc: Npc, centre: CoordGrid) {
        clearWalls()
        for ((end, loc) in ends) deps.worldRepo.locAnim(loc, end.seq)
        explode(centre)
        val stones = STONES.associateBy { centre.translate(it.dx, it.dz) }
        stoneLocs.clear()
        for ((tile, stone) in stones) stoneLocs[tile] = add(tile, stone.loc, LocShape.GroundDecor)
        add(centre.translate(-2, -2), CENTRE_BLOCKER_5)
        add(centre.translate(-1, -1), CENTRE_BLOCKER_3)
        for (player in OcellusArena.players(npc, deps.playerList)) {
            val tile = player.coords
            val onVoid = (onLine(centre, tile) || inCentre(centre, tile)) && tile !in stones
            if (onVoid) player.queueHit(npc, 1, HitType.Typeless, player.hitpoints, deps.playerHitModifier)
        }
    }

    private fun explode(centre: CoordGrid) {
        explosion(centre, EXPLOSIONS.first(), 0)
        for ((dx, dz) in ARMS) {
            ARM_EXPLOSIONS.forEachIndexed { i, (spotanim, delay) ->
                val distance = EXPLOSION_SPACING * (i + 1)
                explosion(centre.translate(dx * distance, dz * distance), spotanim, delay)
            }
        }
    }

    private fun explosion(at: CoordGrid, spotanim: String, delay: Int) {
        deps.worldRepo.spotanimMap(SpotanimType(spotanim.asRSCM(RSCMType.SPOTANIM)), at, delay = delay)
    }

    private fun gapWalls(centre: CoordGrid) {
        for (offset in GAP_RANGE) {
            add(centre.translate(-1, offset), BLOCKRANGE, LocShape.WallStraight, 0)
            add(centre.translate(1, offset), BLOCKRANGE, LocShape.WallStraight, 2)
            add(centre.translate(offset, -1), BLOCKRANGE, LocShape.WallStraight, 3)
            add(centre.translate(offset, 1), BLOCKRANGE, LocShape.WallStraight, 1)
        }
        for ((dx, dz) in NONBLOCKING_WALLS) {
            add(centre.translate(dx, dz), NONBLOCKING_WALL, LocShape.WallStraight, 0)
        }
        for (end in ARM_ENDS) add(centre.translate(end.dx, end.dz), end.emptyLoc)
        for (tile in stoneLocs.keys) makeStandable(tile)
    }

    private fun onLine(centre: CoordGrid, tile: CoordGrid): Boolean =
        tile.level == centre.level &&
            ((tile.x == centre.x && tile.z in centre.z - ARM_LENGTH..centre.z + ARM_LENGTH) ||
                (tile.z == centre.z && tile.x in centre.x - ARM_LENGTH..centre.x + ARM_LENGTH))

    private fun inCentre(centre: CoordGrid, tile: CoordGrid): Boolean =
        tile.level == centre.level &&
            tile.x in centre.x - CENTRE_RADIUS..centre.x + CENTRE_RADIUS &&
            tile.z in centre.z - CENTRE_RADIUS..centre.z + CENTRE_RADIUS

    private class ArmEnd(
        val dx: Int,
        val dz: Int,
        val loc: String,
        val emptyLoc: String,
        val seq: String,
    )

    private class Stone(val dx: Int, val dz: Int, val loc: String)

    companion object {
        const val SWORD_RAISE = "ocellus.sword_raise"
        const val SWORD_IDLE = "ocellus.sword_idle"
        const val SWORD_DROP = "ocellus.sword_drop"
        const val CRACK = "ocellus.arena_crack"
        const val COLLAPSE = "ocellus.arena_collapse"
        const val GAP_WALLS = "ocellus.gap_walls"

        private const val CENTRE_DX = 1
        private const val CENTRE_DZ = 4
        private const val ARM_LENGTH = 12
        private const val CENTRE_RADIUS = 2
        private const val EXPLOSION_SPACING = 3
        private val GAP_RANGE = (-ARM_LENGTH..-2) + (2..ARM_LENGTH)

        private const val SWORD_IDLE_FIRST = "loc.tfa_the_blade1_idle0"
        private const val SWORD_IDLE_SECOND = "loc.tfa_the_blade1_idle2"
        private const val SWORD_GONE = "loc.invisible_type8_nonblocking"

        private const val BLOCKER = "loc.invisible_type8_blocking"
        private const val CENTRE_BLOCKER_5 = "loc.invisible_type8_blocking_size5"
        private const val CENTRE_BLOCKER_3 = "loc.invisible_type8_blocking_size3"
        private const val NONBLOCKING_TYPE0 = "loc.invisible_type0_nonblocking"
        private const val BLOCKRANGE = "loc.inviswall_blockrange"
        private const val NONBLOCKING_WALL = "loc.invisable_nonblocking_wall"
        private const val BLOCKER_DX = -1
        private const val BLOCKER_DZ = -15
        private const val NONBLOCKING_DX = 0
        private const val NONBLOCKING_DZ = -13

        private val ARMS = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

        private val EXPLOSIONS =
            listOf(
                "spotanim.tfa_arena_collapsing_explosion01",
                "spotanim.tfa_arena_collapsing_explosion02",
                "spotanim.tfa_arena_collapsing_explosion03",
                "spotanim.tfa_arena_collapsing_explosion04",
            )
        private val ARM_EXPLOSIONS =
            listOf(
                EXPLOSIONS[1] to 0,
                EXPLOSIONS[2] to 15,
                EXPLOSIONS[3] to 30,
                EXPLOSIONS[0] to 45,
            )

        private val ARM_ENDS =
            listOf(
                ArmEnd(
                    -1, -13,
                    "loc.tfa_wallkit_archive03_straight_collapse_01",
                    "loc.tfa_wallkit_archive03_straight_collapse_01_empty",
                    "seq.dungeonkit_guthixian03_cracking_collapsing_01",
                ),
                ArmEnd(
                    -13, -1,
                    "loc.tfa_wallkit_archive03_straight_collapse_02",
                    "loc.tfa_wallkit_archive03_straight_collapse_02_empty",
                    "seq.dungeonkit_guthixian03_cracking_collapsing_02",
                ),
                ArmEnd(
                    -1, 3,
                    "loc.tfa_wallkit_archive03_straight_collapse_03",
                    "loc.tfa_wallkit_archive03_straight_collapse_03_empty",
                    "seq.dungeonkit_guthixian03_cracking_collapsing_03",
                ),
                ArmEnd(
                    3, -1,
                    "loc.tfa_wallkit_archive03_straight_collapse_04",
                    "loc.tfa_wallkit_archive03_straight_collapse_04_empty",
                    "seq.dungeonkit_guthixian03_cracking_collapsing_04",
                ),
            )

        private val STONES =
            listOf(
                Stone(0, -4, "loc.tfa_ocellus_platform_jump_outer_west"),
                Stone(0, -7, "loc.tfa_ocellus_platform_jump_middle"),
                Stone(0, -10, "loc.tfa_ocellus_platform_jump_outer_east"),
                Stone(0, 4, "loc.tfa_ocellus_platform_jump_outer_east"),
                Stone(0, 7, "loc.tfa_ocellus_platform_jump_middle"),
                Stone(0, 10, "loc.tfa_ocellus_platform_jump_outer_west"),
                Stone(4, 0, "loc.tfa_ocellus_platform_jump_outer_south"),
                Stone(7, 0, "loc.tfa_ocellus_platform_jump_middle"),
                Stone(10, 0, "loc.tfa_ocellus_platform_jump_outer_north"),
                Stone(-4, 0, "loc.tfa_ocellus_platform_jump_outer_north"),
                Stone(-7, 0, "loc.tfa_ocellus_platform_jump_middle"),
                Stone(-10, 0, "loc.tfa_ocellus_platform_jump_outer_south"),
            )

        private val NONBLOCKING_WALLS =
            listOf(
                1 to -10,
                -1 to -4,
                10 to 1,
                1 to 4,
                4 to -1,
                -4 to 1,
                -10 to -1,
                -1 to 10,
            )
    }
}
