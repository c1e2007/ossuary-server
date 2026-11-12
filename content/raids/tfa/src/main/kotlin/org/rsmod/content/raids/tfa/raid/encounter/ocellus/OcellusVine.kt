package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import java.util.IdentityHashMap
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

internal class OcellusVine(private val deps: BossDeps) {
    private val roots = IdentityHashMap<Npc, Player>()

    fun register() {
        deps.extensionRegistry.register(SPAWN) { spawn(it.npc) }
        deps.extensionRegistry.register(CLEAR) { clear() }
    }

    fun clear() {
        for ((vine, victim) in roots.entries.toList()) {
            release(vine, victim)
            if (vine.isSlotAssigned) deps.npcRepo.del(vine, Int.MAX_VALUE)
        }
    }

    private fun spawn(boss: Npc) {
        val candidates = OcellusArena.quadrantPlayers(boss, deps.playerList)
        if (candidates.isEmpty()) return
        val victim = deps.random.pick(candidates)

        val vine = Npc(VINE, victim.coords)
        vine.moveRestrict = MoveRestrict.NoMove
        vine.mode = NpcMode.None
        deps.npcRepo.add(vine, Int.MAX_VALUE)
        vine.respawns = false
        roots[vine] = victim

        victim.frozen = true
        victim.abortRoute()
        victim.spotanim(START_SPOT, slot = SPOT_SLOT)
        watch(boss, vine, victim, LIFETIME)
    }

    private fun watch(boss: Npc, vine: Npc, victim: Player, ticksLeft: Int) {
        deps.worldQueues.add(1) {
            if (roots[vine] !== victim) return@add
            when {
                vine.hitpoints <= 0 || !vine.isSlotAssigned -> release(vine, victim)
                ticksLeft <= 1 -> expire(boss, vine, victim)
                else -> {
                    if (victim.isSlotAssigned) victim.spotanim(IDLE_SPOT, slot = SPOT_SLOT)
                    watch(boss, vine, victim, ticksLeft - 1)
                }
            }
        }
    }

    private fun release(vine: Npc, victim: Player) {
        roots.remove(vine)
        victim.frozen = false
        if (victim.isSlotAssigned) victim.spotanim(END_SPOT, slot = SPOT_SLOT)
    }

    private fun expire(boss: Npc, vine: Npc, victim: Player) {
        release(vine, victim)
        if (boss.isSlotAssigned && victim.isValidTarget()) {
            val damage = deps.random.of(EXPIRE_DAMAGE)
            victim.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
        deps.worldQueues.add(END_TICKS) {
            if (vine.isSlotAssigned) deps.npcRepo.del(vine, Int.MAX_VALUE)
        }
    }

    companion object {
        const val SPAWN = "ocellus.vine_spawn"
        const val CLEAR = "ocellus.vine_clear"

        // PLACEHOLDER: the wiki only says 50+; no captured figure.
        private val EXPIRE_DAMAGE = 50..70

        private const val VINE = "npc.tfa_ocellus_p3_void_vine"
        private const val LIFETIME = 32
        private const val END_TICKS = 2
        private const val SPOT_SLOT = 5

        private const val START_SPOT = "spotanim.vfx_ocellus_spell_entangle01_start"
        private const val IDLE_SPOT = "spotanim.vfx_ocellus_spell_entangle01_idle"
        private const val END_SPOT = "spotanim.vfx_ocellus_spell_entangle01_end"
    }
}
