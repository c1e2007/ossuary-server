package org.rsmod.game.entity.npc

/** What an Npc's engine-owned routing does once its ap-range interaction (e.g. combat) triggers. */
public enum class NpcApproach {
    /** Stops where the ap script fired. The vanilla behaviour. */
    StopInRange,

    /**
     * Keeps routing toward the target after the ap script triggers, and is not dropped for having
     * moved, so it can attack from range (e.g. a spell) while closing to melee. The script picks
     * the attack, e.g. by whether the target is in melee reach. Unlike `scriptedMovement`, the
     * engine still owns routing, facing and stepping out from under the target.
     */
    ChaseToMelee,
}
