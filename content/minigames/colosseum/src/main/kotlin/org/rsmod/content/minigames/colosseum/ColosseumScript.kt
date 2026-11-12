package org.rsmod.content.minigames.colosseum

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ColosseumScript
@Inject
constructor(private val runs: ColosseumRuns, private val sol: ColosseumSol) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.colosseum_entrance_outside") { goDown() }
        onOpLoc1("loc.colosseum_exit_lobby") { goUp() }
        onOpLoc1("loc.colosseum_entrance") { tunnel() }
        onOpLoc1("loc.colosseum_bank") { bankChest() }

        onOpNpc1(ColosseumNpcs.MINIMUS_LOBBY) { startDialogue(it.npc) { lobbyMinimus() } }
        onOpNpc1(ColosseumNpcs.MINIMUS_ARENA) { openIntermission() }
        onOpNpc3(ColosseumNpcs.MINIMUS_ARENA) { askToLeave() }

        for ((index, button) in MOD_BUTTONS.withIndex()) {
            onIfModalButton(button) { selectModifier(index + 1) }
        }
        onIfModalButton(CONTINUE_BUTTON) { confirm() }

        onEvent<InstancePlayerLeaveEvent>(instanceEventId(ColosseumRuns.KEY)) {
            runs.runFor(player)?.let { runs.end(it) }
            player.colosseumSelected = 0
        }
    }

    private suspend fun ProtectedAccess.goDown() {
        withInstanceEnterTransition(InstanceEnterTransition()) {
            telejump(Colosseum.LOBBY, TeleportType.Exempt)
        }
    }

    private suspend fun ProtectedAccess.goUp() {
        withInstanceEnterTransition(InstanceEnterTransition()) {
            telejump(Colosseum.CITY, TeleportType.Exempt)
        }
    }

    private suspend fun ProtectedAccess.tunnel() {
        if (!player.colosseumIntro) {
            mes("Oi! Where d'you think you're going? Talk to Minimus first.")
            return
        }
        if (player.colosseumNoWarning) {
            with(runs) { enterArena { begin(it) } }
            return
        }
        mesbox(ENTRY_WARNING)
        val choice =
            choice3(
                "Yes, I understand the risks.",
                1,
                "Yes, and don't ask again.",
                2,
                "No.",
                3,
                title = "Are you sure you wish to enter the Fortis Colosseum?",
            )
        when (choice) {
            1 -> with(runs) { enterArena { begin(it) } }
            2 -> {
                player.colosseumNoWarning = true
                with(runs) { enterArena { begin(it) } }
            }
        }
    }

    private suspend fun ProtectedAccess.bankChest() {
        if (player.colosseumGlory < Colosseum.BANK_GLORY) {
            mes("You do not have enough Glory to use that.")
            return
        }
        tryOpenBank()
    }

    private suspend fun Dialogue.lobbyMinimus() {
        if (!player.colosseumIntro) {
            chatNpc(happy, "Well, well! Fresh blood for the sands! I'm Minimus, keeper of this place.")
            chatNpc(
                neutral,
                "Beyond that tunnel lies the Colosseum. Survive the waves, choose your modifiers, " +
                    "and the glory is yours.",
            )
            player.colosseumIntro = true
            return
        }
        chatNpc(happy, "Welcome back, ${player.colosseumRank}. Ready to face the sands?")
    }

    private fun ProtectedAccess.begin(run: ColosseumRun) {
        resetVars(player)
        player.colosseumCurrentGlory = 0
        val minimus = runs.spawn(run, ColosseumNpcs.MINIMUS_ARENA, run.tile(Colosseum.MINIMUS_START))
        run.minimus = minimus
        run.sol = runs.spawn(run, ColosseumNpcs.SOL_SEATED, run.tile(Colosseum.SOL_SEAT))
        minimus?.say("A ${player.colosseumRank} approaches!")
        runs.tickLoop(run) { tick(it) }
    }

    private fun resetVars(player: Player) {
        player.colosseumWaveStart = 0
        player.colosseumWaveDamage = 0
        player.colosseumLastModifierGlory = 0
        player.colosseumSelected = 0
    }

    private suspend fun ProtectedAccess.openIntermission() {
        val run = runs.runFor(player) ?: return
        if (run.stage != RunStage.INTERMISSION) return
        run.offered = run.modifiers.offer(run.wave + 1, run.random)
        player.colosseumSelected = 0
        syncModifierVarbits(player, run)
        ifOpenMainModal("interface.colosseum_intermission_2")
        runClientScript(
            INTERMISSION_INIT.asRSCM(RSCMType.CLIENTSCRIPT),
            run.wave,
            run.offered.getOrNull(0)?.key ?: -1,
            run.offered.getOrNull(1)?.key ?: -1,
            run.offered.getOrNull(2)?.key ?: -1,
            0,
            0,
            0,
            0,
        )
    }

    private fun ProtectedAccess.selectModifier(choice: Int) {
        val run = runs.runFor(player) ?: return
        if (run.stage != RunStage.INTERMISSION || choice !in 1..run.offered.size) return
        player.colosseumSelected = choice
    }

    private fun ProtectedAccess.confirm() {
        val run = runs.runFor(player) ?: return
        if (run.stage != RunStage.INTERMISSION) return
        val chosen = run.offered.getOrNull(player.colosseumSelected - 1) ?: return
        run.modifiers.add(chosen)
        syncModifierVarbits(player, run)
        player.colosseumLastModifierGlory = chosen.gloryPerWave
        player.colosseumSelected = 0
        ifClose()
        run.minimus?.let { runs.remove(it) }
        run.minimus = null
        run.stage = RunStage.STARTING
        run.startingIn = Colosseum.WAVE_START_DELAY
    }

    private suspend fun ProtectedAccess.askToLeave() {
        val run = runs.runFor(player) ?: return
        if (run.stage != RunStage.INTERMISSION && run.stage != RunStage.FINISHED) return
        val early = run.stage == RunStage.INTERMISSION && run.wave > 0
        if (early) {
            mesbox(
                "Leaving now will forfeit your run, with you only being rewarded the items you " +
                    "have earned so far."
            )
        }
        val sure =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = if (early) "Are you sure you wish to leave early?" else "Are you sure you wish to leave?",
            )
        if (sure) {
            with(runs) { leaveArena() }
        }
    }

    private fun syncModifierVarbits(player: Player, run: ColosseumRun) {
        for (modifier in ColosseumModifier.entries) {
            player.setModifierTier(modifier, run.modifiers.tier(modifier))
        }
    }

    private fun tick(run: ColosseumRun) {
        when (run.stage) {
            RunStage.STARTING -> {
                run.startingIn--
                if (run.startingIn <= 0) startWave(run)
            }
            RunStage.WAVE -> waveTick(run)
            else -> Unit
        }
    }

    private fun startWave(run: ColosseumRun) {
        val player = run.player
        val wave = run.wave + 1
        run.stage = RunStage.WAVE
        run.waveTicks = 0
        run.reinforced = false
        run.waveDamage = 0
        run.hitByEnemy = false
        if (wave > player.colosseumHighestWave) player.colosseumHighestWave = wave
        player.colosseumWaveDamage = 0
        player.mes("<col=ef1020>Wave: $wave</col>")
        if (wave == Colosseum.FINAL_WAVE) {
            sol.begin(run)
            return
        }
        val types = ColosseumWaves.startingNpcs(wave, run.modifiers, run.random)
        for (spawn in ColosseumWaves.placeWave(types, run.playerTile(), run.random)) {
            spawnEnemy(run, spawn)
        }
    }

    private fun spawnEnemy(run: ColosseumRun, spawn: WaveSpawn) {
        val npc = runs.spawn(run, spawn.type, run.tile(spawn.x, spawn.z)) ?: return
        run.npcs += npc
        runs.engage(run, npc)
    }

    private fun waveTick(run: ColosseumRun) {
        run.waveTicks++
        if (run.fight != null) {
            sol.tick(run)
            sol.tendSand(run)
            return
        }
        run.npcs.removeAll { it.hitpoints <= 0 || !it.isSlotAssigned }
        if (run.npcs.isEmpty()) {
            completeWave(run)
            return
        }
        if (!run.reinforced && run.waveTicks >= Colosseum.REINFORCEMENT_TICKS) {
            run.reinforced = true
            val types = ColosseumWaves.reinforcements(run.wave + 1, run.modifiers)
            for (spawn in ColosseumWaves.placeReinforcements(types, run.playerTile(), run.random)) {
                spawnEnemy(run, spawn)
            }
        }
    }

    private fun completeWave(run: ColosseumRun) {
        run.wave++
        run.totalTicks += run.waveTicks
        ColosseumEffects.waveCompleted(run)
        run.stage = RunStage.INTERMISSION
        run.player.colosseumWavesCompleted = run.player.colosseumWavesCompleted + 1
        run.player.mes(
            "Wave ${run.wave} completed! Duration: <col=ef1020>${run.secondsString(run.waveTicks)}</col>"
        )
        val tile = run.player.coords.translate(0, 1)
        run.minimus = runs.spawn(run, ColosseumNpcs.MINIMUS_ARENA, tile)
    }

    private companion object {
        const val INTERMISSION_INIT = "clientscript.[clientscript,colosseum_intermission_2_init]"
        const val CONTINUE_BUTTON = "component.colosseum_intermission_2:right_button"
        val MOD_BUTTONS =
            listOf(
                "component.colosseum_intermission_2:mod_button_1",
                "component.colosseum_intermission_2:mod_button_2",
                "component.colosseum_intermission_2:mod_button_3",
            )
        const val ENTRY_WARNING =
            "You are about to enter the Fortis Colosseum. Within, you will face<br>multiple " +
                "waves of <col=b30000>deadly foes</col>. Dying at any point is <col=b30000>not " +
                "considered a<br><col=b30000>safe death</col>. Apart from between waves, there is " +
                "no exit within the<br>Colosseum, but teleporting out is allowed."
    }
}
