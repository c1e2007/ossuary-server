package org.rsmod.content.raids.tfa.raid.reward

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.eliteClueDropDenominator
import org.rsmod.game.entity.Player

internal object TfaChestKeys {
    const val STANDARD = "npc.tfa_rewards_chest_vault_closed_mine"
    const val UNIQUE = "npc.tfa_unique_chest_vault_closed"
    const val STANDARD_ROLLS = 3
    const val RARE_QUANTITY_ONE_IN = 51
    const val RARE_QUANTITY_MULTIPLIER = 5

    /** Objs rolled from the tertiary table, which the rare quantity multiplier never applies to. */
    val TERTIARY_OBJS =
        setOf(
            "obj.archivists_folio_empty",
            "obj.tfa_mote_air",
            "obj.tfa_mote_water",
            "obj.tfa_mote_earth",
            "obj.tfa_mote_fire",
            "obj.trail_elite_emote_exp1",
        )
}

private const val ELITE_CLUE = "obj.trail_elite_emote_exp1"
private const val ELITE_CLUE_DENOMINATOR = 12
private const val FOLIO = "obj.archivists_folio_empty"
private const val FOLIO_CHARGED = "obj.archivists_folio"

// PLACEHOLDER: the wiki states the unique odds are unknown; this is a per-player guess.
private const val UNIQUE_DENOMINATOR = 50

@field:RegisterDropTable
@JvmField
val tfaStandardChestDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "The Fractured Archive Chest",
        npcs = npcs(TfaChestKeys.STANDARD),
        mainTable =
            rsPlayerWeightedTable(total = 330) {
                name("The Fractured Archive Chest")
                11 weight "obj.cert_battlestaff" count 34..37
                11 weight "obj.dragon_platelegs" count 1
                11 weight "obj.dragon_plateskirt" count 1
                11 weight "obj.cert_rune_kiteshield" count 5..7
                11 weight "obj.naturerune" count 301..385
                11 weight "obj.astralrune" count 301..385
                11 weight "obj.bloodrune" count 301..385
                11 weight "obj.soulrune" count 301..380
                11 weight "obj.earthrune" count 3009..3551
                7 weight "obj.cert_unidentified_harralander" count 60..66
                7 weight "obj.cert_unidentified_ranarr" count 18..20
                7 weight "obj.cert_unidentified_toadflax" count 24..28
                7 weight "obj.snapdragon_seed" count 3..4
                7 weight "obj.kwuarm_seed" count 7..8
                7 weight "obj.snape_grass_seed" count 7..8
                7 weight "obj.yew_seed" count 4..5
                11 weight "obj.cert_sapphire" count 24..27
                11 weight "obj.cert_emerald" count 24..26
                11 weight "obj.cert_ruby" count 23..27
                11 weight "obj.cert_diamond" count 24..26
                6 weight "obj.cert_iron_ore" count 851..915
                6 weight "obj.cert_adamantite_ore" count 60..72
                6 weight "obj.cert_runite_ore" count 25..26
                55 weight "obj.ascension_shards" count 75..82
                11 weight "obj.xbows_bolt_tips_onyx" count 50..58
                11 weight "obj.cert_earth_orb" count 68..75
                7 weight "obj.cert_wine_of_zamorak" count 32..36
                6 weight "obj.desiccated_page" count 30..36
                6 weight "obj.cosmic_soul_catalyst" count 180..191
                6 weight "obj.cert_blankrune_high" count 1404..1632
                6 weight "obj.cert_mahogany_logs" count 140..152
                6 weight "obj.dragon_arrowheads" count 350..369
                6 weight "obj.cert_raw_shark" count 80..92
            },
        mainRolls = TfaChestKeys.STANDARD_ROLLS,
        tertiaries =
            rsPlayerTertiaryTable {
                boosted {
                    1 outOf 16 weight FOLIO count 1 condition { player -> !player.ownsFolio() }
                }
                boosted { 1 outOf 80 weight "obj.tfa_mote_air" count 1 }
                boosted { 1 outOf 80 weight "obj.tfa_mote_water" count 1 }
                boosted { 1 outOf 80 weight "obj.tfa_mote_earth" count 1 }
                boosted { 1 outOf 80 weight "obj.tfa_mote_fire" count 1 }
                1 outOf ELITE_CLUE_DENOMINATOR chance
                    DropRollItem(
                        obj = ELITE_CLUE,
                        count = 1,
                        condition = { player -> !player.hasEliteClueBoost() },
                        transformObj = { player -> player.clueScrollTransformObj(ELITE_CLUE) },
                    )
                1 outOf ELITE_CLUE_DENOMINATOR - 1 chance
                    DropRollItem(
                        obj = ELITE_CLUE,
                        count = 1,
                        condition = { player -> player.hasEliteClueBoost() },
                        transformObj = { player -> player.clueScrollTransformObj(ELITE_CLUE) },
                    )
            },
    )

@field:RegisterDropTable
@JvmField
val tfaUniqueChestDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "The Fractured Archive Unique Chest",
        npcs = npcs(TfaChestKeys.UNIQUE),
        tertiaries =
            rsPlayerTertiaryTable {
                boosted {
                    1 outOf UNIQUE_DENOMINATOR chance
                        rsPlayerWeightedTable(total = 15) {
                            name("The Fractured Archive Uniques")
                            5 weight "obj.rondache" count 1
                            4 weight "obj.zoryas_tome_uncharged" count 1
                            3 weight "obj.tfa_zeal_reward" count 1
                            2 weight "obj.ascension_crossbows" count 1
                            1 weight "obj.obligator_uncharged" count 1
                        }
                }
            },
    )

private fun Player.hasEliteClueBoost(): Boolean =
    eliteClueDropDenominator(ELITE_CLUE_DENOMINATOR) != ELITE_CLUE_DENOMINATOR

private fun Player.ownsFolio(): Boolean =
    FOLIO in inv ||
        FOLIO in worn ||
        FOLIO in invMap.getOrPut("inv.bank") ||
        FOLIO_CHARGED in inv ||
        FOLIO_CHARGED in worn ||
        FOLIO_CHARGED in invMap.getOrPut("inv.bank")
