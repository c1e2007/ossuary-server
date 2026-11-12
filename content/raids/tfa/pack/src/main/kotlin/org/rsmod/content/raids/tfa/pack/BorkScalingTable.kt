package org.rsmod.content.raids.tfa.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object BorkScalingTable {

    const val BORK = 0
    const val SMORK = 1

    private val ROWS =
        mapOf(
            2 to (1500 to 600),
            3 to (2180 to 870),
            4 to (2810 to 1120),
            5 to (3440 to 1370),
            6 to (4070 to 1630),
            7 to (4700 to 1880),
            8 to (5330 to 2130),
        )

    fun scaling() =
        dbTable("dbtable.tfa_bork_scaling", serverOnly = true) {
            column("bork", BORK, VarType.INT)
            column("smork", SMORK, VarType.INT)
            for ((players, hp) in ROWS) {
                row("dbrow.tfa_bork_scaling_$players") {
                    column(BORK, hp.first)
                    column(SMORK, hp.second)
                }
            }
        }
}
