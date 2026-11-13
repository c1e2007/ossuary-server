package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import java.awt.Color
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.ui.setColour
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

internal class OcellusPhaseTwo(
    private val deps: BossDeps,
    private val hpBar: BossHpBarScript,
    private val meter: OcellusMeter,
    interactions: AiPlayerInteractions,
    energy: SpecialAttackEnergy,
    private val partySize: () -> Int,
) {
    private val censers = OcellusCensers(deps, energy)
    private val walls = OcellusWalls(deps)
    private val collapse = OcellusCollapse(deps, walls::clear)
    private val void = OcellusVoid(deps)
    private val rifts = OcellusRifts(deps)
    private val quadrants =
        OcellusQuadrants(deps, interactions, collapse.stoneLocs) { tiles ->
            void.clearOn(tiles)
            rifts.clearOn(tiles)
        }
    private val bombs = OcellusBombs(deps, quadrants::standingTurns)
    private val souls = OcellusSouls(deps, void::tiles)
    private val voidChain = OcellusVoidChain(deps)
    private val deflect = OcellusDeflect(deps)
    private val vine = OcellusVine(deps)
    private var swordHitbox: Npc? = null
    private var storedDamage = 0

    fun register() {
        walls.register()
        collapse.register()
        quadrants.register()
        void.register()
        bombs.register()
        rifts.register()
        souls.register()
        voidChain.register()
        deflect.register()
        vine.register()
        deps.extensionRegistry.register(ENTER) { enter(it.npc) }
        deps.extensionRegistry.register(FINISH) { finish(it.npc) }
        deps.extensionRegistry.register(PHASE_THREE) { phaseThree(it.npc) }
        deps.extensionRegistry.register(OPEN) { open(it.npc) }
        deps.extensionRegistry.register(DISCHARGE) { discharge(it.npc) }
        deps.extensionRegistry.register(CLOSE) { removeHitbox() }
        deps.extensionRegistry.register(SET_LEVEL) { it.npc.setCombatLevel(PHASE_TWO_LEVEL) }
    }

    fun isOutsideQuadrant(npc: Npc, tile: CoordGrid): Boolean = quadrants.isOutside(npc, tile)

    fun bombTiles(npc: Npc): List<CoordGrid> = bombs.tiles(npc)

    fun modifyHit(event: NpcHitEvents.Modify) {
        censers.modify(event)
        voidChain.halve(event)
    }

    fun placeCensers(middle: CoordGrid) = censers.place(middle)

    fun resetCharges(players: List<Player>) = censers.resetCharges(players)

    fun lightCenser(boss: Npc, at: BoundLocInfo): String? = censers.light(boss, at)

    fun absorb(damage: Int) {
        storedDamage += damage
    }

    fun clear() {
        stopHazards()
        walls.clear()
        collapse.clear()
        quadrants.clear()
        censers.clear()
        removeHitbox()
        storedDamage = 0
    }

    fun stopHazards() {
        void.clear()
        bombs.clear()
        rifts.clear()
        voidChain.clear()
        deflect.clear()
        vine.clear()
    }

    fun playVictorySong(npc: Npc) = playSong(npc, VICTORY_SONG)

    private fun playSong(npc: Npc, song: String) {
        for (fighter in OcellusArena.players(npc, deps.playerList)) {
            fighter.midiSong(song, fadeOutSpeed = SONG_FADE_SPEED, fadeInDelay = SONG_FADE_SPEED)
        }
    }

    private fun finish(npc: Npc) {
        npc.hideAllOps()
        playSong(npc, PHASE_THREE_SONG)
    }

    private fun phaseThree(npc: Npc) {
        val type = ServerCacheManager.getNpc("npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC))!!
        npc.moveRestrict = type.moveRestrict
        npc.apRequiresLineOfSight = false
        npc.setCombatLevel(PHASE_TWO_LEVEL)
        npc.hideAllOps()
    }

    private fun enter(npc: Npc) {
        playSong(npc, PHASE_TWO_SONG)
        meter.hide(npc)
        val type = ServerCacheManager.getNpc("npc.tfa_ocellus_p2".asRSCM(RSCMType.NPC))!!
        npc.copyStats(type)
        npc.baseHitpointsLvl = POOL_HP_BASE + POOL_HP_PER_PLAYER * partySize()
        npc.hitpoints = npc.baseHitpointsLvl
        npc.moveRestrict = type.moveRestrict
        npc.apRequiresLineOfSight = false
        npc.setCombatLevel(PHASE_TWO_TRANSFORM_LEVEL)
        for (fighter in OcellusArena.players(npc, deps.playerList)) {
            hpBar.onUpdate(fighter, npc)
            hpBar.onRefresh(fighter, npc) {
                setColour("component.hpbar_hud:health_bar_back", BAR_BACK)
                setColour("component.hpbar_hud:health_bar_sliding", BAR_SLIDING)
                setColour("component.hpbar_hud:health_bar_remaining", BAR_REMAINING)
            }
        }
    }

    private fun open(npc: Npc) {
        npc.showHitmark(hitmark(HITMARK_SHIELD_MAX, npc.baseHitpointsLvl))
        showPoolBar(npc)
        removeHitbox()
        val hitbox = Npc("npc.tfa_ocellus_p2_sword_hitbox", npc.coords)
        hitbox.moveRestrict = MoveRestrict.NoMove
        hitbox.baseHitpointsLvl = HITBOX_HP
        hitbox.hitpoints = HITBOX_HP
        deps.npcRepo.add(hitbox, Int.MAX_VALUE)
        swordHitbox = hitbox
        storedDamage = 0
    }

    private fun discharge(npc: Npc) {
        val damage = (storedDamage * DISCHARGE_SHARE).toInt().coerceAtMost(npc.hitpoints - 1)
        storedDamage = 0
        npc.hitpoints -= damage
        npc.showHitmark(hitmark(HITMARK_REGULAR_TINT, damage))
        showPoolBar(npc)
        for (fighter in OcellusArena.players(npc, deps.playerList)) hpBar.onUpdate(fighter, npc)
    }

    private fun showPoolBar(npc: Npc) {
        val fill = (npc.hitpoints.toLong() * BAR_SEGMENTS / npc.baseHitpointsLvl).toInt()
        npc.showHeadbar(Headbar.fromNoSource(HEADBAR_HEALTH_100, HEADBAR_HEALTH_100, fill, fill, 0, 0))
    }

    private fun removeHitbox() {
        swordHitbox?.let { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        swordHitbox = null
    }

    private fun hitmark(id: Int, damage: Int): Hitmark =
        Hitmark.fromNoSource(self = id, source = id, public = id, damage = damage, delay = 0)

    companion object {
        const val ENTER = "ocellus.p2_enter"
        const val OPEN = "ocellus.p2_open"
        const val DISCHARGE = "ocellus.p2_discharge"
        const val CLOSE = "ocellus.p2_close"
        const val SET_LEVEL = "ocellus.p2_set_level"

        const val WALLS_SPAWN = OcellusWalls.SPAWN
        const val WALLS_TELEGRAPH = OcellusWalls.TELEGRAPH
        const val WALLS_CLOSED_TELEGRAPH = OcellusWalls.CLOSED_TELEGRAPH
        const val WALLS_FIRE = OcellusWalls.FIRE
        const val POOLS = OcellusWalls.POOLS
        const val POOLS_DETONATE = OcellusWalls.POOLS_DETONATE
        const val PORTALS_SPAWN = OcellusWalls.PORTALS_SPAWN
        const val PORTALS_TICK = OcellusWalls.PORTALS_TICK
        const val PORTALS_END = OcellusWalls.PORTALS_END
        const val WALLS_CLEAR = OcellusWalls.CLEAR
        const val WALLS_DESPAWN = OcellusWalls.DESPAWN

        const val QUADRANT_ENTER = OcellusQuadrants.ENTER
        const val QUADRANT_FREEZE = OcellusQuadrants.FREEZE
        const val QUADRANT_THAW = OcellusQuadrants.THAW
        const val UNTETHER_GLIDE = OcellusQuadrants.UNTETHER_GLIDE
        const val BOMBS_MARK = OcellusBombs.MARK
        const val BOMBS_IDLE = OcellusBombs.IDLE
        const val RIFTS_RESET = OcellusRifts.RESET
        const val RIFTS_OPEN = OcellusRifts.OPEN
        const val RIFTS_STOP = OcellusRifts.STOP
        const val VINES_CLEAR = OcellusVine.CLEAR
        const val SOULS_TAKE = OcellusSouls.TAKE
        const val SOULS_RELEASE = OcellusSouls.RELEASE
        const val VOID_CHAIN = OcellusVoidChain.START
        const val DEFLECT_START = OcellusDeflect.START
        const val DEFLECT_END = OcellusDeflect.END
        const val QUADRANT_BARRIER_SPAWN = OcellusQuadrants.BARRIER_SPAWN
        const val QUADRANT_BARRIER = OcellusQuadrants.BARRIER
        const val QUADRANT_BARRIER_DESPAWN = OcellusQuadrants.BARRIER_DESPAWN
        const val QUADRANT_BARRIER_REMOVE = OcellusQuadrants.BARRIER_REMOVE
        const val QUADRANT_COLLAPSE = OcellusQuadrants.COLLAPSE
        const val QUADRANT_STONES_FALL = OcellusQuadrants.STONES_FALL
        const val QUADRANT_STONES_BLOCK = OcellusQuadrants.STONES_BLOCK

        const val FINISH = "ocellus.p2_finish"
        const val PHASE_THREE = "ocellus.p3_enter"
        const val SWORD_RAISE = OcellusCollapse.SWORD_RAISE
        const val SWORD_IDLE = OcellusCollapse.SWORD_IDLE
        const val SWORD_DROP = OcellusCollapse.SWORD_DROP
        const val ARENA_CRACK = OcellusCollapse.CRACK
        const val ARENA_COLLAPSE = OcellusCollapse.COLLAPSE
        const val GAP_WALLS = OcellusCollapse.GAP_WALLS

        private const val PHASE_TWO_SONG = "midi.tfa_ocellus_combat_part_01"
        private const val PHASE_THREE_SONG = "midi.tfa_ocellus_combat_part_02"
        private const val VICTORY_SONG = "midi.silence"
        private const val SONG_FADE_SPEED = 60

        private const val PHASE_TWO_TRANSFORM_LEVEL = 1424
        private const val PHASE_TWO_LEVEL = 1601
        private const val POOL_HP_BASE = 1020
        private const val POOL_HP_PER_PLAYER = 2200
        private val HITMARK_SHIELD_MAX = "hitmark.shield_damage_max".asRSCM(RSCMType.HITMARK)
        private val HITMARK_REGULAR_TINT = "hitmark.regular_damage_tint".asRSCM(RSCMType.HITMARK)
        const val HITBOX_HEADBAR = 0
        private val BAR_BACK = Color(200, 0, 0)
        private val BAR_SLIDING = Color(0, 144, 0)
        private val BAR_REMAINING = Color(0, 200, 0)
        private val HEADBAR_HEALTH_100 = "headbar.health_100".asRSCM(RSCMType.HEADBAR)
        private const val BAR_SEGMENTS = 100
        private const val HITBOX_HP = 30000
        private const val DISCHARGE_SHARE = 0.5
    }
}
