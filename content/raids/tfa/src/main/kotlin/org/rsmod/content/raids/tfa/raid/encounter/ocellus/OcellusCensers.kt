package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

internal class OcellusCensers(
    private val deps: BossDeps,
    private val energy: SpecialAttackEnergy,
) {
    private val censers = ArrayList<Censer>()
    private val finalForm = "npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC)

    private var Player.charges by intVarp(CHARGES)
    private var Player.maxHit by intVarp(MAX_HIT)

    private class Censer(val kind: Kind, val loc: LocInfo, var lit: Boolean = false)

    private enum class Kind(val active: String, val inactive: String, val glow: String) {
        Heal(
            "loc.tfa_ocellus_artifact_heal",
            "loc.tfa_ocellus_artifact_heal_inactive",
            "spotanim.sp_attackglow_saradomin",
        ),
        Spec(
            "loc.tfa_ocellus_artifact_spec",
            "loc.tfa_ocellus_artifact_spec_inactive",
            "spotanim.sp_attackglow_zaros",
        ),
        Melee(
            "loc.tfa_ocellus_artifact_damage_melee",
            "loc.tfa_ocellus_artifact_damage_melee_inactive",
            "spotanim.sp_attackglow_bandos",
        ),
        Ranged(
            "loc.tfa_ocellus_artifact_damage_ranged",
            "loc.tfa_ocellus_artifact_damage_ranged_inactive",
            "spotanim.sp_attackglow_armadyl",
        ),
    }

    fun place(middle: CoordGrid) {
        clear()
        val corners =
            listOf(
                middle.translate(-CORNER, -CORNER),
                middle.translate(CORNER, -CORNER),
                middle.translate(CORNER, CORNER),
                middle.translate(-CORNER, CORNER),
            )
        for ((corner, kind) in corners.zip(Kind.entries.shuffled())) {
            val loc = deps.locRepo.add(corner, kind.active, Int.MAX_VALUE, ANGLE, SHAPE)
            censers.add(Censer(kind, loc))
        }
    }

    fun clear() {
        for (censer in censers) deps.locRepo.del(censer.loc, Int.MAX_VALUE)
        censers.clear()
    }

    fun resetCharges(players: List<Player>) {
        for (player in players) player.charges = 0
    }

    /** Lights the censer at [at], returning why it can't be if it can't. */
    fun light(boss: Npc, at: BoundLocInfo): String? {
        val censer = censers.firstOrNull { it.loc.coords == at.coords } ?: return NOTHING
        if (censer.lit || boss.visType.id != finalForm || boss.hitpoints <= 0) return NOTHING
        censer.lit = true
        deps.worldRepo.locAnim(censer.loc, ACTIVATE_SEQ)
        deps.worldQueues.add(EFFECT_DELAY) { help(boss, censer.kind) }
        deps.worldQueues.add(INACTIVE_DELAY) {
            if (censer in censers) {
                deps.locRepo.add(censer.loc.coords, censer.kind.inactive, Int.MAX_VALUE, ANGLE, SHAPE)
            }
        }
        return null
    }

    private fun help(boss: Npc, kind: Kind) {
        for (member in OcellusArena.players(boss, deps.playerList)) {
            member.spotanim(kind.glow, slot = GLOW_SLOT)
            when (kind) {
                Kind.Heal -> {
                    member.statHeal(HITPOINTS, RESTORE, 0)
                    member.statHeal(PRAYER, RESTORE, 0)
                }
                Kind.Spec -> energy.addSpecialEnergy(member, SPEC_RESTORE)
                Kind.Melee -> member.charges = member.charges or MELEE_CHARGE
                Kind.Ranged -> member.charges = member.charges or RANGED_CHARGE
            }
        }
    }

    fun modify(event: NpcHitEvents.Modify) {
        val hit = event.hit
        if (!hit.isFromPlayer) return
        val charge =
            when (hit.type) {
                HitType.Melee -> MELEE_CHARGE
                HitType.Ranged -> RANGED_CHARGE
                else -> return
            }
        val uid = hit.sourceUid ?: return
        val attacker = PlayerUid(uid).resolve(deps.playerList) ?: return
        if (attacker.charges and charge == 0) return
        attacker.charges = attacker.charges and charge.inv()
        hit.damage = maxOf(hit.damage, attacker.maxHit)
    }

    companion object {
        const val NOTHING = "Nothing interesting happens."

        private const val CHARGES = "varp.ocellus_censer"
        private const val MAX_HIT = "varp.com_maxhit"
        private const val MELEE_CHARGE = 1
        private const val RANGED_CHARGE = 2

        private const val CORNER = 11
        private val ANGLE = LocAngle[0]
        private val SHAPE = LocShape.CentrepieceStraight

        private const val ACTIVATE_SEQ = "seq.ornament_censer_activate"
        private const val EFFECT_DELAY = 1
        private const val INACTIVE_DELAY = 3
        private const val GLOW_SLOT = 1

        private const val HITPOINTS = "stat.hitpoints"
        private const val PRAYER = "stat.prayer"
        private const val RESTORE = 50
        private const val SPEC_RESTORE = 500
    }
}
