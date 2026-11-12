package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import java.awt.Color
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.ui.setColour
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.BARRAGE_WAVES
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.METER
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class OcellusMeter(private val deps: BossDeps, private val hpBar: BossHpBarScript) {
    fun show(npc: Npc) {
        for (player in OcellusArena.players(npc, deps.playerList)) {
            player.hudExtraBase = MAX
            player.hudExtraCurrent = npc.vars[METER]
            player.hudShowExtraBar = 1
            hpBar.onRefresh(player, npc) { applyExtraColours() }
        }
    }

    fun hide(npc: Npc) {
        for (player in OcellusArena.players(npc, deps.playerList)) {
            player.hudShowExtraBar = 0
            player.hudExtraBase = 0
            player.hudExtraCurrent = 0
        }
    }

    fun charge(npc: Npc, tendril: OcellusTendril) {
        val players = OcellusArena.players(npc, deps.playerList)
        if (players.isEmpty()) return
        val unblocked = players.count { it.coords !in tendril.tiles }
        val gain = TENDRIL_CHARGE * unblocked / players.size
        set(npc, (npc.vars[METER] + gain).coerceAtMost(MAX))
    }

    fun release(npc: Npc) {
        npc.vars[BARRAGE_WAVES] = waves(npc.vars[METER])
        set(npc, 0)
    }

    private fun set(npc: Npc, value: Int) {
        if (npc.vars[METER] == value) return
        npc.vars[METER] = value
        for (player in OcellusArena.players(npc, deps.playerList)) {
            player.hudExtraCurrent = value
            hpBar.onRefresh(player, npc) { applyExtraColours() }
        }
    }

    private fun Player.applyExtraColours() {
        setColour("component.hpbar_hud:health_bar_back_extra", BACK)
        setColour("component.hpbar_hud:health_bar_sliding_extra", SLIDING)
        setColour("component.hpbar_hud:health_bar_remaining_extra", REMAINING)
    }

    companion object {
        const val MAX = 96
        const val TENDRIL_CHARGE = 8
        const val CHARGE_PER_WAVE = 16

        const val SINGLE_WAVE_MAX = CHARGE_PER_WAVE

        fun waves(meter: Int): Int = maxOf(1, (meter + CHARGE_PER_WAVE - 1) / CHARGE_PER_WAVE)

        private val BACK = Color(40, 40, 0)
        private val SLIDING = Color(160, 152, 0)
        private val REMAINING = Color(216, 208, 0)

        private var Player.hudExtraBase by intVarBit("varbit.hpbar_hud_extra_base")
        private var Player.hudExtraCurrent by intVarBit("varbit.hpbar_hud_extra_current")
        private var Player.hudShowExtraBar by intVarp("varp.hpbar_hud_show_extra_bar")
    }
}
