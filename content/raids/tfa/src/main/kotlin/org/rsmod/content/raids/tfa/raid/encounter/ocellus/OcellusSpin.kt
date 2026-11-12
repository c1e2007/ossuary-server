package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The prayer half of the spin attack, per spin. A player is only protected from a spin's hit if
 * Protect from Melee is down when its melee prayer fades ([CHECK_OFF]) and up again when its spin
 * animation plays ([CHECK_ON]). Anyone who missed either step takes the hit unprotected. The next
 * spin's [CHECK_OFF] starts the tally over. Progress lives on the player in [SPIN].
 */
internal class OcellusSpin(private val deps: BossDeps) {
    fun register() {
        deps.extensionRegistry.register(CHECK_OFF) {
            forEachPlayer(it.npc) { player -> set(player, if (isProtecting(player)) MISSED else OFF) }
        }
        deps.extensionRegistry.register(CHECK_ON) {
            forEachPlayer(it.npc) { player ->
                val wasOff = player.vars[SPIN] == OFF
                set(player, if (wasOff && isProtecting(player)) PROTECTED else MISSED)
            }
        }
    }

    fun isProtected(player: Player): Boolean = player.vars[SPIN] == PROTECTED

    private fun forEachPlayer(boss: Npc, action: (Player) -> Unit) {
        OcellusArena.players(boss, deps.playerList).forEach(action)
    }

    private fun set(player: Player, state: Int) {
        VarPlayerIntMapSetter.set(player, SPIN, state)
    }

    private fun isProtecting(player: Player): Boolean = player.vars[PROTECT_FROM_MELEE] > 0

    companion object {
        const val CHECK_OFF = "ocellus.spin_check_off"
        const val CHECK_ON = "ocellus.spin_check_on"

        private const val SPIN = "varp.ocellus_spin"
        private const val MISSED = 0
        private const val OFF = 1
        private const val PROTECTED = 2

        private const val PROTECT_FROM_MELEE = "varbit.prayer_protectfrommelee"
    }
}
