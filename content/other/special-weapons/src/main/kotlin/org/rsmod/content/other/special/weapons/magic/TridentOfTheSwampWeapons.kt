package org.rsmod.content.other.special.weapons.magic

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Trident of the swamp (and its enchanted form). In this beta it is always charged: each cast tops
 * the charges back up, so it never runs dry. Max hit is `magic level / 3 - 2`.
 */
class TridentOfTheSwampWeapons
@Inject
constructor(
    private val charges: CombatChargeManager,
    private val objCharges: ObjChargeManager,
) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val swamp = Swamp(manager, charges, objCharges)
        register("obj.toxic_tots_charged", swamp)
        register("obj.toxic_tots_i_charged", swamp)
        register("obj.toxic_tots_charged_orn", swamp)
        register("obj.toxic_tots_i_charged_orn", swamp)
        val empty = Empty(manager)
        register("obj.toxic_tots_uncharged", empty)
        register("obj.toxic_tots_i_uncharged", empty)
    }

    private class Swamp(
        private val manager: WeaponAttackManager,
        private val charges: CombatChargeManager,
        private val objCharges: ObjChargeManager,
    ) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Staff) {
            objCharges.addCharges(
                player.worn,
                Wearpos.RightHand.slot,
                MAX_CHARGES,
                CHARGE_VAROBJ,
                MAX_CHARGES,
            )
            val chargeResult = charges.attemptDetractWeapon(player, CHARGE_VAROBJ)
            if (chargeResult.isFailure()) {
                manager.stopCombat(this)
                return
            }

            anim("seq.human_castwave_staff_walkmerge")
            spotanim("spotanim.toxic_tots_casting", height = CAST_HEIGHT)

            val proj =
                manager.spawnProjectile(
                    source = this,
                    target = target,
                    spotanim = "spotanim.toxic_tots_projectile",
                    projanim = "projanim.magic_spell",
                )
            val (serverDelay, clientDelay) = proj.durations

            val splash = manager.rollStaffSplash(this, target, attack)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, CAST_SOUND, soundRadius = 10)
                manager.queueSplashHit(this, target, clientDelay, serverDelay)
            } else {
                val baseMaxHit = max(1, player.magicLvl / 3 - 2)
                val damage = manager.rollStaffMaxHit(this, target, baseMaxHit)
                manager.playMagicHitFx(
                    source = this,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = CAST_SOUND,
                    soundRadius = 10,
                    hitSpot = "spotanim.toxic_tots_impact",
                    hitSpotHeight = 92,
                    hitSound = HIT_SOUND,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay, serverDelay)
            }
            manager.continueCombat(this, target)
        }
    }

    private class Empty(private val manager: WeaponAttackManager) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            stop()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            stop()
            return true
        }

        private fun ProtectedAccess.stop() {
            mes("Your trident has no charges.")
            manager.stopCombat(this)
        }
    }

    private companion object {
        const val CHARGE_VAROBJ = "varobj.charges_16383"
        const val MAX_CHARGES = 2_500
        const val HIT_SOUND = "synth.contact_darkness_impact"
        const val CAST_SOUND = "synth.shadow_cast"
        const val CAST_HEIGHT = 92
    }
}
