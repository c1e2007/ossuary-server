package org.rsmod.content.raids.tfa.raid.reward

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.clearSpotanimSpecific
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

private var Player.vaultOwnChest by intVarBit("varbit.tfa_vault_own_chest")

private val Player.vaultKey: Long
    get() = checkNotNull(uuid) { "Player has no uuid." }

/**
 * The reward room: one chest per fighter, plus a unique chest for whoever's roll hit. Loot is
 * rolled when the boss dies and sits in each fighter's reward inventory, so a fighter who walks
 * out without opening anything finds it again at the lobby wheelbarrow.
 */
@Singleton
class TfaVault
@Inject
constructor(
    private val loot: TfaVaultLoot,
    private val playerList: PlayerList,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val collision: CollisionFlagMap,
) {
    private class Session(val slots: Map<Long, Int>, val claim: TfaUniqueClaim) {
        var built = false
        var unique: Npc? = null
        var blade: Npc? = null
        val chests = HashMap<Int, Npc>()
        val opened = HashSet<Int>()
        val locs = ArrayList<LocInfo>()
    }

    private var session: Session? = null

    fun begin(fighters: List<Player>, bladeAt: CoordGrid) {
        reset()
        val party = fighters.take(CHEST_TILES.size)
        val slots = LinkedHashMap<Long, Int>()
        party.forEachIndexed { index, player ->
            slots[player.vaultKey] = index + 1
            player.vaultOwnChest = index + 1
            loot.stock(player, loot.rollStandard(player))
        }
        var winner: Long? = null
        var uniqueObj: String? = null
        for (player in party.shuffled()) {
            val earned = loot.rollUnique(player) ?: continue
            winner = player.vaultKey
            uniqueObj = earned
            break
        }
        val next = Session(slots, TfaUniqueClaim(winner, uniqueObj))
        session = next
        worldQueues.add(BLADE_DELAY) {
            if (session !== next) return@add
            val blade = Npc(BLADE, bladeAt)
            blade.moveRestrict = MoveRestrict.NoMove
            npcRepo.add(blade, Int.MAX_VALUE)
            next.blade = blade
        }
    }

    fun party(near: CoordGrid): List<Player> {
        val current = session ?: return emptyList()
        return playerList.filter {
            it.uuid in current.slots &&
                it.coords.level == near.level &&
                it.coords.chebyshevDistance(near) <= GATHER_RADIUS
        }
    }

    fun enter(players: List<Player>) {
        buildRoom()
        val current = session ?: return
        for (player in players) {
            PathingEntityCommon.telejump(player, collision, ARRIVAL)
            worldQueues.add(SPARKLE_DELAY) { sparkle(current, player) }
        }
    }

    /** The spotanim plays once per [SPARKLE_LOOP] ticks, so it is replayed until the chest opens. */
    private fun sparkle(current: Session, player: Player) {
        if (session !== current || !player.isSlotAssigned) return
        val slot = current.slots[player.uuid] ?: return
        val chest = current.chests[slot] ?: return
        if (!chest.isSlotAssigned || slot in current.opened || loot.isEmpty(player)) return
        if (player.coords.level != ARRIVAL.level) return
        player.spotanimSpecific(chest, SPARKLES_SPOT, slot = SPARKLES_SLOT)
        worldQueues.add(SPARKLE_LOOP) { sparkle(current, player) }
    }

    fun leave(player: Player) {
        settle(player)
        PathingEntityCommon.telejump(player, collision, LOBBY)
    }

    private fun buildRoom() {
        val current = session ?: return
        if (current.built) return
        current.built = true
        CHEST_TILES.forEachIndexed { index, tile ->
            val slot = index + 1
            val npc = Npc("npc.tfa_rewards_chest_vault_${slot}_closed", tile)
            npc.moveRestrict = MoveRestrict.NoMove
            npcRepo.add(npc, Int.MAX_VALUE)
            current.chests[slot] = npc
        }
        current.locs +=
            locRepo.add(SCOREBOARD, SCOREBOARD_LOC, Int.MAX_VALUE, LocAngle.North, LocShape.CentrepieceStraight)
        current.locs += locRepo.add(EXIT, EXIT_LOC, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        val hasUnique = current.claim.hasUnique
        worldQueues.add(BRAZIER_DELAY) {
            if (session !== current) return@add
            val brazier = if (hasUnique) BRAZIER_UNIQUE else BRAZIER_STANDARD
            for ((tile, angle) in BRAZIERS) {
                if (!hasUnique && locRepo.findLoc(tile, brazier)) continue
                current.locs += locRepo.add(tile, brazier, Int.MAX_VALUE, angle, LocShape.GroundDecor)
            }
            if (!hasUnique) return@add
            BRAZIER_SOUND?.let { sound ->
                for (player in playerList) {
                    if (player.uuid in current.slots && player.coords.level == ARRIVAL.level) {
                        player.soundSynth(sound)
                    }
                }
            }
            worldQueues.add(UNIQUE_CHEST_DELAY) {
                if (session !== current) return@add
                val chest = Npc(UNIQUE_CLOSED, UNIQUE_CHEST)
                chest.moveRestrict = MoveRestrict.NoMove
                npcRepo.add(chest, Int.MAX_VALUE)
                chest.anim(UNIQUE_FLOAT_SEQ)
                current.unique = chest
            }
        }
    }

    fun slotOf(player: Player): Int? = session?.slots?.get(player.uuid)

    fun chestOf(slot: Int): Npc? = session?.chests?.get(slot)

    fun isWinner(player: Player): Boolean = session?.claim?.isWinner(player.uuid) == true

    fun openChest(player: Player, slot: Int) {
        val npc = chestOf(slot) ?: return
        session?.opened?.add(slot)
        player.clearSpotanimSpecific(npc, SPARKLES_SLOT)
        val type = ServerCacheManager.getNpc("npc.tfa_rewards_chest_vault_${slot}_open".asRSCM(RSCMType.NPC))!!
        npc.transmog(type, Int.MAX_VALUE)
    }

    fun beginReveal(): String? = session?.claim?.beginReveal()

    fun finishReveal(player: Player) {
        val current = session ?: return
        val obj = current.claim.finishReveal() ?: return
        current.unique?.let {
            val type = ServerCacheManager.getNpc(UNIQUE_OPEN.asRSCM(RSCMType.NPC))!!
            it.transmog(type, Int.MAX_VALUE)
        }
        loot.add(player, obj, 1)
    }

    fun settle(player: Player) {
        val obj = session?.claim?.settle(player.uuid) ?: return
        loot.add(player, obj, 1)
    }

    private fun reset() {
        val previous = session ?: return
        for (npc in previous.chests.values) if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        for (npc in listOfNotNull(previous.unique, previous.blade)) {
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }
        for (loc in previous.locs) locRepo.del(loc, Int.MAX_VALUE)
        session = null
    }

    companion object {
        val ARRIVAL = CoordGrid(4127, 5776, 2)
        val LOBBY = CoordGrid(4129, 5559, 0)

        val UNIQUE_CHEST = CoordGrid(4126, 5807, 2)

        private val CHEST_TILES =
            listOf(
                CoordGrid(4124, 5793, 2),
                CoordGrid(4130, 5793, 2),
                CoordGrid(4121, 5796, 2),
                CoordGrid(4133, 5796, 2),
                CoordGrid(4120, 5800, 2),
                CoordGrid(4134, 5800, 2),
                CoordGrid(4121, 5803, 2),
                CoordGrid(4133, 5803, 2),
            )
        private val BRAZIERS =
            listOf(CoordGrid(4123, 5805, 2) to LocAngle.East, CoordGrid(4131, 5805, 2) to LocAngle.West)
        private val SCOREBOARD = CoordGrid(4129, 5778, 2)
        private val EXIT = CoordGrid(4127, 5773, 2)

        const val BLADE = "npc.tfa_blade_npc_vault_entry"
        const val UNIQUE_CLOSED = TfaChestKeys.UNIQUE
        const val UNIQUE_OPEN = "npc.tfa_unique_chest_vault_open"
        const val UNIQUE_FLOAT_SEQ = "seq.chest_guthixian01_float_down"
        private const val BRAZIER_STANDARD = "loc.tfa_brazier_guthixloot01_fire01"
        private const val BRAZIER_UNIQUE = "loc.tfa_brazier_guthixloot01_fire02"
        private const val SCOREBOARD_LOC = "loc.tfa_vault_scoreboard"
        private const val EXIT_LOC = "loc.tfa_vault_exit"
        private const val SPARKLES_SPOT = "spotanim.vfx_tfa_small_chest_sparkles"
        private const val SPARKLES_SLOT = 1
        private const val SPARKLE_DELAY = 3
        private const val SPARKLE_LOOP = 3
        private const val BLADE_DELAY = 14
        private const val BRAZIER_DELAY = 12

        /** The synth the fires light with; not in any capture, so unset until its symbol is known. */
        private val BRAZIER_SOUND: String? = null
        private const val UNIQUE_CHEST_DELAY = 2
        private const val GATHER_RADIUS = 30
    }
}
