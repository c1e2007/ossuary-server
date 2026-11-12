package org.rsmod.content.drops.clue

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invClear
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Opens treasure trail reward caskets using the wiki reward tables ([ClueRewardTable]). */
private var Player.beginnerTrails by intVarBit("varbit.collection_clues_beginner_completed")
private var Player.easyTrails by intVarBit("varbit.collection_clues_easy_completed")
private var Player.mediumTrails by intVarBit("varbit.collection_clues_medium_completed")
private var Player.hardTrails by intVarBit("varbit.collection_clues_hard_completed")
private var Player.eliteTrails by intVarBit("varbit.collection_clues_elite_completed")
private var Player.masterTrails by intVarBit("varbit.collection_clues_master_completed")

class ClueCasketScript
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val marketPrices: MarketPrices,
    private val petRewards: PetRewards,
) :
    PluginScript() {
    private val tables = ClueRewardTable.loadAll()

    override fun ScriptContext.startup() {
        onIfClose(REWARD_INTERFACE) {
            val rewardInv = player.invMap[REWARD_INV] ?: return@onIfClose
            player.invClear(rewardInv)
            player.stopInvTransmit(rewardInv)
        }
        for (table in tables) {
            onOpHeld1(table.casket) { openCasket(table) }
        }
    }

    private fun ProtectedAccess.openCasket(table: ClueRewardTable) {
        if (invDel(inv, table.casket, 1).failure) return
        val rewards = table.open(random)
        mes("Well done, you've completed the Treasure Trail!")
        val rewardInv = player.invMap.getOrPut(REWARD_INV)
        invClear(rewardInv)
        for ((obj, qty) in rewards) {
            invAddOrDrop(objRepo, obj, qty)
            invAdd(rewardInv, obj, qty)
        }
        invTransmit(rewardInv)
        ifOpenMain(REWARD_INTERFACE)

        val count = player.incrementTrails(table.tier)
        mes("<col=ff0000>You have completed $count ${table.tier} Treasure Trail${if (count == 1) "" else "s"}.</col>")
        // Wiki: 1/1,000 chance of the Bloodhound pet per master casket opened.
        if (table.tier == "master" && random.of(1000) == 0) {
            petRewards.give(player, "obj.bloodhound_pet")
        }
        mes("<col=ff0000>Your treasure is worth around ${"%,d".format(treasureValue(rewards))} coins!</col>")
    }

    private fun Player.incrementTrails(tier: String): Int =
        when (tier) {
            "beginner" -> ++beginnerTrails
            "easy" -> ++easyTrails
            "medium" -> ++mediumTrails
            "hard" -> ++hardTrails
            "elite" -> ++eliteTrails
            else -> ++masterTrails
        }

    private fun treasureValue(rewards: List<Pair<String, Int>>): Long =
        rewards.sumOf { (obj, qty) ->
            val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))
            (type?.let { marketPrices[it] } ?: 0).toLong() * qty
        }

    private companion object {
        const val REWARD_INTERFACE = "interface.trail_rewardscreen"
        const val REWARD_INV = "inv.trail_rewardinv"
    }
}
