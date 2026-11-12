package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.BOSS_SPOT_SLOT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.HEADICON_GRAPHIC
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusRefs.HEADICON_SLOT

internal class OcellusFinalAbilities {
    /**
     * The last quadrant's 60-tick loop, with no other attacks. The boss deflects melee, pulls
     * half the party's hitpoints into a soul fragment, then swings a charged attack in a random
     * style under soul split and deflects missiles, and does it all again before the rifts open and
     * the loop restarts. Protecting from the style still costs a chip of damage.
     */
    fun curses(): Effect {
        fun hybrid(at: Int): Effect {
            fun swing(spot: String, hitType: HitType): Effect {
                val blocked =
                    hit {
                        damage(CURSES_HYBRID_BLOCKED_DAMAGE).roll()
                        type(HitType.Typeless)
                    }
                val landed =
                    hit {
                        damage(CURSES_HYBRID_DAMAGE).roll()
                        type(hitType)
                    }
                val strike = whenever(Condition.TargetPraying(hitType), blocked, otherwise = landed)
                return sequence(
                    anim(HYBRID_SEQ),
                    spotanim(spot, slot = BOSS_SPOT_SLOT),
                    wait(HYBRID_HIT_DELAY),
                    onEach(playersIn(OcellusArena.FLOOR), strike),
                )
            }
            return sequence(
                wait(at),
                oneOf(
                    swing(HYBRID_MELEE_SPOT, HitType.Melee),
                    swing(HYBRID_MAGIC_SPOT, HitType.Magic),
                    swing(HYBRID_RANGED_SPOT, HitType.Ranged),
                ),
            )
        }
        fun fragment(levitating: Boolean) =
            sequence(
                anim(SOUL_START_SEQ),
                spotanim(if (levitating) SOUL_LEVITATING_SPOT else SOUL_IDLE_SPOT, slot = BOSS_SPOT_SLOT),
                external(OcellusPhaseTwo.SOULS_TAKE, params = !levitating),
                external(OcellusPhaseTwo.VOID_CHAIN),
            )
        fun icon(index: Int, curse: Int) =
            sequence(headIcon(HEADICON_SLOT, HEADICON_GRAPHIC, index), setVarn(OcellusCurses.VARN, curse))
        val clearIcon =
            sequence(clearHeadIcon(HEADICON_SLOT), setVarn(OcellusCurses.VARN, OcellusCurses.NONE))
        return parallel(
            sequence(
                anim(DEFLECT_IN_SEQ),
                idleAnim(DEFLECT_LOOP_SEQ),
                icon(HEADICON_DEFLECT_MELEE, OcellusCurses.DEFLECT_MELEE),
                external(OcellusPhaseTwo.DEFLECT_START, params = OcellusDeflect.Style.Ranged),
            ),
            sequence(wait(CURSES_LOOP_AT), anim(DEFLECT_LOOP_SEQ)),
            sequence(wait(CURSES_FRAGMENT_AT), fragment(levitating = true)),
            sequence(
                wait(CURSES_DEFLECT_OUT_AT),
                anim(DEFLECT_OUT_SEQ),
                clearIdleAnim(),
                external(OcellusPhaseTwo.SOULS_RELEASE),
                external(OcellusPhaseTwo.DEFLECT_END),
            ),
            hybrid(CURSES_HYBRID_AT),
            sequence(wait(CURSES_SOUL_SPLIT_AT), icon(HEADICON_SOUL_SPLIT, OcellusCurses.SOUL_SPLIT)),
            sequence(
                wait(CURSES_MELEE_ARENA_AT),
                clearIcon,
                external(OcellusPhaseTwo.DEFLECT_START, params = OcellusDeflect.Style.Melee),
            ),
            sequence(
                wait(CURSES_DEFLECT_MISSILES_AT),
                icon(HEADICON_DEFLECT_MISSILES, OcellusCurses.DEFLECT_MISSILES),
            ),
            sequence(wait(CURSES_SECOND_FRAGMENT_AT), fragment(levitating = false)),
            sequence(
                wait(CURSES_FRAGMENT_END_AT),
                anim(SOUL_END_SEQ),
                external(OcellusPhaseTwo.SOULS_RELEASE),
                external(OcellusPhaseTwo.DEFLECT_END),
            ),
            hybrid(CURSES_SECOND_HYBRID_AT),
            sequence(
                wait(CURSES_SECOND_SOUL_SPLIT_AT),
                icon(HEADICON_SOUL_SPLIT, OcellusCurses.SOUL_SPLIT),
            ),
            sequence(wait(CURSES_LENGTH - 1), clearIcon, nextAttackIn(1)),
        )
    }

    private companion object {
        const val HEADICON_SOUL_SPLIT = 11
        const val HEADICON_DEFLECT_MELEE = 15
        const val HEADICON_DEFLECT_MISSILES = 16
        const val CURSES_LENGTH = 60
        const val CURSES_LOOP_AT = 2
        const val CURSES_FRAGMENT_AT = 3
        const val CURSES_DEFLECT_OUT_AT = 21
        const val CURSES_HYBRID_AT = 24
        const val CURSES_SOUL_SPLIT_AT = 25
        const val CURSES_MELEE_ARENA_AT = 30
        const val CURSES_DEFLECT_MISSILES_AT = 31
        const val CURSES_SECOND_FRAGMENT_AT = 34
        const val CURSES_FRAGMENT_END_AT = 50
        const val CURSES_SECOND_HYBRID_AT = 52
        const val CURSES_SECOND_SOUL_SPLIT_AT = 53
        const val HYBRID_HIT_DELAY = 4

        // Spectator captures: 7 or 8 on every player but one, and 38-51 on those whose hit healed him.
        val CURSES_HYBRID_BLOCKED_DAMAGE = 7..8
        val CURSES_HYBRID_DAMAGE = 38..51
        const val DEFLECT_IN_SEQ = "seq.npc_ocellus_phase3_deflect_in"
        const val DEFLECT_LOOP_SEQ = "seq.npc_ocellus_phase3_deflect_loop"
        const val DEFLECT_OUT_SEQ = "seq.npc_ocellus_phase3_deflect_out"
        const val SOUL_START_SEQ = "seq.npc_ocellus_phase3_soul_fragment_start"
        const val SOUL_END_SEQ = "seq.npc_ocellus_phase3_soul_fragment_end"
        const val SOUL_IDLE_SPOT = "spotanim.vfx_ocellus_soul_fragment_ocellus_idle"
        const val SOUL_LEVITATING_SPOT = "spotanim.vfx_ocellus_soul_fragment_ocellus_levitating_idle"
        const val HYBRID_SEQ = "seq.npc_ocellus_phase3_melee_hybrid"
        const val HYBRID_MELEE_SPOT = "spotanim.vfx_ocellus_phase3_melee_hybrid"
        const val HYBRID_MAGIC_SPOT = "spotanim.vfx_ocellus_phase3_melee_hybrid_magic"
        const val HYBRID_RANGED_SPOT = "spotanim.vfx_ocellus_phase3_melee_hybrid_ranged"
    }
}
