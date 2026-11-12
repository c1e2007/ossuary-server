package org.rsmod.content.raids.toabots

import com.github.michaelbull.logging.InlineLogger
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.loader.request.AccountLoadAuth
import org.rsmod.api.account.loader.request.AccountLoadResponse
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.net.rsprot.BotSessions
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.isSuccess
import org.rsmod.api.script.onCommand
import org.rsmod.content.raids.toa.party.ToaPartyManager
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Server-run Tombs of Amascut party members, for admins only. `::toabots` brings the accounts in
 * `toa-bots/bot-accounts.txt` into the world with no client, joins them to the caller's lobby party,
 * and has them follow the caller. Each bot wears the gear set that matches the caller's weapon
 * (magic, ranged or melee), attacks whatever the caller attacks, eats, and drinks. `::toabots stop`
 * logs them out. No passwords are used: the account list is the gate.
 */
class ToaBots
@Inject
constructor(
    private val deps: BossDeps,
    private val accounts: AccountManager,
    private val accountRegistry: AccountRegistry,
    private val playerRegistry: PlayerRegistry,
    private val botSessions: BotSessions,
    private val eventBus: EventBus,
    private val npcInteractions: NpcInteractions,
    private val heldEquip: HeldEquipOp,
    private val heldInteractions: HeldInteractions,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {
    private val logger = InlineLogger()
    private val bots = ArrayList<Bot>()
    private var loading = 0

    private class Bot(val player: Player, val leader: Player) {
        var style: Style? = null
        var nextConsume = 0
        var overhead = -1
    }

    override fun ScriptContext.startup() {
        onCommand("toabots") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Run your ToA party members as server bots: ::toabots, ::toabots stop"
            cheat { command() }
        }
    }

    private fun accountNames(): List<String> =
        ToaBots::class
            .java
            .classLoader
            .getResource("toa-bots/bot-accounts.txt")
            ?.readText()
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            .orEmpty()

    private fun Cheat.command() {
        if (args.firstOrNull()?.lowercase() == "stop") {
            player.mes("Logging ${bots.size} bot(s) out.")
            bots.toList().forEach { leave(it) }
            return
        }
        if (bots.isNotEmpty() || loading > 0) {
            player.mes("The bots are already running. ::toabots stop to end them.")
            return
        }
        for (name in accountNames()) {
            if (deps.playerList.any { it.username.equals(name, true) }) {
                player.mes("$name is already logged in. Log that account out first.")
                continue
            }
            load(name, player)
        }
    }

    private fun load(name: String, leader: Player) {
        loading++
        val queued =
            accounts.load(AccountLoadAuth.UnknownDevice, name.lowercase()) { response ->
                if (response is AccountLoadResponse.Ok) {
                    val fresh = Player()
                    response.transforms.forEach { it.apply(fresh) }
                    fresh.worldType = response.worldType
                    fresh.ui.setWindowStatus(width = 765, height = 503, resizable = false)
                    accountRegistry.queueLogin(fresh, response) { p, _ -> enterWorld(p, leader) }
                } else {
                    loading--
                    leader.mes("Bot account '$name' could not be loaded (does it exist?): $response")
                    logger.warn { "Bot account '$name' could not be loaded: $response" }
                }
            }
        if (!queued) {
            loading--
            leader.mes("The account loader is busy. Try again in a moment.")
        }
    }

    private fun enterWorld(player: Player, leader: Player) {
        loading--
        val slot = playerRegistry.nextFreeSlot()
        if (slot == null) {
            leader.mes("The world is full.")
            return
        }
        eventBus.publish(SessionStateEvent.PrepareLogin(player))
        player.slotId = slot
        botSessions.attach(player)
        if (!playerRegistry.add(player).isSuccess()) {
            botSessions.release(player)
            leader.mes("A bot could not join the world.")
            return
        }
        eventBus.publish(SessionStateEvent.Login(player))
        eventBus.publish(SessionStateEvent.EngineLogin(player))
        val bot = Bot(player, leader)
        bots += bot
        giveSupplies(player)
        wear(bot, styleOf(leader))
        launcher.launch(player) { telejump(leader.coords) }
        joinParty(bot)
        leader.mes("${player.displayName} joined you.")
        schedule(bot)
    }

    private fun joinParty(bot: Bot) {
        val party = ToaPartyManager.allParties().firstOrNull { it.isLeader(bot.leader) }
        if (party == null) {
            bot.leader.mes("Make your party first, then run ::toabots again so the bots can join it.")
            return
        }
        party.apply(bot.player)
        ToaPartyManager.acceptApplicant(party, bot.player)
    }

    private fun leave(bot: Bot) {
        bots.remove(bot)
        bot.player.pendingLogout = true
    }

    private fun schedule(bot: Bot) {
        deps.worldQueues.add(1) {
            if (bot in bots && bot.player.isSlotAssigned) {
                runCatching { think(bot) }.onFailure { logger.error(it) { "A ToA bot hit an error" } }
                schedule(bot)
            }
        }
    }

    // --- Gear -------------------------------------------------------------------------------------

    private enum class Style(val kit: List<Pair<Int, Int>>) {
        MAGIC(
            listOf(
                21018 to 1, 21021 to 1, 21024 to 1, 27275 to 1, 31106 to 1,
                28313 to 1, 34625 to 1, 12002 to 1, 13235 to 1,
            )
        ),
        RANGED(
            listOf(
                27235 to 1, 27238 to 1, 27241 to 1, 20997 to 1, 26235 to 1,
                28310 to 1, 28902 to 1, 33639 to 1, 13237 to 1, 11212 to 4936,
            )
        ),
        MELEE(
            listOf(
                26382 to 1, 26384 to 1, 26386 to 1, 27246 to 1, 22322 to 1,
                22981 to 1, 29801 to 1, 28307 to 1, 24133 to 1, 13239 to 1,
            )
        ),
    }

    private fun styleOf(leader: Player): Style {
        val weapon = leader.righthand?.id ?: return Style.MELEE
        return Style.entries.firstOrNull { style -> style.kit.any { it.first == weapon } } ?: Style.MELEE
    }

    @OptIn(dev.openrune.types.util.UncheckedType::class, org.rsmod.annotations.InternalApi::class)
    private fun wear(bot: Bot, style: Style) {
        if (bot.style == style) return
        val player = bot.player
        bot.style = style
        for (slot in player.worn.indices) player.worn[slot] = null
        // Wear one item at a time through slot 0 of the pack, then restore the supplies.
        val saved = player.inv.indices.map { player.inv[it] }
        for ((id, count) in style.kit) {
            player.inv[0] = InvObj(id, count)
            heldEquip.equip(player, 0, player.inv)
        }
        for (slot in player.inv.indices) player.inv[slot] = saved[slot]
        player.rebuildAppearance()
    }

    @OptIn(dev.openrune.types.util.UncheckedType::class, org.rsmod.annotations.InternalApi::class)
    private fun giveSupplies(player: Player) {
        for (slot in player.inv.indices) player.inv[slot] = null
        List(6) { 3024 to 1 }.plus(List(20) { 385 to 1 }).forEachIndexed { slot, (id, n) ->
            player.inv[slot] = InvObj(id, n)
        }
        for (stat in listOf("stat.hitpoints", "stat.prayer")) {
            player.statMap.setCurrentLevel(stat, player.statMap.getBaseLevel(stat))
            player.markStatUpdate(stat)
        }
    }

    // --- The brain, one tick at a time --------------------------------------------------------------

    private fun think(bot: Bot) {
        val player = bot.player
        val leader = bot.leader
        if (!leader.isSlotAssigned) {
            leave(bot)
            return
        }
        if (player.hitpoints <= 0) return
        wear(bot, styleOf(leader))
        consume(bot)
        val target = (leader.interaction as? InteractionNpcOp)?.target
        if (target != null && target.hitpoints > 0) {
            attack(player, target)
            return
        }
        follow(player, leader)
    }

    private fun follow(player: Player, leader: Player) {
        if (player.coords.level != leader.coords.level ||
            player.coords.chebyshevDistance(leader.coords) > TELEPORT_DISTANCE
        ) {
            launcher.launch(player) { telejump(leader.coords) }
            return
        }
        if (player.coords.chebyshevDistance(leader.coords) <= FOLLOW_DISTANCE || player.isDelayed) return
        player.clearPendingAction(eventBus)
        player.interaction = null
        player.tempMoveSpeed = MoveSpeed.Run
        player.routeRequest = RouteRequestCoord(leader.coords)
    }

    private fun attack(player: Player, npc: Npc) {
        val current = player.interaction as? InteractionNpcOp
        if (current?.target === npc) return
        if (player.isDelayed || npc.isDelayed) return
        val op = ATTACK_OPS.firstOrNull { npcInteractions.hasOp(npc, player.vars, it) } ?: return
        player.clearPendingAction(eventBus)
        player.faceNpc(npc)
        player.interaction =
            InteractionNpcOp(
                target = npc,
                op = op,
                hasOpTrigger = npcInteractions.hasOpTrigger(player, npc, op),
                hasApTrigger = npcInteractions.hasApTrigger(player, npc, op),
            )
        player.routeRequest = RouteRequestPathingEntity(npc.avatar, clientRequest = true)
    }

    private fun consume(bot: Bot) {
        val player = bot.player
        val now = deps.mapClock.cycle
        if (now < bot.nextConsume || player.isDelayed) return
        val pattern =
            when {
                player.hitpoints < EAT_BELOW -> FOOD
                player.prayerLvl < DRINK_BELOW -> PRAYER_POTION
                else -> return
            }
        val slot =
            player.inv.indices.firstOrNull {
                pattern.containsMatchIn(player.inv[it]?.let { o -> getInvObj(o).name.lowercase() }.orEmpty())
            } ?: return
        bot.nextConsume = now + CONSUME_GAP
        launcher.launch(player) { heldInteractions.interact(this, player.inv, slot, HeldOp.Op1) }
    }

    private companion object {
        val ATTACK_OPS = listOf(InteractionOp.Op2, InteractionOp.Op1, InteractionOp.Op3)
        const val FOLLOW_DISTANCE = 2
        const val TELEPORT_DISTANCE = 20
        const val EAT_BELOW = 55
        const val DRINK_BELOW = 25
        const val CONSUME_GAP = 3
        val FOOD = Regex("shark|anglerfish|manta|karambwan|monkfish|lobster|saradomin brew")
        val PRAYER_POTION = Regex("prayer potion|super restore")
    }
}
