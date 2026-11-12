package org.rsmod.content.other.special.attacks.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.BoltEffects
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull

/**
 * Zaryte crossbow, Evoke (75% energy, from the cache's special energy table): doubled accuracy, and
 * the effect of enchanted bolts is guaranteed if the hit lands (see [BoltEffects]).
 */
class ZaryteCrossbowSpecialAttack
@Inject
constructor(
    private val ammunition: RangedAmmoManager,
    private val combat: PlayerAttackManager,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val evoke = Evoke(manager, ammunition, combat)
        registerRanged("obj.zaryte_xbow", evoke)
        registerRanged("obj.br_zaryte_xbow", evoke)
    }

    private class Evoke(
        private val manager: SpecialAttackManager,
        private val ammunition: RangedAmmoManager,
        private val combat: PlayerAttackManager,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean = evoke(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = evoke(target, attack)

        private fun ProtectedAccess.evoke(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val righthandType = getInvObj(attack.weapon)
            val quiverType = getOrNull(player.quiver)

            if (!ammunition.attemptAmmoUsage(player, righthandType, quiverType)) {
                manager.stopCombat(this)
                return false
            }

            val travelSpotanim = quiverType?.paramOrNull(params.proj_travel)
            val projanimId = righthandType.paramOrNull(params.proj_type)?.id
            if (travelSpotanim == null || projanimId == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }

            anim("seq.xbows_human_fire_and_reload")
            val launch = quiverType.paramOrNull(params.proj_launch)
            val launchName = launch?.let { RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id) }
            spotanim(launchName, height = 96, slot = constants.spotanim_slot_combat)

            val projectile =
                manager.spawnProjectile(
                    this,
                    target,
                    RSCM.getReverseMapping(RSCMType.SPOTANIM, travelSpotanim.id),
                    RSCM.getReverseMapping(RSCMType.PROJANIM, projanimId),
                )
            val serverDelay = projectile.serverCycles
            val clientDelay = projectile.clientCycles

            ammunition.useQuiverAmmo(player, quiverType, target.coords, dropDelay = serverDelay)

            val damage = BoltEffects.rollDamage(combat, player, target, attack, quiverType, evoke = true)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, quiverType, damage, clientDelay, serverDelay)
            manager.continueCombat(this, target)
            return true
        }
    }
}
