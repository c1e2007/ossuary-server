package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.ScriptContext

/**
 * A thin call into the existing `yamaDropTable` on death: Yama plays his death, and eleven ticks
 * after it begins the table's loot lands in front of the throne (as captured). No drop logic of
 * its own; the table lists `npc.yama` alongside the throne npcs it already covered.
 */
internal object YamaLoot {
    private const val LOOT_TICKS = 11

    fun register(ctx: ScriptContext, fights: YamaFights, npcDeath: NpcDeath) {
        val type = ServerCacheManager.getNpc(YamaIds.YAMA.asRSCM(RSCMType.NPC)) ?: return
        with(ctx) {
            onNpcQueue(type, "queue.death") {
                val fight = fights.fightOfNpc(npc)
                anim(YamaIds.SEQ_DESPAWN)
                delay(LOOT_TICKS)
                val loot = fight?.arena?.tile(YamaCommon.LOOT_TILE) ?: coords
                npcDeath.deathWithDrops(this, loot)
                fight?.finishKill()
            }
        }
    }
}
