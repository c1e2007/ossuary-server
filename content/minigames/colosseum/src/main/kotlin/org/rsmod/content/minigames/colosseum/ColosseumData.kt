package org.rsmod.content.minigames.colosseum

import kotlin.math.abs
import kotlin.random.Random
import org.rsmod.map.CoordGrid

internal object ColosseumNpcs {
    const val MINIMUS_LOBBY = "npc.colosseum_master_outside"
    const val MINIMUS_ARENA = "npc.colosseum_master"
    const val SOL_SEATED = "npc.colosseum_boss_seated"
    const val SOL = "npc.colosseum_sol_p1"
    const val BERSERKER = "npc.colosseum_warbander_melee_male"
    const val ARCHER = "npc.colosseum_warbander_ranged_female"
    const val SEER = "npc.colosseum_warbander_mage_male"
    const val SHAMAN = "npc.colosseum_standard_mager"
    const val JAGUAR = "npc.colosseum_jaguar_warrior"
    const val JAVELIN = "npc.colosseum_javelin_colossus"
    const val MANTICORE = "npc.colosseum_manticore"
    const val SHOCKWAVE = "npc.colosseum_shockwave_colossus"
    const val MINOTAUR = "npc.colosseum_minotaur"
    const val MINOTAUR_ROUTING = "npc.colosseum_minotaur_routefind"
    const val BEES = "npc.colosseum_modifier_bees"
    const val CRYSTAL = "npc.colosseum_beam_crystal"
    const val TOTEM = "npc.colosseum_healing_totem"
    const val SOLAR_FLARE = "npc.colosseum_solar_flare"

    val WARBAND = setOf(BERSERKER, ARCHER, SEER)
}

internal object Colosseum {
    const val FINAL_WAVE = 12
    const val ARENA_REGION = 7216
    const val ARENA_BASE_X = 1792
    const val ARENA_BASE_Z = 3072

    val LOBBY = CoordGrid(1799, 9506, 0)
    val CITY = CoordGrid(1795, 3106, 0)
    val LOBBY_RESPAWN = CoordGrid(1804, 9508, 0)
    val LOBBY_MINIMUS = CoordGrid(1806, 9508, 0)
    val ARENA_START = CoordGrid(1824, 3094, 0)
    val MINIMUS_START = CoordGrid(1824, 3106, 0)
    val ARRIVAL_WALK = CoordGrid(1824, 3104, 0)
    val SOL_SEAT = CoordGrid(1823, 3123, 0)
    val CHEST = CoordGrid(1829, 3105, 0)
    val MINIMUS_BY_CHEST = CoordGrid(1830, 3103, 0)

    const val WAVE_START_DELAY = 5
    const val SECOND_GREETING_DELAY = 3
    const val REINFORCEMENT_TICKS = 67
    const val BANK_GLORY = 2000

    val RANKS: List<Pair<Int, String>> =
        listOf(
            20000 to "Grand Champion",
            16000 to "Champion",
            12000 to "Hero",
            8000 to "Gladiator",
            5000 to "Challenger",
            2000 to "Brawler",
            0 to "Rookie",
        )

    fun rankFor(glory: Int): String = RANKS.first { glory >= it.first }.second
}

internal data class ArenaTile(val x: Int, val z: Int)

internal data class WaveSpawn(val type: String, val x: Int, val z: Int)

internal object ColosseumWaves {
    private val SPAWN_POINTS =
        listOf(
            ArenaTile(1811, 3104),
            ArenaTile(1817, 3106),
            ArenaTile(1811, 3109),
            ArenaTile(1821, 3109),
            ArenaTile(1827, 3109),
            ArenaTile(1825, 3114),
            ArenaTile(1821, 3103),
            ArenaTile(1827, 3103),
            ArenaTile(1824, 3099),
            ArenaTile(1832, 3107),
            ArenaTile(1836, 3109),
            ArenaTile(1836, 3104),
        )
    private const val SPAWN_EXCLUSION = 4
    private const val FREMENNIK_MIN_X = 1821
    private const val FREMENNIK_MAX_X = 1827
    private const val FREMENNIK_MIN_Z = 3105
    private const val FREMENNIK_MAX_Z = 3111
    private val TRIO_OFFSETS =
        mapOf(
            ColosseumNpcs.BERSERKER to (0 to 1),
            ColosseumNpcs.SEER to (1 to 0),
            ColosseumNpcs.ARCHER to (-1 to 0),
        )
    private val QUARTET_OFFSET = 0 to -1
    private const val GATE_X = 1824
    private const val GATE_NORTH_Z = 3122
    private const val GATE_SOUTH_Z = 3091
    private const val GATE_SPLIT_Z = 3107

    fun startingNpcs(wave: Int, modifiers: ModifierSet, random: Random): List<String> {
        val npcs = mutableListOf(ColosseumNpcs.BERSERKER, ColosseumNpcs.ARCHER, ColosseumNpcs.SEER)
        if (modifiers.has(ColosseumModifier.QUARTET)) {
            npcs += ColosseumNpcs.WARBAND.random(random)
        }
        if (wave <= 6) {
            npcs += ColosseumNpcs.SHAMAN
        }
        val javelins =
            when {
                wave == 2 || wave == 3 -> wave - 1
                wave >= 5 -> 2 - (wave % 2)
                else -> 0
            }
        repeat(javelins) { npcs += ColosseumNpcs.JAVELIN }
        if (wave >= 4) {
            repeat(if (wave <= 8) 1 else 2) { npcs += ColosseumNpcs.MANTICORE }
        }
        if (wave == 7 || wave == 8 || wave == 11) {
            repeat(if (modifiers.has(ColosseumModifier.DYNAMIC_DUO)) 2 else 1) {
                npcs += ColosseumNpcs.SHOCKWAVE
            }
        }
        return npcs
    }

    fun reinforcements(wave: Int, modifiers: ModifierSet): List<String> {
        val npcs = mutableListOf<String>()
        if (wave <= 6) {
            npcs += ColosseumNpcs.JAGUAR
        }
        if (wave in 4..6 || wave == 10 || wave == 11) {
            npcs += ColosseumNpcs.SHAMAN
        }
        if (wave in 7..11) {
            val minotaur =
                if (modifiers.has(ColosseumModifier.RED_FLAG)) {
                    ColosseumNpcs.MINOTAUR_ROUTING
                } else {
                    ColosseumNpcs.MINOTAUR
                }
            npcs += minotaur
        }
        return npcs
    }

    fun placeWave(npcs: List<String>, player: ArenaTile, random: Random): List<WaveSpawn> {
        val baseX = random.nextInt(FREMENNIK_MIN_X, FREMENNIK_MAX_X + 1)
        val baseZ = random.nextInt(FREMENNIK_MIN_Z, FREMENNIK_MAX_Z + 1)
        val unusedOffsets = TRIO_OFFSETS.toMutableMap()
        val used = mutableSetOf<Int>()
        return npcs.map { type ->
            if (type in ColosseumNpcs.WARBAND) {
                val offset = unusedOffsets.remove(type) ?: QUARTET_OFFSET
                WaveSpawn(type, baseX + offset.first, baseZ + offset.second)
            } else {
                val free = SPAWN_POINTS.indices.filter { it !in used }
                val far =
                    free.filter {
                        maxOf(abs(SPAWN_POINTS[it].x - player.x), abs(SPAWN_POINTS[it].z - player.z)) >
                            SPAWN_EXCLUSION
                    }
                val index = (far.ifEmpty { free.ifEmpty { SPAWN_POINTS.indices.toList() } }).random(random)
                used += index
                WaveSpawn(type, SPAWN_POINTS[index].x, SPAWN_POINTS[index].z)
            }
        }
    }

    fun placeReinforcements(npcs: List<String>, player: ArenaTile, random: Random): List<WaveSpawn> {
        val gateZ = if (player.z > GATE_SPLIT_Z) GATE_NORTH_Z else GATE_SOUTH_Z
        return npcs.map { WaveSpawn(it, GATE_X - 1 + random.nextInt(4), gateZ) }
    }
}
