package org.rsmod.content.minigames.colosseum

import kotlin.random.Random
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal enum class RunStage {
    INTERMISSION,
    STARTING,
    WAVE,
    SOL,
    WON,
    FINISHED,
    ENDED,
}

internal class ColosseumRun(
    val player: Player,
    val session: InstanceSession,
    private val southWest: CoordGrid,
    val random: Random = Random.Default,
) {
    var stage: RunStage = RunStage.INTERMISSION
    var wave: Int = 0
    var offered: List<ColosseumModifier> = emptyList()
    val modifiers: ModifierSet = ModifierSet()
    val npcs: MutableSet<Npc> = linkedSetOf()
    var minimus: Npc? = null
    var sol: Npc? = null
    var waveTicks: Int = 0
    var totalTicks: Int = 0
    var reinforced: Boolean = false
    var hitByEnemy: Boolean = false
    var waveDamage: Int = 0
    var glory: Int = 0
    var ticking: Boolean = false
    var startingIn: Int = 0
    var fight: SolFight? = null
    var loot: RunLoot? = null
    var chest: LocInfo? = null
    var doomStacks: Int = 0
    val sand: MutableMap<ArenaTile, LocInfo> = linkedMapOf()
    val barricade: MutableList<LocInfo> = mutableListOf()
    val hazards: MutableSet<Npc> = linkedSetOf()
    val cleanup: MutableList<() -> Unit> = mutableListOf()

    fun tile(x: Int, z: Int): CoordGrid =
        CoordGrid(
            southWest.x + x - Colosseum.ARENA_BASE_X,
            southWest.z + z - Colosseum.ARENA_BASE_Z,
            southWest.level,
        )

    fun tile(world: CoordGrid): CoordGrid = tile(world.x, world.z)

    fun playerTile(): ArenaTile =
        ArenaTile(
            player.coords.x - southWest.x + Colosseum.ARENA_BASE_X,
            player.coords.z - southWest.z + Colosseum.ARENA_BASE_Z,
        )

    fun secondsString(ticks: Int): String {
        val seconds = ticks * 0.6
        val minutes = (seconds / 60).toInt()
        val rest = seconds % 60
        return "%d:%05.2f".format(minutes, rest)
    }
}
