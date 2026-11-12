package org.rsmod.api.bosses.spec

import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** An inclusive box between two corner tiles; spawn-relative corners keep it instance-safe. */
data class Area(val sw: TargetExpr.Single, val ne: TargetExpr.Single)

/**
 * A set of tiles for [Effect.OnTiles], resolved when it runs. Only free tiles are kept: inside
 * the set's area, walkable, and not already holding a loc the encounter spawned.
 */
sealed interface TileSet {
    data class RandomFree(val area: Area, val count: IntRange) : TileSet

    /** The tile under each player inside [area]. */
    data class UnderPlayers(val area: Area) : TileSet

    /** Each of [tiles], or the nearest free tile within [searchRadius] of it. */
    data class Nearest(
        val tiles: List<TargetExpr.Single>,
        val area: Area,
        val searchRadius: Int,
    ) : TileSet

    /** Tiles computed in Kotlin; the ones that aren't free inside [area] are dropped. */
    data class Custom(
        val area: Area,
        val tiles: (npc: Npc, target: Player, random: GameRandom) -> List<CoordGrid>,
    ) : TileSet

    /** Tiles computed in Kotlin around [at] (e.g. `ImpactTile`); the ones not free inside [area] are dropped. */
    data class CustomAt(
        val area: Area,
        val at: TargetExpr.Single,
        val tiles: (npc: Npc, at: CoordGrid, random: GameRandom) -> List<CoordGrid>,
    ) : TileSet

    /** Every tile of [a] and [b], each once. */
    data class Plus(val a: TileSet, val b: TileSet) : TileSet

    /** Every tile of [a] that is not in [b]. */
    data class Minus(val a: TileSet, val b: TileSet) : TileSet

    /** A uniformly random subset of at most [count] tiles of [tiles], picked after [tiles] resolves. */
    data class Take(val tiles: TileSet, val count: Int) : TileSet

    data class Bound(val name: String) : TileSet

    /** Tiles holding a loc the encounter spawned with id [loc]; not filtered by freeness. */
    data class OwnedLocs(val loc: String) : TileSet

    /** Tiles carrying [tag] ([Effect.TagTile]); not filtered by freeness. */
    data class Tagged(val tag: String) : TileSet
}
