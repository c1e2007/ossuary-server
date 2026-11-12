package org.rsmod.content.raids.tfa.raid.reward

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TfaVaultScript
@Inject
constructor(private val vault: TfaVault, private val loot: TfaVaultLoot) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(TfaVault.BLADE) { commune() }
        onOpNpc3(TfaVault.BLADE) { commune() }

        onOpNpc1(CHEST_CLOSED_MINE) { openChest(it.npc) }
        onOpNpc1(CHEST_CLOSED_OTHER) { mes(NOT_YOURS) }
        onOpNpc1(CHEST_OPEN_MINE) { openRewards() }
        onOpNpc1(CHEST_OPEN_OTHER) { mes(NOT_YOURS) }
        onOpNpc1(TfaVault.UNIQUE_CLOSED) { revealUnique(it.npc) }
        onOpNpc1(TfaVault.UNIQUE_OPEN) { openRewards() }

        onOpNpc1(EXIT_NPC) { leave() }
        onOpNpc2(EXIT_NPC) { leave() }
        onOpLoc1(EXIT_LOC) { leave() }
        onOpLoc1(WHEELBARROW) { wheelbarrow() }

        onIfModalButton(ITEMS) { takeItem(it) }
        onIfModalButton("component.tfa_chests:inventory") { takeAll() }
        onIfModalButton("component.tfa_chests:bank") { bankAll() }
        onIfModalButton("component.tfa_chests:discard") { discardAll() }
        onIfClose(REWARD_INTERFACE) { player.stopInvTransmit(loot.rewards(player)) }
        onPlayerLogout { vault.settle(player) }
    }

    private fun ProtectedAccess.commune() {
        vault.enter(vault.party(player.coords).ifEmpty { listOf(player) })
    }

    private suspend fun ProtectedAccess.openChest(chest: Npc) {
        val slot = vault.slotOf(player) ?: return
        if (vault.chestOf(slot) !== chest) return
        faceEntitySquare(chest)
        anim(PLAYER_OPEN_SEQ)
        delay(1)
        chest.anim(CHEST_OPEN_SEQ)
        vault.openChest(player, slot)
        delay(1)
        openRewards()
    }

    private suspend fun ProtectedAccess.revealUnique(chest: Npc) {
        if (!vault.isWinner(player)) {
            mes(NOT_YOURS)
            return
        }
        val obj = vault.beginReveal() ?: return
        faceEntitySquare(chest)
        anim(UNIQUE_PLAYER_SEQ)
        delay(UNIQUE_OPEN_DELAY)
        chest.anim(UNIQUE_OPEN_SEQ)
        delay(UNIQUE_LIGHT_DELAY)
        chest.spotanim(UNIQUE_LIGHT_SPOT, slot = UNIQUE_SPOT_SLOT)
        val reveal = REVEALS[obj]
        if (reveal != null) {
            delay(UNIQUE_REVEAL_DELAY)
            chest.spotanim(reveal.first, slot = UNIQUE_SPOT_SLOT)
            delay(1)
            chest.spotanim(reveal.second, slot = UNIQUE_SPOT_SLOT)
        }
        vault.finishReveal(player)
        openRewards()
    }

    private fun ProtectedAccess.openRewards() {
        if (loot.isEmpty(player)) {
            mes("There is nothing left in here.")
            return
        }
        val rewards = loot.rewards(player)
        invTransmit(rewards)
        ifOpenMainModal(REWARD_INTERFACE)
        player.runClientScript(
            INV_INIT.asRSCM(RSCMType.CLIENTSCRIPT),
            ITEMS.asRSCM(RSCMType.COMPONENT),
            REWARD_INV.asRSCM(RSCMType.INV),
            COLUMNS,
            ROWS,
            0,
            NO_COMPONENT,
            "Take",
            "Take-5",
            "Take-10",
            "Take-All",
            "",
        )
        ifSetEvents("component.tfa_chests:inventory", -1..-1, IfEvent.Op1)
        ifSetEvents("component.tfa_chests:bank", -1..-1, IfEvent.Op6)
        ifSetEvents("component.tfa_chests:discard", -1..-1, IfEvent.Op6)
        ifSetEvents(
            ITEMS,
            0 until rewards.size,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op10,
        )
    }

    private fun ProtectedAccess.takeItem(button: IfModalButton) {
        when (button.op) {
            IfButtonOp.Op1 -> take(button.comsub, 1)
            IfButtonOp.Op2 -> take(button.comsub, TAKE_FIVE)
            IfButtonOp.Op3 -> take(button.comsub, TAKE_TEN)
            IfButtonOp.Op4 -> take(button.comsub, Int.MAX_VALUE)
            IfButtonOp.Op10 -> button.obj?.let { mes(it.examine) }
            else -> Unit
        }
    }

    private fun ProtectedAccess.take(slot: Int, amount: Int) {
        loot.move(player, slot, amount, inv, banking = false)
        finishClaim()
    }

    private fun ProtectedAccess.wheelbarrow() {
        if (loot.isEmpty(player)) {
            mes("The wheelbarrow is empty.")
            return
        }
        openRewards()
    }

    private fun ProtectedAccess.takeAll() {
        loot.moveAll(player, inv, banking = false)
        finishClaim()
    }

    private fun ProtectedAccess.bankAll() {
        loot.moveAll(player, bank, banking = true)
        finishClaim()
    }

    private suspend fun ProtectedAccess.discardAll() {
        val discard =
            choice2("Yes.", true, "No.", false, title = "Are you sure you want to discard everything?")
        if (!discard) return
        loot.discardAll(player)
        finishClaim()
    }

    private fun ProtectedAccess.finishClaim() {
        if (loot.isEmpty(player)) ifClose()
    }

    private fun ProtectedAccess.leave() {
        vault.leave(player)
    }

    private companion object {
        const val REWARD_INTERFACE = "interface.tfa_chests"
        const val REWARD_INV = "inv.tfa_chests"
        const val ITEMS = "component.tfa_chests:items"
        const val INV_INIT = "clientscript.[clientscript,interface_inv_init]"
        const val COLUMNS = 2
        const val ROWS = 3
        const val NO_COMPONENT = -1
        const val TAKE_FIVE = 5
        const val TAKE_TEN = 10
        const val CHEST_CLOSED_MINE = "npc.tfa_rewards_chest_vault_closed_mine"
        const val CHEST_CLOSED_OTHER = "npc.tfa_rewards_chest_vault_closed_notmine"
        const val CHEST_OPEN_MINE = "npc.tfa_rewards_chest_vault_open_mine"
        const val CHEST_OPEN_OTHER = "npc.tfa_rewards_chest_vault_open_notmine"
        const val EXIT_NPC = "npc.tfa_vault_exit_small"
        const val EXIT_LOC = "loc.tfa_vault_exit"
        const val WHEELBARROW = "loc.tfa_loot_wheelbarrow"
        const val NOT_YOURS = "This isn't your chest."

        const val PLAYER_OPEN_SEQ = "seq.human_tfa_small_chest_open"
        const val CHEST_OPEN_SEQ = "seq.chest_guthixian02_open01"
        const val UNIQUE_PLAYER_SEQ = "seq.human_pickuptable"
        const val UNIQUE_OPEN_SEQ = "seq.chest_guthixian01_open"
        const val UNIQUE_LIGHT_SPOT = "spotanim.vfx_tfa_loot_light"
        const val UNIQUE_SPOT_SLOT = 1
        const val UNIQUE_OPEN_DELAY = 2
        const val UNIQUE_LIGHT_DELAY = 9
        const val UNIQUE_REVEAL_DELAY = 6

        val REVEALS =
            mapOf(
                "obj.rondache" to ("spotanim.vfx_tfa_loot_rondache_01" to "spotanim.vfx_tfa_loot_rondache_02"),
                "obj.zoryas_tome_uncharged" to
                    ("spotanim.vfx_tfa_loot_tome_01" to "spotanim.vfx_tfa_loot_tome_02"),
                "obj.tfa_zeal_reward" to ("spotanim.vfx_tfa_loot_zeal_01" to "spotanim.vfx_tfa_loot_zeal_02"),
                "obj.ascension_crossbows" to
                    ("spotanim.vfx_tfa_loot_crossbow_01" to "spotanim.vfx_tfa_loot_crossbow_02"),
                "obj.obligator_uncharged" to
                    ("spotanim.vfx_tfa_loot_obligator_01" to "spotanim.vfx_tfa_loot_obligator_02"),
            )
    }
}
