package org.rsmod.content.other.special.attacks

import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.content.other.special.attacks.boost.StatBoostSpecialAttacks
import org.rsmod.content.other.special.attacks.magic.PoweredStaffSpecialAttacks
import org.rsmod.content.other.special.attacks.melee.DragonClawsSpecialAttack
import org.rsmod.content.other.special.attacks.melee.DragonLongswordSpecialAttack
import org.rsmod.content.other.special.attacks.melee.HalberdSpecialAttack
import org.rsmod.content.other.special.attacks.melee.PowerMeleeSpecialAttacks
import org.rsmod.content.other.special.attacks.ranged.DarkBowSpecialAttack
import org.rsmod.content.other.special.attacks.ranged.ToxicBlowpipeSpecialAttack
import org.rsmod.content.other.special.attacks.ranged.ZaryteCrossbowSpecialAttack
import org.rsmod.plugin.module.PluginModule

class SpecialAttackModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpecialAttackMap>(StatBoostSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(DarkBowSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(DragonLongswordSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(HalberdSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(DragonClawsSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(PowerMeleeSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(ZaryteCrossbowSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(ToxicBlowpipeSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(PoweredStaffSpecialAttacks::class.java)
    }
}
