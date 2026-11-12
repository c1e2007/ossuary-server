package org.rsmod.content.raids.tfa.raid.encounter.guardian

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.runAbility
import org.rsmod.api.bosses.runtime.runAbilityAs
import org.rsmod.api.script.onNpcHit
import org.rsmod.plugin.scripts.ScriptContext

internal class GuardianBatteryHits(
    private val deps: BossDeps,
    private val fights: GuardianEncounter,
) {
    fun register(ctx: ScriptContext) {
        val type = ServerCacheManager.getNpc("npc.tfa_guardian_battery_active".asRSCM(RSCMType.NPC))!!
        with(ctx) {
            onNpcHit(type) {
                val fight = fights.ofBattery(npc) ?: return@onNpcHit
                if (npc.hitpoints > 1 || !fight.breaking.add(npc)) return@onNpcHit
                val player = fight.fighters().firstOrNull() ?: return@onNpcHit
                deps.runAbilityAs(fight.boss, npc, player, "battery_body")
                deps.runAbility(fight.boss, player, "battery_shield")
            }
        }
    }
}
