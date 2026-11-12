package org.rsmod.api.player.cheat

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

public val ADMIN_GOD_MODE_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_MAX_HIT_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_FLAT_HIT_ATTR: AttributeKey<Boolean> = AttributeKey()

/** Damage every hit deals while [adminFlatHit] is on. */
public const val ADMIN_FLAT_HIT_DAMAGE: Int = 100

public var Player.adminGodMode: Boolean
    get() = attr[ADMIN_GOD_MODE_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_GOD_MODE_ATTR] = true
        } else {
            attr.remove(ADMIN_GOD_MODE_ATTR)
        }
    }

public var Player.adminMaxHit: Boolean
    get() = attr[ADMIN_MAX_HIT_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_MAX_HIT_ATTR] = true
        } else {
            attr.remove(ADMIN_MAX_HIT_ATTR)
        }
    }

public var Player.adminFlatHit: Boolean
    get() = attr[ADMIN_FLAT_HIT_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_FLAT_HIT_ATTR] = true
        } else {
            attr.remove(ADMIN_FLAT_HIT_ATTR)
        }
    }

/** Toggles immunity, always-hit and flat 100-damage hits together. Returns the new state. */
public fun Player.toggleGodMode(): Boolean {
    val enabled = !adminGodMode
    adminGodMode = enabled
    adminMaxHit = enabled
    adminFlatHit = enabled
    return enabled
}
