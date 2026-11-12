package org.rsmod.content.other.special.weapons.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

/**
 * Toxic blowpipe. The loaded blowpipe fires dragon darts with unlimited darts and scales in this
 * beta; the empty one tells the player to load it. Dart strength (+35, dragon dart) is added in
 * `WornBonuses`.
 */
class ToxicBlowpipeWeapons : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register("obj.toxic_blowpipe_loaded", Blowpipe(manager))
        register("obj.toxic_blowpipe", Empty(manager))
    }

    private class Blowpipe(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        private fun ProtectedAccess.shoot(target: PathingEntity, attack: CombatAttack.Ranged) {
            val weapon = getInvObj(attack.weapon)
            val projId = weapon.paramOrNull(params.proj_type)?.id
            if (projId == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return
            }
            manager.playWeaponFx(this, attack)
            spotanim(
                "spotanim.ii_dragondart_launch",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val proj =
                manager.spawnProjectile(
                    this,
                    target,
                    "spotanim.ii_dragon_dart_projanim",
                    RSCM.getReverseMapping(RSCMType.PROJANIM, projId),
                )
            val (serverDelay, clientDelay) = proj.durations
            val damage = manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, null, damage, clientDelay, serverDelay)
            manager.continueCombat(this, target)
        }
    }

    private class Empty(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            stop()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            stop()
            return true
        }

        private fun ProtectedAccess.stop() {
            mes("Your blowpipe has no darts or scales in it.")
            manager.stopCombat(this)
        }
    }
}
