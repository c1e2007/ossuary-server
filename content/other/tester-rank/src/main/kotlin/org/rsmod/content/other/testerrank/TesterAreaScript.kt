package org.rsmod.content.other.testerrank

import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * One crystal in each tester area opens the PvP-challenges hub: Spellbook, Teleports (between the
 * two areas) and Gear (the Tournament Supplies interface).
 */
class TesterAreaScript
@Inject
constructor(private val locRepo: LocRepository, private val commands: CheatCommandMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup {
            locRepo.add(TFA_CRYSTAL, CRYSTAL, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
            locRepo.add(TOA_CRYSTAL, CRYSTAL, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        }

        onOpLoc1(CRYSTAL) { TesterHub.open(this) }

        // The lobby bank chests (63274 closed, 63275 open): any option opens the bank.
        for (chest in listOf("loc.tfa_lobby_bankchest_closed", "loc.tfa_lobby_bankchest_open")) {
            onOpLoc1(chest) { tryOpenBank() }
            onOpLoc2(chest) { tryOpenBank() }
            onOpLoc3(chest) { tryOpenBank() }
            onOpLoc4(chest) { tryOpenBank() }
        }
    }

    private companion object {
        const val HUB = "interface.pvptut_ui"
        const val SUPPLIES = "interface.tournament_supplies"

        // The ToA raid's teleport crystal model.
        const val CRYSTAL = "loc.toa_teleport_crystal_continue"

        val TFA_CRYSTAL = CoordGrid(4124, 5548, 0)
        val TOA_CRYSTAL = CoordGrid(3358, 9125, 0)
    }
}
