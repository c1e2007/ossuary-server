package org.rsmod.content.raids.tfa.raid.encounter.bork

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.plugin.scripts.ScriptContext

internal class SmorkHits(private val fights: BorkEncounter) {
    fun register(ctx: ScriptContext) {
        val type = ServerCacheManager.getNpc("npc.tfa_smork".asRSCM(RSCMType.NPC))!!
        with(ctx) {
            onModifyNpcHit(type) { npc.vars["varn.flat_armour"] = FLAT_ARMOUR }
            onNpcHit(type) { fights.ofSmork(npc)?.onSmorkHit() }
        }
    }

    private companion object {
        const val FLAT_ARMOUR = 2
    }
}
