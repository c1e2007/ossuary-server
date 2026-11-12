package org.rsmod.content.raids.tfa.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object DreadnothScalingTable {

    const val HP = 0

    private val ROWS =
        mapOf(2 to 2100, 3 to 3050, 4 to 3930, 5 to 4810, 6 to 5690, 7 to 6580, 8 to 7460)

    fun scaling() =
        dbTable("dbtable.tfa_dreadnoth_scaling", serverOnly = true) {
            column("hp", HP, VarType.INT)
            for ((players, hp) in ROWS) {
                row("dbrow.tfa_dreadnoth_scaling_$players") { column(HP, hp) }
            }
        }
}
