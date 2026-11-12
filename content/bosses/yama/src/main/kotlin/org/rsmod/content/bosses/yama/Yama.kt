package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcApproach
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/** What every fight needs, injected once. */
@Singleton
internal class YamaEnv
@Inject
constructor(val deps: BossDeps, val manager: InstanceManager, val eventBus: org.rsmod.events.EventBus)

internal enum class YamaState {
    /** On the throne, waiting to be challenged. */
    SEATED,
    SUMMONING,
    FIGHTING,
    TRANSITION,
    JUDGE,
    RETURNING,
    OVER,
}

/** One player's domain: the seated Yama, and the fight once it is challenged. */
@Singleton
internal class YamaFights @Inject constructor(private val env: YamaEnv) {
    private val fights = HashMap<Player, YamaFight>()

    fun fightOf(player: Player): YamaFight? = fights[player]

    fun fightOfNpc(npc: Npc): YamaFight? = fights.values.firstOrNull { it.yama === npc }

    fun fightOfJudge(npc: Npc): YamaFight? = fights.values.firstOrNull { it.judge?.npc === npc }

    fun fightOfSurge(npc: Npc): YamaFight? =
        fights.values.firstOrNull { f -> f.judge?.surges?.any { it.npc === npc } == true }

    /** The player's domain while Yama is still on his throne. */
    fun pending(player: Player): YamaFight? =
        fights[player]?.takeIf { it.state == YamaState.SEATED && !it.stopped }

    fun seat(player: Player, session: InstanceSession, enter: CoordGrid) {
        fights.remove(player)?.stop()
        val origin = enter.translate(
            YamaCommon.SOURCE_ORIGIN_X - YamaCommon.ARRIVAL.x,
            YamaCommon.SOURCE_ORIGIN_Z - YamaCommon.ARRIVAL.z,
        )
        val throne = env.manager.npcsForInstance(session.id).firstOrNull()
        val fight = YamaFight(env, player, YamaArena(origin), session, throne)
        fights[player] = fight
        throne?.let { fight.pin(it) }
        player.yamaThroneState = 0
        player.yamaMultiway = 1
        player.midiSong(YamaCommon.MUSIC_TRACK)
        fight.runLoop()
    }

    fun end(player: Player) {
        val fight = fights.remove(player) ?: return
        if (fight.state != YamaState.OVER && fight.state != YamaState.SEATED) {
            player.lastYamaResult = YamaResult.ESCAPED
        }
        fight.stop()
        fight.resetThrone()
        player.yamaMultiway = 0
        player.yamaTransitionPhase = 0
        player.yamaTransitionDamage = 0
    }

    fun engage(player: Player) {
        val fight = fights[player] ?: return
        if (fight.state != YamaState.SEATED) return
        player.faceTileOf(fight.throne)
        fight.start()
    }

    /** "Yes. Let's do this." from the chat starts it a tick later (as the reference does). */
    fun engageFromDialogue(player: Player) {
        val fight = fights[player] ?: return
        if (!player.contractSigned) return
        env.deps.worldQueues.add(1) { engage(player) }
    }

    private fun Player.faceTileOf(npc: Npc?) {
        npc?.let { faceSquare(it.coords) }
    }
}

/**
 * A fight with Yama (Fight.Yama.js, docs/yama.md). Ticks are counted by [process]; [at] schedules
 * work a number of ticks ahead of the fight's own clock. His attacks are driven by the boss
 * framework (see [Yama]), which calls [turn] once per attack.
 */
internal class YamaFight(
    val env: YamaEnv,
    val player: Player,
    val arena: YamaArena,
    val session: InstanceSession,
    val throne: Npc?,
) {
    val deps: BossDeps get() = env.deps

    var state = YamaState.SEATED
    var phase = 1
    var stance = Stance.MELEE
    var speed = 8
    var tick = 0
    var startTick = 0
    var stopped = false
    var over = false
    var pendingTransition = false
    var hurt = false

    var yama: Npc? = null
    val flames = ArrayList<LocInfo>()
    private val events = ArrayList<Pair<Int, () -> Unit>>()

    // phase 1 and 2 rotation
    var rotation: Rotation? = null
    var nextSpecialTick = Int.MAX_VALUE
    var hazardTurn = 0
    var majorGlyph = "shadow"

    // the units' state
    val immunity = IntArray(2)
    val glyphs = ArrayList<YamaGlyphs.Glyph>()
    val flares = ArrayList<YamaFlares.Flare>()
    val waves = ArrayList<YamaHazards.Wave>()
    val streaks = ArrayList<YamaHazards.Streak>()
    var lastTile: CoordGrid? = null
    var judge: YamaJudge.State? = null
    var phaseThree: YamaPhaseThree.State? = null
    var burnLeft = 0
    var burning = false

    enum class Stance { MELEE, MAGIC }

    class Rotation(
        val opening: String,
        var element: String,
        var lastSpecial: String? = null,
        var specials: Int = 0,
        var sinceSpecial: Int = 0,
        val flareSlot: Int,
    )

    /** The domain's floor tile the player stands on, as the cache map has it. */
    val playerSource: CoordGrid get() = arena.source(player.coords)

    fun at(ticks: Int, action: () -> Unit) {
        events += (tick + maxOf(1, ticks)) to action
    }

    fun runLoop() {
        deps.repeatTick(
            ticks = Int.MAX_VALUE,
            onTick = {
                if (stopped) {
                    false
                } else {
                    process()
                    true
                }
            },
            onStop = {},
        )
    }

    /** Every tick: due events, the hazard before a special, the glyphs, hazards, the Judge, targets. */
    private fun process() {
        if (state == YamaState.SEATED) return
        tick++
        val due = events.filter { it.first <= tick }
        events.removeAll(due.toSet())
        for ((_, action) in due) if (!stopped) action()
        if (stopped) return
        if (state == YamaState.FIGHTING) fightTick()
        if (phase < 3 && state == YamaState.FIGHTING) YamaGlyphs.process(this)
        YamaHazards.process(this)
        YamaJudge.process(this)
        YamaTargets.reassert(this)
    }

    private fun fightTick() {
        val yama = yama ?: return
        if (!yama.isSlotAssigned || yama.hitpoints <= 0) return
        yama.facePlayer(player)
        if (phase < 3 && !pendingTransition && yama.hitpoints <= YamaCommon.THRESHOLDS[phase - 1]) {
            pendingTransition = true
        }
        if (phase < 3 && tick == nextSpecialTick - hazardLead()) hazard()
    }

    /** One attack of Yama's, called by the framework on his cadence. */
    fun turn() {
        if (stopped || over || state != YamaState.FIGHTING) return
        if (pendingTransition) {
            YamaJudge.begin(this)
            return
        }
        if (phase == 3) {
            YamaPhaseThree.beat(this)
            return
        }
        val before = rotation?.specials ?: 0
        YamaAttacks.attack(this)
        if ((rotation?.specials ?: 0) > before) nextSpecialTick = tick + speed * 4
    }

    private fun hazardLead(): Int {
        val rotation = rotation ?: return Int.MAX_VALUE
        if (rotation.specials == 0) return Int.MAX_VALUE
        return if (hazardTurn % 2 == 0) YamaHazards.WAVE_LEAD else YamaHazards.STREAK_LEAD
    }

    private fun hazard() {
        if (hazardTurn++ % 2 == 0) {
            val first = if (deps.random.randomBoolean(2)) YamaHazards.Corner.SOUTH_EAST else YamaHazards.Corner.SOUTH_WEST
            YamaHazards.wave(this, first)
            if (phase == 2) YamaHazards.wave(this, first.other())
        } else {
            YamaHazards.streaks(this, if (phase == 2) 12 else 8)
        }
    }

    // ------------------------------------------------------------------ the summon

    fun start() {
        if (state != YamaState.SEATED) return
        state = YamaState.SUMMONING
        startTick = tick
        throne?.anim(YamaIds.SEQ_THRONE_SUMMON)
        throne?.spotanim(YamaIds.SPOT_THRONE_SUMMON)
        placeFlames(YamaIds.FLAMES)
        at(2) { summon() }
    }

    private fun summon() {
        val npc = spawn(YamaIds.YAMA, arena.tile(YamaCommon.YAMA_TILE)) ?: return
        yama = npc
        deps.encounterRegistry.of(npc).invulnerable = true
        npc.anim(YamaIds.SEQ_SUMMON)
        npc.spotanim(YamaIds.SPOT_SUMMON)
        npc.facePlayer(player)
        at(3) { engage() }
    }

    private fun engage() {
        val npc = yama ?: return
        if (stopped) return
        player.yamaThroneState = 1
        deps.encounterRegistry.of(npc).invulnerable = false
        npc.apPlayer2(player, deps.interactions)
        startPhase(1)
    }

    /** A phase begins: the glyphs (phases 1 and 2), his first attack and the stance. */
    fun startPhase(next: Int) {
        val npc = yama ?: return
        if (stopped || !npc.isSlotAssigned) return
        phase = next
        state = YamaState.FIGHTING
        pendingTransition = false
        speed = if (next == 3) 7 else 8
        deps.encounterRegistry.of(npc).transitionTo(PHASE_NAMES[next - 1], deps.mapClock.cycle)
        deps.encounterRegistry.of(npc).invulnerable = false
        val firstAttack = FIRST_ATTACK_TICKS[next - 1]
        if (next < 3) {
            val firstSpecial = YamaAttacks.startRotation(this)
            majorGlyph = firstSpecial
            hazardTurn = 0
            changeStance(Stance.MELEE)
            at(2) { YamaGlyphs.light(this, firstSpecial) }
            nextSpecialTick = tick + firstAttack + speed
        } else {
            npc.moveRestrict = MoveRestrict.NoMove
            npc.approach = NpcApproach.StopInRange
            npc.apRangeOverride = AP_RANGE
            YamaPhaseThree.start(this, majorGlyph)
        }
        deps.suppressAttacks(npc, firstAttack)
        npc.apPlayer2(player, deps.interactions)
    }

    /** After the Judge: the next phase, from in front of the throne. */
    fun nextPhase() {
        if (stopped) return
        at(PHASE_START_TICKS) { startPhase(phase + 1) }
    }

    /**
     * The challenger's last style on him sets his stance (phases 1 and 2): after melee he chases;
     * after magic or ranged he stands his ground. See YAMA-DEVIATIONS.md for the defence bonuses.
     */
    fun changeStance(next: Stance) {
        val npc = yama ?: return
        if (phase == 3) return
        stance = next
        // His attacks are on a timer, wherever the challenger stands (the reference's fight
        // loop), so the combat trigger must fire across the whole arena: ap range covers it.
        // After a melee hit he keeps closing in while casting; otherwise he stands his ground.
        npc.apRangeOverride = AP_RANGE
        if (next == Stance.MELEE) {
            npc.moveRestrict = MoveRestrict.Normal
            npc.approach = NpcApproach.ChaseToMelee
        } else {
            npc.moveRestrict = MoveRestrict.NoMove
            npc.approach = NpcApproach.StopInRange
        }
    }

    // ------------------------------------------------------------------ npcs and locs

    /** An npc of the domain's: pinned, no respawn, in the instance. */
    fun spawn(type: String, tile: CoordGrid): Npc? {
        val npcType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return null
        val npc = Npc(npcType, tile)
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        env.manager.attachNpc(session.id, npc)
        pin(npc)
        return npc
    }

    fun pin(npc: Npc) {
        npc.mode = NpcMode.None
        npc.moveRestrict = MoveRestrict.NoMove
        npc.respawns = false
    }

    fun remove(npc: Npc?) {
        if (npc != null && npc.isSlotAssigned) deps.npcRepo.del(npc, 0)
    }

    private val locs = HashMap<CoordGrid, LocInfo>()

    /** Puts [loc] on [tile], replacing whatever this fight put there before. */
    fun setLoc(tile: CoordGrid, loc: String, angle: LocAngle = LocAngle.West): LocInfo {
        locs.remove(tile)?.let { deps.locRepo.del(it, 0) }
        val info = deps.locRepo.add(tile, loc, Int.MAX_VALUE, angle, LocShape.CentrepieceStraight)
        locs[tile] = info
        return info
    }

    fun clearLocs() {
        for (loc in locs.values) deps.locRepo.del(loc, 0)
        locs.clear()
    }

    fun placeFlames(loc: String) {
        for ((x, z, angle) in FLAME_TILES) setLoc(arena.tile(x, z), loc, angle)
    }

    // ------------------------------------------------------------------ the kill

    /** Yama falls (his death starting): the kill's captured timeline. */
    fun won() {
        if (over || stopped) return
        over = true
        state = YamaState.OVER
        val ticks = tick - startTick
        yama?.spotanim(YamaIds.SPOT_DESPAWN)
        YamaFlares.clear(this)
        YamaHazards.clear(this)
        YamaGlyphs.clear(this)
        placeFlames(YamaIds.FLAMES_OPEN)
        at(5) {
            player.yamaThroneState = 0
            count(ticks)
            throne?.anim(YamaIds.SEQ_THRONE_RESPAWN)
            throne?.spotanim(YamaIds.SPOT_THRONE_RESPAWN)
        }
        at(9) {
            val low = player.hitpoints <= player.statBase("stat.hitpoints") / 4
            val lines =
                when {
                    !hurt -> PERFECT_LINES
                    low -> LOW_LINES
                    else -> DEFEAT_LINES
                }
            throne?.say(lines.random())
        }
        at(RESET_TICKS) { resetAfterKill() }
    }

    /** Yama is back on his throne and can be challenged again, without leaving the domain. */
    private fun resetAfterKill() {
        if (stopped) return
        stopFight()
        state = YamaState.SEATED
        resetThrone()
    }

    private fun count(ticks: Int) {
        val successes = player.yamaKills + 1
        player.yamaKills = successes
        player.lastYamaResult = YamaResult.VICTORIOUS
        player.mes("Your Yama success count is: <col=ff0000>$successes</col>.")
        val best = player.yamaBestTicks
        if (best == 0 || ticks < best) {
            player.yamaBestTicks = ticks
            player.mes("Fight duration: <col=ff0000>${formatYamaTicks(ticks)}</col> (new personal best)")
        } else {
            player.mes(
                "Fight duration: <col=ff0000>${formatYamaTicks(ticks)}</col>. " +
                    "Personal best: ${formatYamaTicks(best)}"
            )
        }
    }

    /** The player flees through the flames: an escape, and Yama back on his throne. */
    fun flee() {
        if (stopped || over) return
        player.lastYamaResult = YamaResult.ESCAPED
        stopFight()
        state = YamaState.SEATED
        resetThrone()
    }

    /** Everything the fight spawned goes; the throne is as it was. */
    fun stopFight() {
        events.clear()
        YamaFlares.clear(this)
        YamaHazards.clear(this)
        YamaGlyphs.clear(this)
        YamaJudge.clear(this)
        remove(yama)
        yama = null
        placeFlames(YamaIds.FLAMES_OPEN)
        over = false
        pendingTransition = false
        phase = 1
    }

    fun resetThrone() {
        player.yamaThroneState = 0
        throne?.let { if (it.isSlotAssigned) it.anim(YamaIds.SEQ_THRONE_RESPAWN) }
        clearLocs()
    }

    /** The domain closes (leave, logout, death): every npc and loc goes. */
    fun stop() {
        if (stopped) return
        stopFight()
        clearLocs()
        stopped = true
    }

    fun glide(npc: Npc, to: CoordGrid) {
        PathingEntityCommon.teleport(npc, deps.collision, to)
    }

    fun typelessDamage(amount: Int) {
        YamaHits.damage(this, amount)
    }

    companion object {
        val PHASE_NAMES = listOf("main", "two", "three")
        val FIRST_ATTACK_TICKS = intArrayOf(5, 4, 3)
        const val PHASE_START_TICKS = 3
        const val RESET_TICKS = 12
        const val AP_RANGE = 32

        val FLAME_TILES =
            listOf(
                Triple(1502, 10067, LocAngle.North),
                Triple(1503, 10067, LocAngle.East),
                Triple(1504, 10067, LocAngle.South),
            )

        val PERFECT_LINES = listOf("Effective, if a bit cowardly.", "Risk averse, are we?")
        val LOW_LINES =
            listOf(
                "A close fight. Your nerve was well held.",
                "A good risk, adequately rewarded.",
                "Excellent performance. Again?",
            )
        val DEFEAT_LINES =
            listOf(
                "Fair is fair.",
                "Your reward, as agreed.",
                "A satisfying warmup. Collect your loot and let us continue.",
                "A passing performance.",
            )
    }
}

/**
 * The controller (Fight.Yama.js and the framework wiring): registers the spec, the external turn,
 * the pinning of every Yama npc, the death and loot, and the hit hooks.
 */
class Yama
@Inject
internal constructor(
    deps: BossDeps,
    private val fights: YamaFights,
    private val npcDeath: NpcDeath,
    private val locRepo: LocRepository,
) : BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = { YamaHits.scaleIncoming(fights, this) },
            onHit = { YamaHits.recordStance(fights, this) },
            onLethal = { npc -> fights.fightOfNpc(npc)?.won() },
        )
        deps.extensionRegistry.register(TURN) { _, npc, _, _ -> fights.fightOfNpc(npc)?.turn() }

        val yamaIds = spec.npcTypes.mapNotNullTo(mutableSetOf()) { it.npcId() }
        // Every Yama npc is pinned, never respawns and idles on the throne before anything else
        // touches it.
        onEvent<NpcStateEvents.Create> { if (npc.type.id in yamaIds) pinNpc(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.type.id in yamaIds) pinNpc(npc) }

        // The Voice of Yama stands still, wherever the map data places him.
        val voiceIds =
            listOf(YamaIds.VOICE, YamaIds.VOICE_1OP, YamaIds.VOICE_2OP, YamaIds.VOICE_3OP)
                .mapNotNullTo(mutableSetOf()) { it.npcId() }
        onEvent<NpcStateEvents.Create> { if (npc.type.id in voiceIds) pinNpc(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.type.id in voiceIds) pinNpc(npc) }

        YamaLoot.register(this, fights, npcDeath)
        YamaFlares.registerHooks(this, fights)
        YamaJudge.registerHooks(this, fights, npcDeath)
    }

    private fun pinNpc(npc: Npc) {
        npc.mode = NpcMode.None
        npc.moveRestrict = MoveRestrict.NoMove
        npc.respawns = false
    }

    override val spec =
        boss(YamaIds.YAMA) {
            stats(attackRate = 8)
            val turn = ability("turn", external(TURN))
            phase("main", attackRate = 8) { weightedSelectorRandom { +random(turn, weight = 1) } }
            phase("two", attackRate = 8) { weightedSelectorRandom { +random(turn, weight = 1) } }
            phase("three", attackRate = 7) { weightedSelectorRandom { +random(turn, weight = 1) } }
        }

    private companion object {
        private const val TURN = "yama.turn"

        private fun String.npcId(): Int? = ServerCacheManager.getNpc(this.asRSCM(RSCMType.NPC))?.id
    }
}
