package org.rsmod.api.combat.manager

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import java.util.concurrent.ThreadLocalRandom
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Enchanted dragon bolt effects for crossbow attacks.
 *
 * - Ruby dragon bolts (e), Blood Forfeit: 6% (11% against players) to take 20% of the target's
 *   current hitpoints (capped at 100) for 10% of the player's own current hitpoints. The effect
 *   overrides the normal hit and ignores the accuracy roll.
 * - Diamond dragon bolts (e), Armour Piercing: 10% (5% against players) for a guaranteed hit with
 *   a 15% higher max hit.
 * - Zaryte crossbow special ([evoke]): double accuracy, and the effect is guaranteed if the hit
 *   lands. Blood Forfeit then takes 22% (capped at 110) and Armour Piercing gets a 26% max hit.
 */
public object BoltEffects {
    private val RUBY_E by lazy { "obj.dragon_bolts_enchanted_ruby".asRSCM(RSCMType.OBJ) }
    private val DIAMOND_E by lazy { "obj.dragon_bolts_enchanted_diamond".asRSCM(RSCMType.OBJ) }

    public fun rollDamage(
        manager: PlayerAttackManager,
        source: Player,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        ammo: ItemServerType?,
        evoke: Boolean = false,
    ): Int {
        val ruby = ammo?.id == RUBY_E
        val diamond = ammo?.id == DIAMOND_E
        if (!evoke && !ruby && !diamond) {
            return manager.rollRangedDamage(source, target, attack)
        }

        val pvp = target is Player
        val accurate =
            manager.rollRangedAccuracy(
                source = source,
                target = target,
                multiplier = if (evoke) 2.0 else 1.0,
                attackType = attack.type,
                attackStyle = attack.style,
                blockType = attack.type,
            )
        val chance = ThreadLocalRandom.current().nextInt(100)
        val procChance = if (ruby) (if (pvp) 11 else 6) else if (pvp) 5 else 10
        // The effect rolls regardless of accuracy, except that the Evoke guarantee needs a hit.
        val proc = if (evoke) accurate && (ruby || diamond) else (ruby || diamond) && chance < procChance

        if (proc && ruby) {
            val pct = if (evoke) 22 else 20
            val cap = if (evoke) 110 else 100
            val ownHp = source.hitpoints
            if (ownHp > 10) {
                source.statSub("stat.hitpoints", ownHp / 10, 0)
                return minOf(cap, hitpointsOf(target) * pct / 100)
            }
        }
        if (proc && diamond) {
            return manager.rollRangedMaxHit(
                source = source,
                target = target,
                attackType = attack.type,
                attackStyle = attack.style,
                multiplier = if (evoke) 1.26 else 1.15,
                boltSpecDamage = 0,
            )
        }
        if (!accurate) return 0
        return manager.rollRangedMaxHit(
            source = source,
            target = target,
            attackType = attack.type,
            attackStyle = attack.style,
            multiplier = 1.0,
            boltSpecDamage = 0,
        )
    }

    private fun hitpointsOf(target: PathingEntity): Int =
        when (target) {
            is Npc -> target.hitpoints
            is Player -> target.hitpoints
        }
}
