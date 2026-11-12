package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.ScriptContext

internal class OcellusCurses(private val deps: BossDeps, private val npcList: NpcList) {
    private val finalForm = "npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC)
    private var soundAt = -1

    fun register(ctx: ScriptContext) {
        with(ctx) {
            onEvent<PlayerHitEvents.Impact> {
                if (!hit.isFromNpc) return@onEvent
                val boss = hit.resolveNpcSource(npcList) ?: return@onEvent
                soulSplit(boss, player, hit.type, hit.damage)
            }
        }
    }

    fun reflect(event: NpcHitEvents.Impact) {
        val boss = event.npc
        val hit = event.hit
        if (!hit.isFromPlayer || hit.damage <= 0) return
        val deflected =
            when (boss.vars[VARN]) {
                DEFLECT_MISSILES -> HitType.Ranged
                DEFLECT_MELEE -> HitType.Melee
                else -> return
            }
        if (hit.type != deflected) return
        val attacker = hit.resolvePlayerSource(deps.playerList) ?: return
        if (!attacker.isValidTarget()) return
        val damage = maxOf(1, hit.damage * REFLECT_PERCENT / PERCENT)
        attacker.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    private fun soulSplit(boss: Npc, player: Player, type: HitType, damage: Int) {
        if (boss.visType.id != finalForm || boss.vars[VARN] != SOUL_SPLIT) return
        if (type != HitType.Melee && type != HitType.Ranged && type != HitType.Magic) return
        if (damage <= 0 || boss.hitpoints <= 0) return
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        deps.worldRepo.projAnim(
            ProjAnim(
                spotanim = SOUL_SPLIT_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
                startHeight = SOUL_SPLIT_START_HEIGHT,
                endHeight = SOUL_SPLIT_END_HEIGHT,
                startTime = 0,
                endTime = SOUL_SPLIT_CYCLES,
                angle = SOUL_SPLIT_ANGLE,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = player.coords,
                endCoord = centre,
            )
        )
        deps.worldQueues.add(SOUL_SPLIT_DELAY) {
            if (!boss.isSlotAssigned || boss.hitpoints <= 0) return@add
            boss.heal(damage, showHitsplat = true)
            val now = deps.mapClock.cycle
            if (soundAt != now) {
                soundAt = now
                deps.worldRepo.soundArea(centre, SOUL_SPLIT_SOUND, radius = SOUL_SPLIT_SOUND_RADIUS)
            }
        }
    }

    companion object {
        const val VARN = "varn.ocellus_curse"
        const val NONE = 0
        const val DEFLECT_MISSILES = 1
        const val DEFLECT_MELEE = 2
        const val SOUL_SPLIT = 3

        // PLACEHOLDER: no fighter-side capture of the final phase shows what a deflect returns.
        private const val REFLECT_PERCENT = 20
        private const val PERCENT = 100

        private const val SOUL_SPLIT_DELAY = 2
        private const val SOUL_SPLIT_PROJECTILE = "spotanim.nex_soulsplit_projanim"
        private const val SOUL_SPLIT_START_HEIGHT = 120
        private const val SOUL_SPLIT_END_HEIGHT = 190
        private const val SOUL_SPLIT_CYCLES = 60
        private const val SOUL_SPLIT_ANGLE = 5
        private const val SOUL_SPLIT_SOUND = "synth.ocellus_soul_split_heal"
        private const val SOUL_SPLIT_SOUND_RADIUS = 9
    }
}
