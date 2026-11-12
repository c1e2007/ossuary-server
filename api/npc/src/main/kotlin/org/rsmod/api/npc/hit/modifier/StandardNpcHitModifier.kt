package org.rsmod.api.npc.hit.modifier

import jakarta.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.min
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.isStyleImmuneTo
import org.rsmod.api.player.cheat.ADMIN_FLAT_HIT_DAMAGE
import org.rsmod.api.player.cheat.adminFlatHit
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

public class StandardNpcHitModifier
@Inject
constructor(private val eventBus: EventBus, private val playerList: PlayerList) :
    NpcHitModifier {
    override fun HitBuilder.modify(target: Npc) {
        // The admin flat hit sets the base damage, so it must run before the npc's own hit rules:
        // encounter caps (e.g. the Wardens' obelisk, which must never drop below 1 hp) and damage
        // modifiers then get the last word. Applying it last overrode those caps and let the
        // obelisk die like a normal npc, skipping the phase transition.
        applyAdminFlatHit()
        target.publishEvent(this)
        target.applyStyleImmunity(this)
        target.applyFlatArmour(this)
    }

    private fun HitBuilder.applyAdminFlatHit() {
        val uid = sourceUid
        if (!isFromPlayer || type == HitType.Typeless || uid == null) {
            return
        }
        val source = PlayerUid(uid).resolve(playerList) ?: return
        if (source.adminFlatHit) {
            damage = ADMIN_FLAT_HIT_DAMAGE
        }
    }

    private fun Npc.publishEvent(hit: HitBuilder) {
        val event = NpcHitEvents.Modify(this, hit)
        eventBus.publish(event)
    }

    private fun Npc.applyStyleImmunity(hit: HitBuilder) {
        if (isStyleImmuneTo(hit.type)) {
            hit.damage = 0
        }
    }

    private fun Npc.applyFlatArmour(hit: HitBuilder) {
        val armour = vars["varn.flat_armour"]

        if (armour > 0) {
            val capped = min(armour.absoluteValue, hit.damage)
            vars["varn.flat_armour"] -= capped
            hit.damage -= capped
        }

        if (armour < 0) {
            vars["varn.flat_armour"] = 0
            hit.damage += armour.absoluteValue
        }
    }
}
