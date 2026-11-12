package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.map.CoordGrid

class OcellusTendril
private constructor(
    private val start: CoordGrid,
    private val dx: Int,
    private val dz: Int,
    length: Int,
    soundSteps: Int,
) {
    val tiles: List<CoordGrid> = List(length) { start.translate(dx * it, dz * it) }

    private val soundTile: CoordGrid = tiles.last().translate(dx * soundSteps, dz * soundSteps)

    private val crack: String
        get() = if (dx == 0 || dz == 0) CRACK_STRAIGHT else CRACK_DIAGONAL

    private val rotation: String
        get() =
            when {
                dx == 0 -> if (dz > 0) "" else "_180"
                dz == 0 -> if (dx > 0) "_90" else "_270"
                dx > 0 -> if (dz > 0) "" else "_90"
                else -> if (dz < 0) "_180" else "_270"
            }

    fun draw(repo: WorldRepository, delay: Int) {
        tiles.forEachIndexed { i, tile ->
            val spotanim = if (i == 0) "${crack}_start$rotation" else "$crack$rotation"
            repo.spotanimMap(spotanim.spotanimType(), tile, delay = delay + i)
        }
    }

    fun resolve(repo: WorldRepository, synth: String) {
        val spotanim = "${crack}_end$rotation".spotanimType()
        tiles.forEachIndexed { i, tile -> repo.spotanimMap(spotanim, tile, delay = i) }
        repo.soundArea(soundTile, synth, radius = SOUND_RADIUS, size = SOUND_SIZE)
    }

    companion object {
        private const val CRACK_STRAIGHT = "spotanim.vfx_ocellus_p1_floor_crack_01"
        private const val CRACK_DIAGONAL = "spotanim.vfx_ocellus_p1_floor_crack_02"

        private const val BOSS_SIZE = 3
        private const val CORNER_LENGTH = 8
        private const val CENTRE_LENGTH = 6
        private const val SOUND_RADIUS = 20
        private const val SOUND_SIZE = 5

        /** Clockwise from west; centre charges turn through these one step per round. */
        val SIDES: List<Pair<Int, Int>> = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)

        /**
         * The 7 lines a corner charge picks from, all running into the boss from the room's centre
         * ([sx], [sz] point from the boss towards it): two straight lines down each inner side,
         * on the middle column/row and the one nearer the centre, and three diagonals onto the
         * inner corner.
         */
        fun corner(boss: CoordGrid, sx: Int, sz: Int): List<OcellusTendril> {
            val nearCol = if (sx < 0) 0 else BOSS_SIZE - 1
            val nearRow = if (sz < 0) 0 else BOSS_SIZE - 1
            val outX = if (sx < 0) -1 else BOSS_SIZE
            val outZ = if (sz < 0) -1 else BOSS_SIZE
            val diagonal = boss.translate(nearCol + sx, nearRow + sz)
            return listOf(1, nearCol).map { cornerLine(boss.translate(it, outZ), 0, -sz) } +
                listOf(1, nearRow).map { cornerLine(boss.translate(outX, it), -sx, 0) } +
                listOf(diagonal, diagonal.translate(0, -sz), diagonal.translate(-sx, 0)).map {
                    cornerLine(it, -sx, -sz)
                }
        }

        /**
         * The two diagonals that land on the ends of the boss face on [side]; the first lands on
         * the end nearer the previous side and resolves first.
         */
        fun centre(boss: CoordGrid, side: Int): Pair<OcellusTendril, OcellusTendril> {
            val (nx, nz) = SIDES[side]
            val (px, pz) = SIDES[(side + SIDES.size - 1) % SIDES.size]
            val middle = boss.translate(1 + nx * 2, 1 + nz * 2)
            val first = centreLine(middle.translate(px, pz), -(nx + px), -(nz + pz))
            val second = centreLine(middle.translate(-px, -pz), -(nx - px), -(nz - pz))
            return first to second
        }

        private fun cornerLine(end: CoordGrid, dx: Int, dz: Int): OcellusTendril =
            into(end, dx, dz, CORNER_LENGTH, soundSteps = 1)

        private fun centreLine(end: CoordGrid, dx: Int, dz: Int): OcellusTendril =
            into(end, dx, dz, CENTRE_LENGTH, soundSteps = 0)

        private fun into(
            end: CoordGrid,
            dx: Int,
            dz: Int,
            length: Int,
            soundSteps: Int,
        ): OcellusTendril {
            val start = end.translate(-dx * (length - 1), -dz * (length - 1))
            return OcellusTendril(start, dx, dz, length, soundSteps)
        }

        private fun String.spotanimType(): SpotanimType = SpotanimType(asRSCM(RSCMType.SPOTANIM))
    }
}
