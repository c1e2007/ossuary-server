package org.rsmod.content.raids.tfa.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object XarthScalingTable {

    const val XARTH = 0

    private val ROWS =
        mapOf(2 to 2600, 3 to 3770, 4 to 4860, 5 to 5950, 6 to 7050, 7 to 8140, 8 to 9230)

    fun scaling() =
        dbTable("dbtable.tfa_xarth_scaling", serverOnly = true) {
            column("xarth", XARTH, VarType.INT)
            for ((players, hp) in ROWS) {
                row("dbrow.tfa_xarth_scaling_$players") { column(XARTH, hp) }
            }
        }
}
