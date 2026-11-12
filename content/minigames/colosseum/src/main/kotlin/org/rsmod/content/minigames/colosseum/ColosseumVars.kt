package org.rsmod.content.minigames.colosseum

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.colosseumIntro by boolVarBit("varbit.colosseum_master_intro")
internal var Player.colosseumNoWarning by boolVarBit("varbit.colosseum_entry_warning")
internal var Player.colosseumSelected by intVarBit("varbit.colosseum_selected_modifier")
internal var Player.colosseumHighestWave by intVarBit("varbit.colosseum_highest_wave")
internal var Player.colosseumSolFailures by intVarBit("varbit.colosseum_sol_failures")
internal var Player.colosseumGlory by intVarp("varp.colosseum_glory")
internal var Player.colosseumCurrentGlory by intVarp("varp.colosseum_current_glory")
internal var Player.colosseumWaveStart by intVarp("varp.colosseum_wave_start_time")
internal var Player.colosseumWaveDamage by intVarp("varp.colosseum_wave_damage_taken")
internal var Player.colosseumLastModifierGlory by intVarp("varp.colosseum_last_modifier_glory")
internal var Player.colosseumWavesCompleted by intVarp("varp.total_colosseum_waves_completed")

internal val Player.colosseumRank: String
    get() = Colosseum.rankFor(colosseumGlory)


private var Player.tierMantimayhem by intVarBit("varbit.colosseum_modifier_mantimayhem_stacks_client")
private var Player.tierReentry by intVarBit("varbit.colosseum_modifier_reentry_stacks_client")
private var Player.tierBees by intVarBit("varbit.colosseum_modifier_toxicity_stacks_client")
private var Player.tierVolatility by intVarBit("varbit.colosseum_modifier_volatility_stacks_client")
private var Player.tierBlasphemy by intVarBit("varbit.colosseum_modifier_blasphemy_stacks_client")
private var Player.tierRelentless by intVarBit("varbit.colosseum_modifier_relentless_stacks_client")
private var Player.tierDoom by intVarBit("varbit.colosseum_modifier_doom_stacks_client")
private var Player.tierSolarflare by intVarBit("varbit.colosseum_modifier_solarflare_stacks_client")
private var Player.tierMyopia by intVarBit("varbit.colosseum_modifier_myopia_stacks_client")
private var Player.tierFrailty by intVarBit("varbit.colosseum_modifier_frailty_stacks_client")

internal fun Player.setModifierTier(modifier: ColosseumModifier, tier: Int) {
    when (modifier) {
        ColosseumModifier.MANTIMAYHEM -> tierMantimayhem = tier
        ColosseumModifier.REENTRY -> tierReentry = tier
        ColosseumModifier.BEES -> tierBees = tier
        ColosseumModifier.VOLATILITY -> tierVolatility = tier
        ColosseumModifier.BLASPHEMY -> tierBlasphemy = tier
        ColosseumModifier.RELENTLESS -> tierRelentless = tier
        ColosseumModifier.DOOM -> tierDoom = tier
        ColosseumModifier.SOLARFLARE -> tierSolarflare = tier
        ColosseumModifier.MYOPIA -> tierMyopia = tier
        ColosseumModifier.FRAILTY -> tierFrailty = tier
        ColosseumModifier.QUARTET,
        ColosseumModifier.TOTEMIC,
        ColosseumModifier.DYNAMIC_DUO,
        ColosseumModifier.RED_FLAG -> Unit
    }
}
