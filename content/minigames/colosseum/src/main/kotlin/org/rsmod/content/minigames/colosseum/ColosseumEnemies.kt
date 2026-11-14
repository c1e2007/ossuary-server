package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ColosseumEnemies @Inject constructor(private val deps: BossDeps) : PluginScript() {
    override fun ScriptContext.startup() {
        for (spec in specs) {
            BossCombat.register(this, spec, deps)
            // The engine ends an npc's interaction when its target is farther than the cache
            // type's maxRange + attackRange (7 + 1 by default) from the npc's spawn tile, whatever
            // apRangeOverride says. Wave npcs spawn around the arena rim, so without this they
            // dropped their target once the player was more than a few tiles away. The arena is
            // about 30 tiles across, so one maxRange covers all of it.
            for (type in spec.npcTypes) {
                ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC))?.maxRange = ARENA_MAX_RANGE
            }
        }
    }

    private companion object {
        const val ARENA_MAX_RANGE = 40
        const val SPEED_TRIO = 6
        const val SPEED_FAST = 5

        val berserker =
            boss(ColosseumNpcs.BERSERKER) {
                stats(attackRate = SPEED_TRIO)
                val swing =
                    ability("swing") {
                        anim("seq.npc_fremennik_warbander_melee_human_sword_stab")
                        hit {
                            damage(0..29).roll()
                            type(Melee)
                        }
                    }
                phase("combat") { weightedSelectorRandom { +random(swing, weight = 1) } }
            }

        val seer =
            boss(ColosseumNpcs.SEER) {
                stats(attackRate = SPEED_TRIO)
                val cast =
                    ability("cast") {
                        anim("seq.npc_fremennik_warbander_mage_zaros_vertical_casting_walkmerge")
                        projectile(
                            spotanim = "spotanim.fireblast_travel",
                            config = ProjectileConfig.fixed(43, 31, 10, 30, 10),
                            hit = Effect.Hit(damage = Roll(0..12), type = Magic),
                        )
                    }
                phase("combat") { weightedSelectorRandom { +random(cast, weight = 1) } }
            }

        val archer =
            boss(ColosseumNpcs.ARCHER) {
                stats(attackRate = SPEED_TRIO)
                val shoot =
                    ability("shoot") {
                        anim("seq.npc_fremennik_warbander_archer_att_colosseum")
                        projectile(
                            spotanim = "spotanim.iron_arrow_travel",
                            config = ProjectileConfig.fixed(41, 36, 10, 30, 10),
                            hit = Effect.Hit(damage = Roll(0..14), type = Ranged),
                        )
                    }
                phase("combat") { weightedSelectorRandom { +random(shoot, weight = 1) } }
            }

        val shaman =
            boss(ColosseumNpcs.SHAMAN) {
                stats(attackRate = SPEED_FAST)
                val cast =
                    ability("cast") {
                        anim("seq.npc_serpent_mager_casting")
                        projectile(
                            spotanim = "spotanim.watersurge_travel",
                            config = ProjectileConfig(startHeight = 43, endHeight = 36, startDelay = 67, stepMultiplier = 4),
                            hit = Effect.Hit(damage = Roll(0..27), type = Magic),
                        )
                    }
                phase("combat") { weightedSelectorRandom { +random(cast, weight = 1) } }
            }

        val jaguar =
            boss(ColosseumNpcs.JAGUAR) {
                stats(attackRate = SPEED_FAST)
                val claws =
                    ability("claws") {
                        anim("seq.npc_jaguar_ranger_claws_attack")
                        hit {
                            damage(0..47).roll()
                            type(Melee)
                        }
                        hit {
                            damage(0..47).roll()
                            type(Melee)
                        }
                        hit {
                            damage(0..47).roll()
                            type(Melee)
                        }
                    }
                phase("combat") { weightedSelectorRandom { +random(claws, weight = 1) } }
            }

        val javelin =
            boss(ColosseumNpcs.JAVELIN) {
                stats(attackRate = SPEED_FAST)
                val throwJavelin =
                    ability("throw") {
                        anim("seq.npc_colossi_javelin_01_range_attack")
                        projectile(
                            spotanim = "spotanim.npc_colossi_javelin_01_spearhead",
                            config = ProjectileConfig(startHeight = 70, endHeight = 36, startDelay = 58, stepMultiplier = 3),
                            hit = Effect.Hit(damage = Roll(0..48), type = Ranged),
                        )
                    }
                phase("combat") { weightedSelectorRandom { +random(throwJavelin, weight = 1) } }
            }

        val shockwave =
            boss(ColosseumNpcs.SHOCKWAVE) {
                stats(attackRate = SPEED_FAST)
                val clap =
                    ability("clap") {
                        anim("seq.npc_colossi_shockwave_01_clapattack")
                        projectile(
                            spotanim = "spotanim.vfx_vardorvis_head_projectile_magic_01",
                            config = ProjectileConfig(startHeight = 45, endHeight = 31, startDelay = 51, stepMultiplier = 5),
                            hit = Effect.Hit(damage = Roll(0..56), type = Magic),
                        )
                    }
                phase("combat") { weightedSelectorRandom { +random(clap, weight = 1) } }
            }

        val manticore =
            boss(ColosseumNpcs.MANTICORE) {
                stats(attackRate = 10)
                fun orb(style: String) =
                    when (style) {
                        "magic" -> Triple("spotanim.vfx_manticore_01_projectile_magic_01", Magic, 31)
                        "ranged" -> Triple("spotanim.vfx_manticore_01_projectile_ranged_01", Ranged, 36)
                        else -> Triple("spotanim.vfx_manticore_01_projectile_melee_01", Melee, 31)
                    }
                fun volley(name: String, first: String, second: String) =
                    ability(name) {
                        anim("seq.npc_manticore_01_triple_charge")
                        wait(6)
                        anim("seq.npc_manticore_01_triple_throw")
                        for ((index, style) in listOf(first, second, "melee").withIndex()) {
                            val (spot, type, max) = orb(style)
                            projectile(
                                spotanim = spot,
                                config = ProjectileConfig.fixed(50, 31, 0, 25, 10),
                                hit = Effect.Hit(damage = Roll(0..max), type = type),
                            )
                            if (index < 2) wait(1)
                        }
                    }
                val usual = volley("volley_a", "magic", "ranged")
                val swapped = volley("volley_b", "ranged", "magic")
                phase("combat") {
                    weightedSelectorRandom {
                        +random(usual, weight = 1)
                        +random(swapped, weight = 1)
                    }
                }
            }

        fun minotaurSpec(npc: String) =
            boss(npc) {
                stats(attackRate = SPEED_FAST)
                val smash =
                    ability("smash") {
                        anim("seq.npc_minotaur_boss_attack_melee")
                        hit {
                            damage(0..74).roll()
                            type(Melee)
                            delay = 2
                        }
                    }
                phase("combat") { weightedSelectorRandom { +random(smash, weight = 1) } }
            }

        val specs: List<BossSpec> =
            listOf(
                berserker,
                seer,
                archer,
                shaman,
                jaguar,
                javelin,
                shockwave,
                manticore,
                minotaurSpec(ColosseumNpcs.MINOTAUR),
                minotaurSpec(ColosseumNpcs.MINOTAUR_ROUTING),
            )
    }
}
