package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.npc.stat.stat
import org.rsmod.api.npc.stat.statSub
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Dragon warhammer, Voidwaker, Osmumten's fang and Abyssal whip specials. Energy costs come from
 * the cache's special energy table (DWH 50%, Voidwaker 50%, Fang 25%, Whip 50%).
 */
class PowerMeleeSpecialAttacks : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val warhammer = Warhammer(manager)
        registerMelee("obj.dragon_warhammer", warhammer)
        registerMelee("obj.br_dragon_warhammer", warhammer)
        registerMelee("obj.bh_dragon_warhammer_corrupted", warhammer)

        val voidwaker = Voidwaker(manager)
        registerMelee("obj.voidwaker", voidwaker)
        registerMelee("obj.br_voidwaker", voidwaker)
        registerMelee("obj.deadman_voidwaker", voidwaker)

        val fang = Fang(manager)
        registerMelee("obj.osmumtens_fang", fang)
        registerMelee("obj.osmumtens_fang_ornament", fang)
        registerMelee("obj.br_osmumtens_fang", fang)

        val whip = Whip(manager)
        registerMelee("obj.abyssal_whip", whip)
        registerMelee("obj.br_abyssal_whip", whip)
        registerMelee("obj.abyssal_whip_lava", whip)
        registerMelee("obj.abyssal_whip_ice", whip)
    }

    private abstract class Base : MeleeSpecialAttack {
        abstract fun ProtectedAccess.perform(target: PathingEntity, attack: CombatAttack.Melee)

        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            perform(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            perform(target, attack)
            return true
        }
    }

    /** Smash: +50% damage; a hit that deals damage lowers the target's current Defence by 30%. */
    private class Warhammer(private val manager: SpecialAttackManager) : Base() {
        override fun ProtectedAccess.perform(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.dragon_warhammer_sa_player")
            spotanim(
                "spotanim.dragon_warhammer_sa_spotanim",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.5,
                    blockType = MeleeAttackType.Crush,
                )
            if (damage > 0) {
                when (target) {
                    is Npc -> target.statSub("stat.defence", target.stat("stat.defence") * 30 / 100, 0)
                    is Player ->
                        target.statSub("stat.defence", target.stat("stat.defence") * 30 / 100, 0)
                }
            }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    /**
     * Disrupt: a guaranteed hit between 50% and 150% of the wielder's max melee hit, with 2 Magic
     * xp per damage dealt (instead of the usual melee xp).
     */
    private class Voidwaker(private val manager: SpecialAttackManager) : Base() {
        override fun ProtectedAccess.perform(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.human_special_voidwaker")
            spotanim(
                "spotanim.fx_voidwaker02_special",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            target.spotanim("spotanim.fx_voidwaker_impact", height = 96, delay = 0)
            val max = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            val low = max / 2
            val high = max * 3 / 2
            val damage = if (high <= low) low else random.of(low..high)
            statAdvance("stat.magic", damage * 2.0)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    /**
     * Eviscerate: +50% accuracy, and the hit uses the weapon's true max hit rather than the 85%
     * cap of its normal attacks (that cap is applied by the shared melee formula).
     */
    private class Fang(private val manager: SpecialAttackManager) : Base() {
        override fun ProtectedAccess.perform(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.weapon_sword_osmumten03_special")
            spotanim(
                "spotanim.spotanim_weapon_sword_osmumten_special",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.5,
                    maxHitMultiplier = 1.0,
                    blockType = MeleeAttackType.Stab,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    /**
     * Energy Drain: +25% accuracy against slash defence; against players it moves 10% of the
     * target's run energy to the wielder.
     */
    private class Whip(private val manager: SpecialAttackManager) : Base() {
        override fun ProtectedAccess.perform(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.slayer_abyssal_whip_attack")
            spotanim(
                "spotanim.sp_attack_abyssal_whip",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.25,
                    maxHitMultiplier = 1.0,
                    blockType = MeleeAttackType.Slash,
                )
            if (target is Player) {
                val taken = target.runEnergy / 10
                target.runEnergy -= taken
                player.runEnergy += taken
                target.mes("You feel drained!")
            }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }
}
