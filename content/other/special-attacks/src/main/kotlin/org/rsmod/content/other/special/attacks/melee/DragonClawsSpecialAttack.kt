package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Dragon claws, Slice and Dice (50% energy, from the cache's special energy table).
 *
 * Four hits. Each is rolled for accuracy until one lands; the rest of the damage follows from which
 * hit landed first ("4-2-1-1", "0-4-2-2", "0-0-3-3", "0-0-0-5"), per the wiki. If all four miss,
 * two thirds of the time two random hits deal 1. Hits land on ticks 1, 1, 2 and 2.
 */
class DragonClawsSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val claws = Claws(manager)
        registerMelee("obj.dragon_claws", claws)
        registerMelee("obj.bh_dragon_claws_corrupted", claws)
        registerMelee("obj.br_dragon_claws", claws)
    }

    private class Claws(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            slice(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            slice(target, attack)
            return true
        }

        private fun ProtectedAccess.rnd(low: Int, high: Int): Int =
            if (high <= low) low else random.of(low..high)

        private fun ProtectedAccess.slice(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.human_dragon_claws_spec")
            spotanim(
                "spotanim.dragon_claws_spot",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )

            val max = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            var first = -1
            for (i in 0 until 4) {
                val hit =
                    manager.rollMeleeAccuracy(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        blockType = MeleeAttackType.Slash,
                        multiplier = 1.0,
                    )
                if (hit) {
                    first = i
                    break
                }
            }

            val hits = IntArray(4)
            when (first) {
                0 -> {
                    hits[0] = rnd(max / 2, max - 1)
                    hits[1] = hits[0] / 2
                    hits[2] = hits[1] / 2
                    hits[3] = hits[2] + 1
                }
                1 -> {
                    hits[1] = rnd(3 * max / 8, 7 * max / 8)
                    hits[2] = hits[1] / 2
                    hits[3] = hits[2] + 1
                }
                2 -> {
                    hits[2] = rnd(max / 4, 3 * max / 4)
                    hits[3] = hits[2] + 1
                }
                3 -> hits[3] = rnd(max / 4, 5 * max / 4)
                else -> {
                    if (random.of(0..2) < 2) {
                        val a = random.of(0..3)
                        var b = random.of(0..2)
                        if (b >= a) b++
                        hits[a] = 1
                        hits[b] = 1
                    }
                }
            }

            val delays = intArrayOf(1, 1, 2, 2)
            var total = 0
            for (i in 0 until 4) {
                total += hits[i]
                manager.queueMeleeHit(this, target, hits[i], delays[i])
            }
            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
        }
    }
}
