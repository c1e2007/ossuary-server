package org.rsmod.content.bosses.yama

import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.disableOverheadPrayers
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spotanimMapPrivate
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * The damage helpers (Hits.Yama.js): styled hits through prayer, typeless damage, burn, poison,
 * roots, prayer drain and the map animations the attacks show. Every hit goes through
 * [Player.queueHit], so the standard hitmark path applies.
 */
internal object YamaHits {
    /** The npc a hit is credited to: Yama, else the Judge, else the throne. */
    private fun source(fight: YamaFight): Npc? =
        fight.yama?.takeIf { it.isSlotAssigned } ?: fight.judge?.npc ?: fight.throne

    /**
     * A styled hit from [from], landing [delay] ticks on. It rolls like any npc attack; the
     * matching protection prayer lets through at most [through] (0 blocks it).
     */
    fun strike(
        fight: YamaFight,
        from: Npc,
        type: HitType,
        maxHit: Int,
        delay: Int,
        through: Int,
    ): Int {
        val player = fight.player
        val deps = fight.deps
        val landed =
            when (type) {
                HitType.Melee -> deps.accuracy.rollMeleeAccuracy(from, player, null, deps.random)
                HitType.Ranged -> deps.accuracy.rollRangedAccuracy(from, player, deps.random)
                else -> deps.accuracy.rollMagicAccuracy(from, player, deps.random)
            }
        var damage = if (landed) deps.random.of(maxHit + 1) else 0
        val prayed = protectedFrom(player, type)
        if (prayed) damage = minOf(damage, deps.random.of(through + 1))
        if (damage > 0) fight.hurt = true
        // The prayer clamp above replaces the standard modifier's full block, so a prayed hit
        // bypasses it (god mode is still honoured by the no-op modifier).
        val modifier = if (prayed) NoopPlayerHitModifier else deps.playerHitModifier
        player.queueHit(from, delay, type, damage, modifier)
        return damage
    }

    fun protectedFrom(player: Player, type: HitType): Boolean =
        when (type) {
            HitType.Melee -> player.vars[YamaIds.PROTECT_MELEE] != 0
            HitType.Ranged -> player.vars[YamaIds.PROTECT_MISSILES] != 0
            HitType.Magic -> player.vars[YamaIds.PROTECT_MAGIC] != 0
            else -> false
        }

    /** Damage no prayer or armour stops (the arena's hazards, the flares, glyphs of fire). */
    fun damage(fight: YamaFight, amount: Int) {
        val from = source(fight) ?: return
        val value = maxOf(0, amount)
        if (value > 0) fight.hurt = true
        fight.player.queueHit(from, 1, HitType.Typeless, value, fight.deps.playerHitModifier)
    }

    /** Set alight: 1 damage every 4 ticks, [total] times (varbit player_own_burn_damage_total). */
    fun burn(fight: YamaFight, total: Int) {
        val player = fight.player
        player.mes(SET_ALIGHT)
        fight.burnLeft = maxOf(fight.burnLeft, total)
        player.yamaBurnTotal = fight.burnLeft
        player.yamaBurnSplat = 1
        if (fight.burning) return
        fight.burning = true
        fun step() {
            if (fight.stopped || fight.burnLeft <= 0) {
                fight.burning = false
                player.yamaBurnSplat = 0
                return
            }
            damage(fight, 1)
            fight.burnLeft--
            player.yamaBurnTotal = fight.burnLeft
            fight.at(BURN_INTERVAL_TICKS) { step() }
        }
        fight.at(BURN_INTERVAL_TICKS) { step() }
    }

    /**
     * Yama's poison, from a first hit (Wiki: starting at 20). The engine has no per-hit poison
     * severity in the shared combat API, so it applies the standard poison for [hit] damage.
     */
    fun poison(fight: YamaFight, hit: Int) {
        CombatEffects.poison(fight.player, hit)
    }

    /** Rooted in place for [ticks]. */
    fun root(fight: YamaFight, ticks: Int) {
        CombatEffects.freeze(fight.player, ticks)
    }

    fun drainPrayer(player: Player, points: Int) {
        CombatEffects.statDrain(player, listOf("stat.prayer"), maxOf(0, points))
    }

    fun dropProtectionPrayers(player: Player) {
        player.disableOverheadPrayers()
    }

    /** A map animation the challenger sees on a tile. */
    fun tileGraphic(player: Player, tile: CoordGrid, spot: String, delay: Int = 0, height: Int = 0) {
        player.spotanimMapPrivate(tile, spot, delay, height)
    }

    // ------------------------------------------------------------------ incoming hits

    /**
     * Phase 1 and 2: the magic defence the reference gives him against a mage (+60) and a melee
     * user (-30) is a damage factor on magic hits here, and phase 3's raised bonuses a factor on
     * every hit. See YAMA-DEVIATIONS.md.
     */
    fun scaleIncoming(fights: YamaFights, event: NpcHitEvents.Modify) {
        val fight = fights.fightOfNpc(event.npc) ?: return
        val hit = event.hit
        if (!hit.isFromPlayer) return
        var factor = 1.0
        if (fight.phase == 3) {
            factor *= PHASE_THREE_FACTOR
        } else if (fight.stance == YamaFight.Stance.MAGIC && hit.type == HitType.Magic) {
            factor *= STANDING_MAGIC_FACTOR
        }
        hit.damage = (hit.damage * factor).toInt()
    }

    /** The challenger's style on Yama sets his stance (Wiki). */
    fun recordStance(fights: YamaFights, event: NpcHitEvents.Impact) {
        val fight = fights.fightOfNpc(event.npc) ?: return
        if (fight.phase == 3 || !event.hit.isFromPlayer) return
        val next =
            if (event.hit.type == HitType.Melee) YamaFight.Stance.MELEE else YamaFight.Stance.MAGIC
        if (next != fight.stance) fight.changeStance(next)
    }

    private const val SET_ALIGHT = "<col=ff8e32>You have been set alight!"
    private const val BURN_INTERVAL_TICKS = 4

    /** (64 - 30) / (64 + 60): the reference's -30 and +60 magic defence as a hit-chance ratio. */
    private const val STANDING_MAGIC_FACTOR = 0.27

    /** The reference's phase 3 bonuses are about 35 percent higher than phase 1 and 2. */
    private const val PHASE_THREE_FACTOR = 0.74

    @Suppress("unused") private fun hitpointsOf(player: Player): Int = player.hitpoints
}
