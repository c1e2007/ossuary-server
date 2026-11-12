package org.rsmod.content.bosses.yama

/**
 * Every cache symbol the Yama fight touches. Confirmed against the cache gamevals; each constant's
 * reference id is in its trailing comment. Read from here so no symbol is typed inline twice.
 *
 * Members are `internal` rather than `private` so the other Yama files can read them.
 */
internal object YamaIds {
    // npcs
    const val VOICE = "npc.voice_of_yama" // 7668
    const val VOICE_1OP = "npc.voice_of_yama_1op" // 14183
    const val VOICE_2OP = "npc.voice_of_yama_2op" // 14184
    const val VOICE_3OP = "npc.voice_of_yama_3op" // 14185
    const val THRONE = "npc.yama_throne" // 123
    const val YAMA_OCCUPIED = "npc.yama_throne_occupied" // 124
    const val YAMA_UNOCCUPIED = "npc.yama_throne_unoccupied" // 14157
    const val YAMA = "npc.yama" // 14176
    const val JUDGE = "npc.yama_judge_of_yama" // 14180
    const val VOID_FLARE = "npc.yama_voidflare" // 14179
    const val METEOR = "npc.yama_meteor_npc" // 14182
    const val FIRE_SURGE = "npc.fire_surge_raised" // 13507
    const val PET = "npc.yama_pet" // 14204

    // locs
    const val EXIT = "loc.yama_exit" // 56249
    const val STEPPING_STONE = "loc.yama_steppingstone" // 56246
    const val FLAMES = "loc.yama_firewall" // 56264
    const val FLAMES_OPEN = "loc.invisible_type8_nonblocking" // 29733
    const val GLYPH_SHADOW = "loc.floorkit_summoning03_full01" // 56335
    const val GLYPH_FIRE = "loc.floorkit_summoning03_full02" // 56336
    const val GLYPH_INACTIVE = "loc.floorkit_summoning03_inactive" // 56337
    const val GLYPH_SHADOW_OUT = "loc.floorkit_summoning03_full01_deactivate" // 56338
    const val GLYPH_FIRE_OUT = "loc.floorkit_summoning03_full02_deactivate" // 56339
    const val CHASM_ROPE = "loc.cof_over_falloff2_rope" // 30236
    const val CHASM_SHORTCUT = "loc.cof_shortcut_top" // 56242

    // varbits and varps
    const val VB_VOICE_PROGRESS = "varbit.chasm_voice_progress" // 16284
    const val VB_THRONE_STATE = "varbit.yama_throne_state" // 16286
    const val VB_MULTIWAY = "varbit.multiway_indicator" // 4605
    const val VB_TRANSITION_PHASE = "varbit.yama_transition_phase" // 16550
    const val VB_TRANSITION_DAMAGE = "varbit.yama_transition_damage_taken" // 16549
    const val VB_MINIMAP = "varbit.minimap_state" // 6719
    const val VB_BURN_TOTAL = "varbit.player_own_burn_damage_total" // 10964
    const val VB_BURN_SPLAT = "varbit.player_own_burn_damage_hitsplat_size" // 10965
    const val VB_BUSY = "varbit.busy" // 12393
    const val VP_KILLS = "varp.total_yama_kills" // 4701
    const val PROTECT_MELEE = "varbit.prayer_protectfrommelee"
    const val PROTECT_MISSILES = "varbit.prayer_protectfrommissiles"
    const val PROTECT_MAGIC = "varbit.prayer_protectfrommagic"

    // sequences
    const val SEQ_MAGIC = "seq.npc_yama01_magic01" // 12144
    const val SEQ_SPECIAL = "seq.npc_yama01_magic02" // 12145
    const val SEQ_MELEE = "seq.npc_yama01_melee01" // 12146
    const val SEQ_TRANSITION = "seq.npc_yama01_portal01" // 12147
    const val SEQ_STOMP = "seq.npc_yama01_stomp01" // 12148
    const val SEQ_PLAYER_FALL = "seq.human_yama_portalfall" // 12149
    const val SEQ_PLAYER_LAND = "seq.human_yama_portal_fall_on_face" // 12152
    const val SEQ_THRONE_SUMMON = "seq.npc_yama_noncombat_summon01" // 12154
    const val SEQ_SUMMON = "seq.npc_yama_summon01" // 12156
    const val SEQ_DESPAWN = "seq.npc_yama01_despawn01" // 12161
    const val SEQ_THRONE_RESPAWN = "seq.npc_yama_noncombat_spawn02" // 12165
    const val SEQ_JUMP = "seq.human_yama_shortjump01" // 12196
    const val SEQ_METEOR_MARKER = "seq.vfx_yama_meteor_impact_02" // 12118
    const val SEQ_FLARE_SPAWN = "seq.npc_voidflare_spawn" // 12135
    const val SEQ_FLARE_EXPLODE = "seq.npc_voidflare_explode" // 12136
    const val SEQ_JUDGE_CAST = "seq.demon_casting" // 69
    const val SEQ_JUDGE_DEATH = "seq.judgeofyama_death" // 9042
    const val SEQ_PICKUP = "seq.human_pickupfloor" // 827

    // spotanims (the reference's "projectiles" are spotanim ids too)
    const val SPOT_THRONE_SUMMON = "spotanim.vfx_yama_noncombat_summon_spotanim01" // 3278
    const val SPOT_SUMMON = "spotanim.vfx_yama_combat_summon_spotanim01" // 3279
    const val SPOT_MAGIC = "spotanim.vfx_npc_yama_magic_fire_spotanim01" // 3246
    const val SPOT_MAGIC_IMPACT = "spotanim.vfx_player_yama_magic_fire_impact01" // 3247
    const val SPOT_RANGED = "spotanim.vfx_npc_yama_magic_shadow_spotanim01" // 3243
    const val SPOT_RANGED_IMPACT = "spotanim.vfx_player_yama_magic_shadow_impact01" // 3244
    const val SPOT_ROCK_CAST = "spotanim.vfx_yama_flaming_rock_spotanim_01" // 3253
    const val SPOT_ROCK_PROJ = "spotanim.vfx_yama_flaming_rock_projectile_01" // 3254
    const val SPOT_ROCK_IMPACT = "spotanim.vfx_yama_flaming_rock_impact_01" // 3255
    const val SPOT_SPIKE_CAST = "spotanim.vfx_yama_shadow_spike_spotanim_01" // 3256
    const val SPOT_SPIKE_PROJ = "spotanim.vfx_yama_shadow_spike_projectile_01" // 3257
    const val SPOT_SPIKE_IMPACT = "spotanim.vfx_yama_shadow_spike_impactanim_01" // 3258
    const val SPOT_METEOR_CAST = "spotanim.vfx_yama_meteor_spotanim01" // 3270
    const val SPOT_METEOR_MARKER = "spotanim.vfx_yama_meteor_projectile_02" // 3272
    const val SPOT_STOMP_CAST = "spotanim.vfx_yama_shadow_root_spotanim_01" // 3259
    const val SPOT_STOMP_PROJ = "spotanim.vfx_yama_shadow_root_projectile_01" // 3260
    const val SPOT_STOMP_IMPACT = "spotanim.vfx_yama_shadow_root_impact_01" // 3261
    const val SPOT_IMMUNITY_FIRE = "spotanim.vfx_yama_fire_immunity" // 3280
    const val SPOT_IMMUNITY_SHADOW = "spotanim.vfx_yama_shadow_immunity" // 3281
    const val SPOT_FLARE_EXPLODE_FIRE = "spotanim.vfx_voidflare_explode_red" // 3237
    const val SPOT_FLARE_EXPLODE_SHADOW = "spotanim.vfx_voidflare_explode_blue" // 3238
    const val SPOT_FLARE_IMPACT_FIRE = "spotanim.vfx_voidflare_human_impact_red" // 3241
    const val SPOT_FLARE_IMPACT_SHADOW = "spotanim.vfx_voidflare_human_impact_blue" // 3242
    const val SPOT_WAVE_SOUTH_WEST = "spotanim.vfx_shadow_wall_02" // 3265
    const val SPOT_WAVE_SOUTH_EAST = "spotanim.vfx_shadow_wall_03" // 3266
    const val SPOT_STREAK = "spotanim.vfx_fire_wall_01" // 3267
    const val SPOT_CRASH_ROCK = "spotanim.vfx_player_yama_falling_rock_impact01" // 3262
    const val SPOT_CRASH_SHADOW = "spotanim.vfx_flaming_skull_shadow_impact01" // 3252
    const val SPOT_FLOOR_PORTAL = "spotanim.vfx_yama_floor_portal_spotanim01" // 3274
    const val SPOT_LAND = "spotanim.vfx_yama_floor_portal_spotanim02" // 3275
    const val SPOT_TRANSITION = "spotanim.vfx_yama_portal_shadow_spotanim01" // 3276
    const val SPOT_DESPAWN = "spotanim.vfx_yama_combat_despawn_spotanim01" // 3284
    const val SPOT_THRONE_RESPAWN = "spotanim.vfx_yama_noncombat_spawn_spotanim02" // 3286
    const val SPOT_SURGE_IMPACT = "spotanim.firesurge_impact" // 1466
    const val SPOT_FIREBALL_WARN = "spotanim.gargboss_debris_shadow_90" // 1446
    const val SPOT_FIREBALL_PROJ = "spotanim.luc2_undead_demon_fireball_large_proj" // 2855
    const val SPOT_FIREBALL_IMPACT = "spotanim.luc2_undead_demon_fireball_large_impact" // 2856

    // dialogue/interface helpers
    const val IFACE_FADE = "interface.fade_overlay" // 174
}
