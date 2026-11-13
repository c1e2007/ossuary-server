package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The flames on the arena's mouth during a fight (Fight.Yama.js `passFlames`): Pass asks "Flee the
 * fight? Yama will count it as an escape.", Quick Pass doesn't. Either takes the player to the
 * south side and ends the fight as an escape: Yama back on his throne, the glyphs out and the
 * flames gone. The reference walks the player through with its obstacle runner; here the player
 * is moved to the tile south of the flames (see YAMA-DEVIATIONS.md).
 */
internal class YamaFlames @Inject constructor(private val fights: YamaFights) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(YamaIds.FLAMES) { pass(ask = true) }
        onOpLoc2(YamaIds.FLAMES) { pass(ask = false) }
    }

    private suspend fun ProtectedAccess.pass(ask: Boolean) {
        val fight = fights.fightOf(player) ?: return
        if (fight.stopped || fight.over || fight.state == YamaState.SEATED) return
        if (ask) {
            val flee = choice2("Yes, flee.", true, "No.", false, title = "Flee the fight? Yama will count it as an escape.")
            if (!flee) return
        }
        val through = fight.arena.tile(player.coords.let { fight.arena.source(it) }.x, 10066, 0)
        fight.flee()
        telejump(through, TeleportType.Exempt)
    }
}
