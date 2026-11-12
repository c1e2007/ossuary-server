package org.rsmod.api.game.process.npc

import org.junit.jupiter.api.Test
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NpcInteractionProcessorMeleeStepTest {
    /**
     * A melee-range (ap range of 1) npc that takes a real step this tick does not also attack. It
     * attacks from the tile it started the tick on, so an npc that begins a tick already adjacent
     * attacks and does not step, and one that has to step to get adjacent attacks one tick later.
     *
     * The script has no attack timer: it is always "ready". That models a boss whose timer has
     * elapsed, which is the case the rule exists for.
     */
    @Test
    fun GameTestState.`melee range npc that steps does not attack and one that starts adjacent does`() =
        runGameTest(MeleeApTestScript::class) {
            val npcCoord = CoordGrid(0, 1, 1, 0, 0)
            allocZoneCollision(npcCoord)
            allocZoneCollision(npcCoord.translateX(8))

            val playerCoord = npcCoord.translateX(3)
            val npc = spawnNpc(npcCoord, melee)
            player.enableWalk()
            player.teleport(playerCoord)
            npc.apPlayer2(player)

            // Out of range at the start of the tick and still out of range after stepping.
            advance(ticks = 1)
            assertEquals(npcCoord.translateX(1), npc.coords)
            assertMessageNotSent("Receive attack.")

            // This step brings the npc adjacent to the player. It moved, so it must not attack.
            advance(ticks = 1)
            assertEquals(npcCoord.translateX(2), npc.coords)
            assertMessageNotSent("Receive attack.")

            // The npc starts this tick adjacent: it attacks from where it stands and does not step.
            advance(ticks = 1)
            assertEquals(npcCoord.translateX(2), npc.coords)
            assertMessageSent("Receive attack.")

            // A stationary npc keeps attacking every tick; only a real step suppresses the attack.
            advance(ticks = 1)
            assertEquals(npcCoord.translateX(2), npc.coords)
            assertMessageSent("Receive attack.")
        }

    private class MeleeApTestScript : PluginScript() {
        override fun ScriptContext.startup() {
            onAiApPlayer2(melee) { combatAp(it.target) }
        }

        private fun StandardNpcAccess.combatAp(target: Player) {
            target.mes("Receive attack.")
        }
    }

    private companion object {
        private val melee =
            npcTypeFactory.create {
                name = "Melee"
                size = 1
                attackRange = 1
                maxRange = 100
            }
    }
}
