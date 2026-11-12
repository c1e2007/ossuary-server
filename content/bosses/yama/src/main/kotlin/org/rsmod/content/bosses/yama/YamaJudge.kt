package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onApLoc2
import org.rsmod.api.script.onApLoc3
import org.rsmod.api.script.onApLoc4
import org.rsmod.api.script.onApLoc5
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Translation
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The phase change and the Judge of Yama (Judge.Yama.js):
 * - At his next attack after 66.6% (then 33.3%) Yama says one of his lines and slams the ground.
 *   The flares and hazards go, the glyphs go out, the player falls and fades out, and six ticks
 *   in lands face down by the stepping stones as the Judge appears at the corridor's far end.
 * - While the player crosses the stones the Judge casts every 4 ticks, sending two fire surges
 *   down the corridor a tile a tick; on the platform it throws a fireball at the player's tile
 *   every 5 ticks, and the cavern shakes on its 5th and 13th.
 * - It prays against two styles: Missiles always, and Melee until hit by magic, then Magic until
 *   hit by melee.
 * - Its death sends the player back to the arena, in front of the throne, five ticks later.
 *
 * The Judge and every surge are pinned, never respawn, and are deleted when the phase ends.
 */
internal object YamaJudge {
    class Surge(val npc: Npc, val x: Int, var y: Int) {
        var fresh = true
        var hit = false
        var doused = false
    }

    class State {
        var npc: Npc? = null
        var tick = 0
        var attacks = 0
        var platformTicks = -1
        var onPlatform = false
        var jumpQueued = false
        var prayers = "meleeRanged"
        val surges = ArrayList<Surge>()
    }

    private val LANDING = CoordGrid(1479, 10077, 0)
    private val JUDGE_TILE = CoordGrid(1478, 10098, 0)
    private val RETURN_TO = CoordGrid(1504, 10086, 0)
    private val YAMA_RETURN = CoordGrid(1501, 10076, 0)

    private const val HEADICON_GRAPHIC = 440
    private const val ICON_MELEE_RANGED = 7
    private const val ICON_RANGED_MAGIC = 6

    private const val FALL_TICKS = 2
    private const val FADE_TICKS = 4
    private const val LAND_TICKS = 6
    private const val RETURN_TICKS = 5
    private const val SURGE_EVERY = 4
    private val SURGE_COLUMNS = listOf(1477, 1478, 1479, 1480, 1481)
    private const val SURGE_START_Z = 10097
    private const val SURGE_END_Z = 10070
    private const val SURGE_MAX_HIT = 30
    private const val SURGE_HITPOINTS = 100
    private const val PLATFORM_Z = 10096
    private const val FIREBALL_EVERY = 5
    private const val FIREBALL_HIT_TICKS = 4
    private val SHAKE_ATTACKS = listOf(5, 13)
    private const val JUMP_CYCLES = 27
    private const val JUMP_AP_RANGE = 4
    private const val JUMP_FACING = 0

    // ------------------------------------------------------------------ the phase change

    fun begin(fight: YamaFight) {
        val yama = fight.yama ?: return
        fight.state = YamaState.TRANSITION
        yama.say(YamaAttacks.TRANSITION_LINES[fight.deps.random.of(YamaAttacks.TRANSITION_LINES.size)])
        yama.anim(YamaIds.SEQ_TRANSITION)
        yama.spotanim(YamaIds.SPOT_TRANSITION)
        YamaFlares.clear(fight)
        YamaHazards.clear(fight)
        fight.judge = State()
        fight.at(FALL_TICKS) {
            YamaGlyphs.putAllOut(fight)
            fall(fight, LAND_TICKS - FALL_TICKS) {
                fight.player.yamaTransitionPhase = 1
                land(fight, LANDING, facing = CoordGrid(LANDING.x, LANDING.z + 5, 0))
                spawnJudge(fight)
            }
        }
    }

    /** The fall out of the arena, and the landing [landTicks] later somewhere else (as captured). */
    private fun fall(fight: YamaFight, landTicks: Int, land: () -> Unit) {
        val player = fight.player
        player.anim(YamaIds.SEQ_PLAYER_FALL, delay = 5)
        fight.at(FADE_TICKS - FALL_TICKS) {
            player.yamaMinimap = 2
            fadeOut(fight)
        }
        fight.at(landTicks) {
            player.yamaMinimap = 0
            land()
            player.anim(YamaIds.SEQ_PLAYER_LAND)
            player.spotanimSpecific(YamaIds.SPOT_LAND, height = 638, slot = 5)
            fadeIn(fight)
        }
    }

    /** The fade overlay as the engine's cinematic helper draws it (clientDuration in client cycles). */
    private fun fadeOut(fight: YamaFight) {
        org.rsmod.api.player.cinematic.Cinematic.fadeOverlay(fight.player, 0, 0, 0, 255, 50, fight.env.eventBus)
    }

    private fun fadeIn(fight: YamaFight) {
        org.rsmod.api.player.cinematic.Cinematic.fadeOverlay(fight.player, 0, 255, 0, 0, 50, fight.env.eventBus)
        fight.at(2) { org.rsmod.api.player.cinematic.Cinematic.closeFadeOverlay(fight.player, fight.env.eventBus) }
    }

    private fun land(fight: YamaFight, at: CoordGrid, facing: CoordGrid) {
        PathingEntityCommon.telejump(fight.player, fight.deps.collision, fight.arena.tile(at))
        fight.player.faceSquare(fight.arena.tile(facing))
    }

    private fun spawnJudge(fight: YamaFight) {
        val state = fight.judge ?: return
        val npc = fight.spawn(YamaIds.JUDGE, fight.arena.tile(JUDGE_TILE)) ?: return
        npc.setHeadIcon(0, HEADICON_GRAPHIC, ICON_MELEE_RANGED)
        state.npc = npc
        state.prayers = "meleeRanged"
        fight.state = YamaState.JUDGE
    }

    // ------------------------------------------------------------------ the Judge's turn

    fun process(fight: YamaFight) {
        val state = fight.judge ?: return
        if (state.npc == null || fight.state != YamaState.JUDGE) return
        state.tick++
        state.onPlatform = fight.playerSource.z >= PLATFORM_Z
        moveSurges(fight, state)
        if (!state.onPlatform) {
            if ((state.tick - 1) % SURGE_EVERY == 0) sendSurges(fight, state)
        } else {
            state.platformTicks++
            if (state.platformTicks % FIREBALL_EVERY == 0) fireball(fight, state)
        }
    }

    private fun sendSurges(fight: YamaFight, state: State) {
        val judge = state.npc ?: return
        judge.anim(YamaIds.SEQ_JUDGE_CAST)
        for (x in SURGE_COLUMNS.shuffled().take(2)) {
            val npc = fight.spawn(YamaIds.FIRE_SURGE, fight.arena.tile(x, SURGE_START_Z)) ?: continue
            npc.hitpoints = SURGE_HITPOINTS
            state.surges += Surge(npc, x, SURGE_START_Z)
        }
    }

    private fun moveSurges(fight: YamaFight, state: State) {
        val here = fight.playerSource
        val keep = ArrayList<Surge>()
        for (surge in state.surges) {
            if (!surge.npc.isSlotAssigned) continue
            if (surge.fresh) {
                surge.fresh = false
                keep += surge
                continue
            }
            surge.y--
            if (surge.y < SURGE_END_Z) {
                fight.remove(surge.npc)
                continue
            }
            glide(fight, surge.npc, fight.arena.tile(surge.x, surge.y))
            if (surge.x == here.x && surge.y == here.z && !surge.hit) {
                surge.hit = true
                YamaHits.damage(fight, fight.deps.random.of(SURGE_MAX_HIT + 1))
                fight.player.spotanimSpecific(YamaIds.SPOT_SURGE_IMPACT, height = 120, slot = 2)
                fight.player.yamaTransitionDamage = 1
            }
            keep += surge
        }
        state.surges.clear()
        state.surges.addAll(keep)
    }

    /** A tile a tick: the reference's `exactMove`, as the engine's glide of an npc to a tile. */
    private fun glide(fight: YamaFight, npc: Npc, to: CoordGrid) {
        val lead = Translation.between(npc.coords, to)
        PathingEntityCommon.teleport(npc, fight.deps.collision, to)
        npc.pendingExactMove =
            EntityExactMove(
                deltaX1 = lead.x,
                deltaZ1 = lead.z,
                deltaX2 = 0,
                deltaZ2 = 0,
                clientDelay1 = 0,
                clientDelay2 = YamaCommon.CLIENT_CYCLES_PER_TICK,
                direction = 4,
            )
    }

    private fun fireball(fight: YamaFight, state: State) {
        val judge = state.npc ?: return
        val player = fight.player
        state.attacks++
        val target = player.coords
        val source = fight.arena.source(target)
        judge.anim(YamaIds.SEQ_JUDGE_CAST)
        YamaHits.tileGraphic(player, target, YamaIds.SPOT_FIREBALL_WARN, delay = 30)
        fight.deps.bossProjectile(
            spotanim = YamaIds.SPOT_FIREBALL_PROJ.asRSCM(RSCMType.SPOTANIM),
            src = judge.coords,
            target = target,
            startHeight = 345,
            endHeight = 0,
            delay = 0,
            travel = 120,
            curve = 30,
            progress = 128,
            homing = null,
        )
        YamaHits.tileGraphic(player, target, YamaIds.SPOT_FIREBALL_IMPACT, delay = 120, height = 30)
        if (state.attacks in SHAKE_ATTACKS) {
            fight.at(1) { YamaHits.damage(fight, 5 + fight.deps.random.of(3)) }
        }
        fight.at(FIREBALL_HIT_TICKS) {
            if (!judge.isSlotAssigned) return@at
            val here = fight.playerSource
            if (here.x == source.x && here.z == source.z) {
                YamaHits.damage(fight, 18 + fight.deps.random.of(5))
            }
            val at = fight.arena.source(judge.coords)
            if (source.x in at.x until at.x + 3 && source.z in at.z until at.z + 3) {
                judge.hitpoints = maxOf(1, judge.hitpoints - (13 + fight.deps.random.of(6)))
            }
        }
    }

    // ------------------------------------------------------------------ its prayers

    /**
     * Hits on the Judge land as the engine rolls them, unless it prays against them. (The
     * reference forces every other hit to its maximum; see YAMA-DEVIATIONS.md.)
     */
    private fun judgeHit(fights: YamaFights, event: NpcHitEvents.Modify) {
        val fight = fights.fightOfJudge(event.npc) ?: return
        val state = fight.judge ?: return
        val hit = event.hit
        if (!hit.isFromPlayer) return
        val type = hit.type
        val prayers = state.prayers
        val blocked =
            type == HitType.Ranged ||
                (type == HitType.Melee && prayers == "meleeRanged") ||
                (type == HitType.Magic && prayers == "rangedMagic")
        if (blocked) {
            hit.damage = 0
        } else if (hit.damage <= 0) {
            hit.damage = 1
        }
        val next =
            when (type) {
                HitType.Magic -> "rangedMagic"
                HitType.Melee -> "meleeRanged"
                else -> prayers
            }
        if (next != prayers) {
            state.prayers = next
            event.npc.setHeadIcon(
                0,
                HEADICON_GRAPHIC,
                if (next == "meleeRanged") ICON_MELEE_RANGED else ICON_RANGED_MAGIC,
            )
        }
    }

    // ------------------------------------------------------------------ the stones

    /**
     * A stepping stone: a jump to it from the row one to three tiles before it (captured). It is
     * an approach-range trigger, so the jump starts from where the player stands, as in the
     * reference, with no walking up to the stone first.
     */
    private suspend fun ProtectedAccess.jump(fights: YamaFights, stone: BoundLocInfo) {
        if (!isWithinApRange(stone, JUMP_AP_RANGE)) return
        val fight = fights.fightOf(player) ?: return
        val state = fight.judge ?: return
        if (fight.state != YamaState.JUDGE || state.jumpQueued) return
        val here = fight.playerSource
        val to = fight.arena.source(stone.coords)
        if (to.z - here.z !in 1..3 || abs(to.x - here.x) > 1) {
            mes("I can't reach that!")
            return
        }
        state.jumpQueued = true
        delay(1)
        anim(YamaIds.SEQ_JUMP)
        exactMove(coords, stone.coords, delay1 = 0, delay2 = JUMP_CYCLES, dir = JUMP_FACING)
        delay(1)
        state.jumpQueued = false
    }

    private fun abs(v: Int) = if (v < 0) -v else v

    // ------------------------------------------------------------------ its death, and back

    /** From its death starting (as captured): five ticks to the fall, then the landing before the throne. */
    private fun onJudgeDeath(fight: YamaFight) {
        val state = fight.judge ?: return
        if (fight.stopped || fight.state != YamaState.JUDGE) return
        val player = fight.player
        for (surge in state.surges) fight.remove(surge.npc)
        state.surges.clear()
        state.npc = null
        fight.state = YamaState.RETURNING
        player.spotanimSpecific("spotanim.vfx_yama_floor_portal_spotanim01", delay = 90, slot = 2)
        fight.at(RETURN_TICKS) {
            player.yamaTransitionDamage = 0
            player.yamaTransitionPhase = 0
            fight.yama?.let {
                PathingEntityCommon.teleport(it, fight.deps.collision, fight.arena.tile(YAMA_RETURN))
            }
        }
        fight.at(RETURN_TICKS + 1) {
            fall(fight, 3) {
                land(fight, RETURN_TO, facing = CoordGrid(RETURN_TO.x, RETURN_TO.z - 5, 0))
                fight.nextPhase()
            }
        }
    }

    /** The judge and its surges go when the phase ends or the fight does. */
    fun clear(fight: YamaFight) {
        val state = fight.judge ?: return
        for (surge in state.surges) fight.remove(surge.npc)
        state.surges.clear()
        fight.remove(state.npc)
        state.npc = null
        fight.judge = null
    }

    fun registerHooks(ctx: ScriptContext, fights: YamaFights, npcDeath: NpcDeath) {
        val judge = ServerCacheManager.getNpc(YamaIds.JUDGE.asRSCM(RSCMType.NPC)) ?: return
        val surge = ServerCacheManager.getNpc(YamaIds.FIRE_SURGE.asRSCM(RSCMType.NPC)) ?: return
        val stone = YamaIds.STEPPING_STONE
        with(ctx) {
            onModifyNpcHit(judge) { judgeHit(fights, this) }
            onModifyNpcHit(surge) { surgeHit(fights, this) }
            onNpcQueue(judge, "queue.death") {
                fights.fightOfJudge(npc)?.let { onJudgeDeath(it) }
                npcDeath.deathNoDrops(this)
            }
            // The stone's action slot is not in the repo's dumps, so every approach op jumps.
            onApLoc1(stone) { jump(fights, it.loc) }
            onApLoc2(stone) { jump(fights, it.loc) }
            onApLoc3(stone) { jump(fights, it.loc) }
            onApLoc4(stone) { jump(fights, it.loc) }
            onApLoc5(stone) { jump(fights, it.loc) }
        }
    }

    /**
     * A fire spell can only be put out by a spell: any magic hit lands and puts it out, anything
     * else is refused. (The reference asks for a water spell specifically.)
     */
    private fun surgeHit(fights: YamaFights, event: NpcHitEvents.Modify) {
        val hit = event.hit
        if (!hit.isFromPlayer) return
        if (hit.type != HitType.Magic) {
            hit.damage = 0
            return
        }
        if (hit.damage <= 0) hit.damage = 1
        val fight = fights.fightOfSurge(event.npc) ?: return
        val surge = fight.judge?.surges?.firstOrNull { it.npc === event.npc } ?: return
        if (surge.doused) return
        surge.doused = true
        fight.at(1) { fight.remove(event.npc) }
    }
}
