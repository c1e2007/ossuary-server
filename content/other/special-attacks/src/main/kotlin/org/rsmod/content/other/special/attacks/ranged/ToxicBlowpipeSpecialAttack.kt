package org.rsmod.content.other.special.attacks.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

/**
 * Toxic blowpipe, Toxic Siphon (50% energy, from the cache's special energy table): double
 * accuracy, +50% damage, and heals the wielder for half the damage dealt.
 */
class ToxicBlowpipeSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerRanged("obj.toxic_blowpipe_loaded", Siphon(manager))
    }

    private class Siphon(private val manager: SpecialAttackManager) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean = siphon(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = siphon(target, attack)

        private fun ProtectedAccess.siphon(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val weapon = getInvObj(attack.weapon)
            val projId = weapon.paramOrNull(params.proj_type)?.id
            if (projId == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            anim("seq.toxic_blowpipe_special_updated")
            spotanim(
                "spotanim.toxic_blowpipe_specialattack",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val projectile =
                manager.spawnProjectile(
                    this,
                    target,
                    "spotanim.ii_dragon_dart_projanim",
                    RSCM.getReverseMapping(RSCMType.PROJANIM, projId),
                )
            val damage =
                manager.rollRangedDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.5,
                )
            if (damage > 1) {
                statHeal("stat.hitpoints", damage / 2, 0)
            }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                this,
                target,
                null,
                damage,
                projectile.clientCycles,
                projectile.serverCycles,
            )
            manager.continueCombat(this, target)
            return true
        }
    }
}
