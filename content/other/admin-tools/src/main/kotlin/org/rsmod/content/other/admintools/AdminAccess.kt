package org.rsmod.content.other.admintools

import dev.or2.central.account.Rights
import org.rsmod.api.player.protect.ProtectedAccess

/** The admin items do nothing for anyone else. */
internal fun ProtectedAccess.requireAdmin(): Boolean {
    if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
        return true
    }
    mes("Nothing interesting happens.")
    return false
}
