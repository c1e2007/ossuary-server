package org.rsmod.content.other.testerrank

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.shops.Shops
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The tester hub (`interface.tester_hub`, defined in this module's pack): teleports between the two
 * areas, spellbook swaps, and free gear shops. Shop stock lives here in [SHOPS], so it can be
 * changed without rebuilding the cache.
 */
class TesterHub @Inject constructor(private val shops: Shops, private val commands: CheatCommandMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        button("tp_tfa") { teleport(TesterRank.START) }
        button("tp_toa") { teleport(TesterRank.TOA_LOBBY) }
        for (book in listOf("standard", "ancients", "lunars", "arceuus")) {
            button("sb_$book") { commands.execute(player, "spellbook", listOf(book)) }
        }
        for ((name, shop) in SHOPS) {
            button(name) { openShop(shop) }
        }
        // Shops here never run out: every tick the stock is put back as it was.
        onPlayerSoftTimer(TIMER) { refill(player) }
        button("sup_stats") {
            maxStats(player)
            mes("All stats set to 99.")
        }
    }

    private val shopsOpen = WeakHashMap<Player, Pair<Inventory, List<InvObj?>>>()

    private fun refill(player: Player) {
        if (player.openedShop == null) {
            shopsOpen.remove(player)
            return
        }
        val (inv, stock) = shopsOpen[player] ?: return
        for (i in stock.indices) {
            val want = stock[i] ?: continue
            if (inv[i]?.count != want.count) inv[i] = want
        }
        topUpCoins(player)
        player.softTimer(TIMER, 1)
    }

    private fun ScriptContext.button(name: String, action: suspend ProtectedAccess.() -> Unit) {
        onIfModalButton("component.tester_hub:$name") { action() }
    }

    private fun ProtectedAccess.teleport(dest: CoordGrid) {
        ifClose()
        mes("The crystal carries you away.")
        telejump(dest, TeleportType.Exempt)
    }

    @OptIn(UncheckedType::class)
    private fun ProtectedAccess.openShop(shop: Shop) {
        val inv = Inventory.create(SHOP_INV)
        inv.fillNulls()
        var slot = 0
        for (name in shop.items) {
            val item = lookup(name)
            if (item == null) {
                continue
            }
            if (slot >= inv.size) break
            // One of each (a count of 1 is drawn without a number); stackables get a big pile.
            inv[slot++] = InvObj(item.id, if (item.isStackable) 1_000_000 else 1)
        }
        shopsOpen[player] = inv to (0 until inv.size).map { inv[it] }
        softTimer(TIMER, 1)
        shops.open(
            player = player,
            title = shop.title,
            shopInv = inv,
            sideInv = player.inv,
            buyPercentage = 0.0,
            sellPercentage = 0.0,
            changePercentage = 0.0,
        )
    }

    private fun lookup(name: String) =
        if (name.startsWith("obj.")) ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ)) else
        ServerCacheManager.getItems().values.let { all ->
            val real = all.filter { !it.isPlaceholder && !it.isCert && !it.isDummyItem }
            real.filter { it.name.equals(name, true) }.minByOrNull { it.id }
                ?: real.filter { it.name.startsWith(name, true) }.minByOrNull { it.id }
        }

    class Shop(val title: String, val items: List<String>)

    companion object {
        /** Opens the hub; called by the crystals. */
        fun open(access: ProtectedAccess) {
            access.ifOpenMainModal("interface.tester_hub")
        }

        const val SHOP_INV = "inv.generalshop3"
        const val TIMER = "timer.tester_shop"

        val SHOPS: Map<String, Shop> =
            mapOf(
                "gear_melee" to Shop("Melee gear", listOf(
                    "Torva full helm", "Torva platebody", "Torva platelegs", "Neitiznot faceguard",
                    "Inquisitor's great helm", "Inquisitor's hauberk", "Inquisitor's plateskirt",
                    "Bandos chestplate", "Bandos tassets", "Amulet of torture",
                    "obj.ultor_ring", "Infernal cape", "Fire cape", "Ferocious gloves",
                    "Primordial boots", "Scythe of vitur", "Ghrazi rapier", "obj.osmumtens_fang",
                    "obj.keris_partisan_sun", "Abyssal bludgeon", "obj.abyssal_whip", "obj.voidwaker",
                    "obj.dragon_claws", "obj.dragon_warhammer", "Avernic defender",
                    "Dinh's bulwark", "Lightbearer", "Regen bracelet", "Serpentine helm",
                )),
                "gear_ranged" to Shop("Ranged gear", listOf(
                    "Masori mask (f)", "Masori body (f)", "Masori chaps (f)", "Necklace of anguish",
                    "obj.dizanas_quiver_infinite", "obj.venator_ring", "Zaryte vambraces",
                    "Pegasian boots", "Twisted bow", "obj.zaryte_xbow", "Venator bow",
                    "Armadyl crossbow", "Dragon hunter crossbow", "obj.toxic_blowpipe_loaded", "Dragon arrow",
                    "obj.dragon_bolts_enchanted_ruby", "obj.dragon_bolts_enchanted_diamond", "Dragon dart",
                )),
                "gear_magic" to Shop("Magic gear", listOf(
                    "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Occult necklace",
                    "obj.magus_ring", "Eternal boots", "Imbued saradomin cape",
                    "Imbued guthix cape", "Imbued zamorak cape", "Tormented bracelet",
                    "Tumeken's shadow", "Sanguinesti staff", "Harmonised nightmare staff",
                    "obj.toxic_tots_charged", "Elidinis' ward (f)", "Arcane spirit shield",
                    "Tome of fire", "Virtus mask", "Virtus robe top", "Virtus robe bottom",
                )),
                "sup_food" to Shop("Potions and food", listOf(
                    "Super combat potion(4)", "Divine super combat potion(4)", "Ranging potion(4)",
                    "Divine ranging potion(4)", "Magic potion(4)", "Divine magic potion(4)",
                    "Super restore(4)", "Prayer potion(4)", "Saradomin brew(4)", "Stamina potion(4)",
                    "Super strength(4)", "Super attack(4)", "Super defence(4)", "Sanfew serum(4)",
                    "Antidote++(4)", "Extended antifire(4)", "Shark", "Manta ray", "Anglerfish",
                    "Cooked karambwan",
                )),
                "sup_runes" to Shop("Runes and extras", listOf(
                    "Rune pouch", "Blood rune", "Death rune", "Soul rune", "Water rune", "Fire rune",
                    "Law rune", "Astral rune", "Earth rune", "Chaos rune", "Nature rune",
                    "Holy wrench", "Ring of suffering (i)",
                )),
            )
    }
}

/** Testers carry a full stack of coins, so shop prices never matter. */
internal fun topUpCoins(player: Player) {
    val have = player.inv.count("obj.coins")
    if (have < Int.MAX_VALUE) {
        player.invAdd(player.inv, "obj.coins", Int.MAX_VALUE - have, strict = false)
    }
}

@OptIn(InternalApi::class)
internal fun maxStats(player: Player) {
    val xp = PlayerSkillXPTable.getXPFromLevel(99)
    for (stat in ServerCacheManager.getStats().values) {
        val name = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
        player.statMap.setXP(name, xp)
        player.statMap.setBaseLevel(name, 99)
        player.statMap.setCurrentLevel(name, 99)
    }
}
