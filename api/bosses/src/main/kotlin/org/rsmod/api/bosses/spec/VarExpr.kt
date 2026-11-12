package org.rsmod.api.bosses.spec

/** An int computed from the boss npc's varns when the owning effect runs. */
sealed interface VarExpr {
    data class Const(val value: Int) : VarExpr

    /** The current game tick, e.g. `Now + 13` to store a deadline for [Condition.VarnExpired]. */
    data object Now : VarExpr

    /** The caster's current hitpoints. */
    data object Hp : VarExpr

    /** The caster's base hitpoints (its max). */
    data object BaseHp : VarExpr

    /** The damage a hit landed on its player, after prayer; only valid inside [Effect.Hit.onHit]. */
    data object HitDamage : VarExpr

    data class Minus(val a: VarExpr, val b: VarExpr) : VarExpr

    data class Varn(val varn: String) : VarExpr
    data class Plus(val a: VarExpr, val b: VarExpr) : VarExpr
    data class Min(val a: VarExpr, val b: VarExpr) : VarExpr
    data class Max(val a: VarExpr, val b: VarExpr) : VarExpr

    /** Manhattan distance in tiles between [a] and [b]. */
    data class Manhattan(val a: TargetExpr.Single, val b: TargetExpr.Single) : VarExpr

    /** Chebyshev distance in tiles from [tile] to the caster's footprint; 0 inside it. */
    data class DistanceToFootprint(val tile: TargetExpr.Single) : VarExpr

    /** Jagex bearing (0..2047) from [from] to [to]. */
    data class BearingTo(val to: TargetExpr.Single, val from: TargetExpr.Single = TargetExpr.Centre) : VarExpr
}
