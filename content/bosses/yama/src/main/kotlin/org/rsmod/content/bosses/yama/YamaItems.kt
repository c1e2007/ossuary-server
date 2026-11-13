package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two items a single drop needs (Dossier.Yama.js, Lockbox.Yama.js), with no surrounding
 * system. The Forgotten lockbox opens for one reward from the Wiki table's 403 chances. The
 * dossier's use is a stub: its rite of vile transference, victory counting and chasm teleport
 * scrolls are the dossier system, which is skipped. Contracts, party play, soul debt and
 * achievements are skipped entirely.
 */
internal class YamaItems @Inject constructor(private val random: GameRandom) : PluginScript() {

    private class Reward(val obj: String, val amount: Int, val weight: Int, val text: String)

    override fun ScriptContext.startup() {
        onOpHeld1(LOCKBOX) { openLockbox() }
        onOpHeld1(DOSSIER) { mes("Yama's dossier can't be read yet.") }
    }

    private fun ProtectedAccess.openLockbox() {
        if (LOCKBOX !in inv) return
        invDel(inv, LOCKBOX, 1)
        val total = REWARDS.sumOf { it.weight }
        var roll = random.of(total)
        val reward = REWARDS.first { roll -= it.weight; roll < 0 }
        invAdd(inv, reward.obj, reward.amount)
        mes(reward.text)
    }

    private companion object {
        const val LOCKBOX = "obj.forgotten_lockbox"
        const val DOSSIER = "obj.yama_dossier"

        val REWARDS =
            listOf(
                Reward(
                    "obj.dragon_bolts_unfeathered",
                    125,
                    60,
                    "You open the forgotten lockbox and find 125x dragon bolts (unf)s inside!",
                ),
                Reward("obj.dragon_dart", 125, 60, "You open the forgotten lockbox and find 125 dragon darts inside!"),
                Reward(
                    "obj.oathplate_shards",
                    14,
                    60,
                    "You open the forgotten lockbox and find 14x oathplate shardss inside!",
                ),
                Reward("obj.dragon_dart", 80, 50, "You open the forgotten lockbox and find 80 dragon darts inside!"),
                Reward(
                    "obj.oathplate_shards",
                    11,
                    50,
                    "You open the forgotten lockbox and find 11x oathplate shardss inside!",
                ),
                Reward(
                    "obj.dragon_platelegs",
                    1,
                    50,
                    "You open the forgotten lockbox and find 1x dragon platelegs inside!",
                ),
                Reward(
                    "obj.dragon_plateskirt",
                    1,
                    50,
                    "You open the forgotten lockbox and find 1 dragon plateskirt inside!",
                ),
                Reward("obj.coins", 233333, 20, "You open the forgotten lockbox and find 233,333 coinss inside!"),
                Reward("obj.oathplate_helm", 1, 1, "You open the forgotten lockbox and find 1 oathplate helm inside!"),
                Reward(
                    "obj.oathplate_chest",
                    1,
                    1,
                    "You open the forgotten lockbox and find 1 oathplate chest inside!",
                ),
                Reward("obj.oathplate_legs", 1, 1, "You open the forgotten lockbox and find 1 oathplate legs inside!"),
            )
    }
}
