package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class OcellusSword @Inject constructor(private val encounter: OcellusEncounter) : PluginScript() {
    override fun ScriptContext.startup() {
        val hitbox = ServerCacheManager.getNpc("npc.tfa_ocellus_p2_sword_hitbox".asRSCM(RSCMType.NPC))!!
        onModifyNpcHit(hitbox) {
            if (hit.isFromPlayer) encounter.at(npc.coords)?.phaseTwo?.absorb(hit.damage)
        }
        onNpcHit(hitbox) {
            npc.hitpoints = npc.baseHitpointsLvl
            npc.removeHeadbar(OcellusPhaseTwo.HITBOX_HEADBAR)
        }
    }
}
