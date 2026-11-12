package org.rsmod.content.raids.tfa.raid.encounter.ocellus

internal object OcellusRefs {
    const val WALKING = "walking"
    const val CORNER_CYCLE = "corner_cycle"
    const val CENTRE = "centre"
    const val BROKEN = "broken"
    const val WALK = "walk"
    const val VOLLEY_START = "volley_start"
    const val VOLLEY_END = "volley_end"
    const val NEXT = "next"
    const val PHASE_TWO = "phase_two"
    const val PHASE_THREE = "phase_three"
    val QUADRANTS = List(4) { "quadrant_${it + 1}" }
    const val UNTETHER_PERSONAL = "ocellus.untether_personal"
    const val UNTETHER_IMPACT = "ocellus.untether_impact"
    const val SPREAD_IMPACT = "spotanim.vfx_ocellus_magic_projanim_impact01"
    val QUADRANT_THRESHOLDS = listOf(0.74, 0.51, 0.26)
    const val VOLLEY_END_SEQ = "seq.npc_ocellus_phase1_basic_projectile_end"
    const val HEADICON_SLOT = 0
    const val HEADICON_GRAPHIC = 440
    const val BOSS_SPOT_SLOT = 2
    val THRESHOLDS = listOf(0.66, 0.25)
    const val TELEPORT_SPOT_SLOT = 2
    const val OPAQUE = 0
    const val INVISIBLE = 255
    const val FADE_IN_START = 0
    const val BOSS_SIZE = 3
}
