package org.rsmod.content.raids.tfa.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object GuardianScalingTable {

    const val SHIELD = 0
    const val GROUND = 1
    const val BATTERY = 2

    private class Scaling(val players: Int, val shield: Int, val ground: Int, val battery: Int)

    private val ROWS =
        listOf(
            Scaling(2, 550, 1100, 180),
            Scaling(3, 800, 1600, 250),
            Scaling(4, 1030, 2060, 330),
            Scaling(5, 1260, 2520, 400),
            Scaling(6, 1490, 2980, 470),
            Scaling(7, 1720, 3440, 540),
            Scaling(8, 1950, 3910, 610),
        )

    fun scaling() =
        dbTable("dbtable.tfa_guardian_scaling", serverOnly = true) {
            column("shield", SHIELD, VarType.INT)
            column("ground", GROUND, VarType.INT)
            column("battery", BATTERY, VarType.INT)
            for (row in ROWS) {
                row("dbrow.tfa_guardian_scaling_${row.players}") {
                    column(SHIELD, row.shield)
                    column(GROUND, row.ground)
                    column(BATTERY, row.battery)
                }
            }
        }
}
