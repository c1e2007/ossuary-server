package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc5
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lets the Voice (YamaVoice) start the domain's instance entry without holding the instance
 * script itself; [YamaInstance] sets [enter] when it starts up.
 */
@Singleton
internal class YamaEntryGate @Inject constructor() {
    var enter: (suspend ProtectedAccess.() -> Unit)? = null
}

class YamaInstance
@Inject
internal constructor(
    registry: BossInstanceRegistry,
    private val fights: YamaFights,
    private val gate: YamaEntryGate,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_yama"

    override fun area(): InstanceArea = INSTANCE

    /** Every entry comes up clean: no grace window, no dropped items left behind. */
    override fun destroyWhenEmpty(): Boolean = true

    override fun ScriptContext.configure() {
        gate.enter = { enterInstance() }

        onEnterPrelude { result, enter ->
            withInstanceEnterTransition(InstanceEnterTransition(message = ENTER_MESSAGE), enter)
            // A fresh domain is seated on creation; a rejoin with no fight left is seated too.
            when (result) {
                is InstanceManager.Result.Created ->
                    fights.seat(player, result.session, result.enter)
                is InstanceManager.Result.Joined ->
                    if (fights.fightOf(player) == null) {
                        fights.seat(player, result.session, result.enter)
                    }
                else -> Unit
            }
        }

        onEnterObject { enterInstance() }
        onExitObject { defaultLeaveFlow() }

        onOpLoc5(YamaIds.EXIT) { quickEscape() }

        onInstancePlayerLeave { fights.end(player) }
    }

    private suspend fun ProtectedAccess.quickEscape() {
        withInstanceLeaveTransition { defaultLeaveFlow() }
    }

    private suspend fun ProtectedAccess.enterInstance() {
        defaultInstanceEntry()
    }

    private companion object {
        private const val ENTER_MESSAGE = "You enter Yama's Domain."

        /**
         * The domain spans two map squares of the cache, (22, 157) and (23, 157): the reference's
         * 11x7 chunks from chunk (181, 1256) cross the boundary at x = 1472. The whole squares are
         * copied; the extra terrain is harmless.
         */
        private val ARENA_REGIONS = listOf(5789, 6045)

        private val INSTANCE =
            InstanceArea.copyRegions(
                regionIds = ARENA_REGIONS,
                npcSpawns = listOf(InstanceNpc(YamaIds.THRONE, YamaCommon.THRONE_TILE)),
            )
    }
}
