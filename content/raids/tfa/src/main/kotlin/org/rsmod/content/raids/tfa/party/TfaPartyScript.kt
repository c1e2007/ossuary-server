package org.rsmod.content.raids.tfa.party

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.raids.party.RaidParty
import org.rsmod.content.raids.party.RaidPartyRegistry.ApplyResult
import org.rsmod.content.raids.tfa.raid.reward.TfaVault
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TfaPartyScript
@Inject
constructor(private val parties: TfaParties, private val lobby: TfaLobby) : PluginScript() {
    private var Player.friendsOnly by intVarBit("varbit.tfa_lobby_friends_filter")

    private enum class View {
        NonMember,
        Member,
        Leader,
        Applicant,
        Kicked,
    }

    override fun ScriptContext.startup() {
        onOpLoc1("loc.tfa_grouping_board") { openBoard() }
        onArea(LOBBY_AREA) { openLobbyHud() }
        onAreaExit(LOBBY_AREA) { exitLobby() }
        onEvent<SessionStateEvent.PrepareLogin> {
            if (lobby.wasInRaid(player)) player.coords = TfaVault.LOBBY
        }
        onPlayerLogin { lobby.markNotInParty(player) }
        onPlayerLogout { lobby.leave(player) }
    }

    private suspend fun ProtectedAccess.openBoard() {
        val own = parties.partyOf(player)
        if (own == null) listLoop() else detailsLoop(own, tab = TAB_MEMBERS)
    }

    internal suspend fun ProtectedAccess.openList() = listLoop()

    private suspend fun ProtectedAccess.listLoop() {
        while (true) {
            lobby.markViewing(player)
            val listed = populateList()
            val input = pauseButton()
            ifClose()
            when (input.component) {
                LIST_CONTENTS ->
                    when (input.subcomponent) {
                        LIST_REFRESH -> continue
                        LIST_MY_PARTY -> {
                            val party = openOwnOrCreate() ?: continue
                            detailsLoop(party, tab = TAB_APPLICANTS)
                        }
                        LIST_FILTER -> player.friendsOnly = if (player.friendsOnly == 0) 1 else 0
                    }
                LIST_ROWS -> {
                    val party = listed.getOrNull(input.subcomponent)
                    if (party == null || party !in parties.parties) {
                        mesbox(NO_LONGER_RECRUITING)
                        continue
                    }
                    detailsLoop(party, tab = TAB_MEMBERS)
                }
            }
        }
    }

    private fun ProtectedAccess.populateList(): List<RaidParty<TfaPartySettings>> {
        ifOpenMainModal(LIST_INTERFACE, transparency = MODAL_TRANSPARENCY)
        val listed = parties.parties.take(TfaParties.MAX_LISTED)
        for (index in 0 until TfaParties.MAX_LISTED) {
            val row = listed.getOrNull(index)?.let { row(it) }.orEmpty()
            player.runClientScript(ADD_ROW.asRSCM(RSCMType.CLIENTSCRIPT), index, row)
        }
        ifSetEvents(LIST_CONTENTS, 0..2, IfEvent.PauseButton)
        ifSetEvents(LIST_ROWS, 0 until TfaParties.MAX_LISTED, IfEvent.PauseButton)
        return listed
    }

    private fun ProtectedAccess.row(party: RaidParty<TfaPartySettings>): String {
        val leader = party.leader ?: return ""
        val name = if (party.isMember(player)) "<col=ffffff>${leader.displayName}</col>" else leader.displayName
        val others = party.members.drop(1).map { it.displayName }
        return TfaPartyFormat.row(name, others, party.size, party.settings, mapClock - party.createdAt)
    }

    private fun ProtectedAccess.openOwnOrCreate(): RaidParty<TfaPartySettings>? {
        parties.partyOf(player)?.let { return it }
        if (parties.isFull) {
            mes("The list of lobby parties is currently full. Please come back later or apply to an existing party.")
            return null
        }
        return lobby.create(player, mapClock)
    }

    private suspend fun ProtectedAccess.detailsLoop(party: RaidParty<TfaPartySettings>, tab: Int) {
        var currentTab = tab
        while (party.leader != null) {
            openDetails(party, currentTab)
            val input = pauseButton()
            ifClose()
            if (input.component != TfaLobby.DETAILS_BUTTONS) continue
            when (input.subcomponent) {
                BACK -> return
                TfaLobby.REFRESH -> continue
                UNBLOCK -> unblock(party)
                PREFERRED_SIZE -> setPreferredSize(party)
                PREFERRED_COMPLETIONS -> setPreferredCompletions(party)
                PREFERRED_MODE -> toggleMode(party)
                ACTION -> if (!action(party)) return
                MEMBERS_TAB -> currentTab = TAB_MEMBERS
                APPLICANTS_TAB -> currentTab = TAB_APPLICANTS
                SPECTATE_TOGGLE -> toggleSpectating(party)
                in MEMBER_ROWS -> if (!memberRow(party, input.subcomponent - MEMBER_ROWS.first)) return
                in ACCEPT_ROWS -> accept(party, input.subcomponent - ACCEPT_ROWS.first)
                in REJECT_ROWS -> reject(party, input.subcomponent - REJECT_ROWS.first)
            }
            if (input.subcomponent !in TAB_BUTTONS) lobby.refreshViewers(party, exclude = player)
        }
    }

    private fun ProtectedAccess.openDetails(party: RaidParty<TfaPartySettings>, tab: Int) {
        ifOpenMainModal(TfaLobby.DETAILS_INTERFACE, transparency = MODAL_TRANSPARENCY)
        val view = viewOf(player, party)
        val addMember = ADD_MEMBER.asRSCM(RSCMType.CLIENTSCRIPT)
        for (slot in 0 until TfaParties.MAX_MEMBERS) {
            val text = party.members.getOrNull(slot)?.let { stats(it) }.orEmpty()
            player.runClientScript(addMember, view.ordinal, text)
        }
        val addApplicant = ADD_APPLICANT.asRSCM(RSCMType.CLIENTSCRIPT)
        for (applicant in party.applicants) {
            player.runClientScript(addApplicant, stats(applicant))
        }
        val settings = party.settings
        player.runClientScript(
            MASTER_UPDATE.asRSCM(RSCMType.CLIENTSCRIPT),
            view.ordinal,
            settings.preferredCompletions,
            settings.mode,
            settings.preferredSize,
            if (settings.spectatingDisabled) 1 else 0,
            tab,
        )
        ifSetEvents(TfaLobby.DETAILS_BUTTONS, 0 until DETAILS_BUTTON_COUNT, IfEvent.PauseButton)
    }

    private fun stats(member: Player): String =
        TfaPartyFormat.member(
            name = member.displayName,
            combat = member.combatLevel,
            skills = SKILLS.map { member.statBase(it) },
            completions = member.vars[COMPLETIONS_VARP],
            practice = 0,
        )

    private fun viewOf(player: Player, party: RaidParty<TfaPartySettings>): View =
        when {
            party.isLeader(player) -> View.Leader
            party.isMember(player) -> View.Member
            party.isApplicant(player) -> View.Applicant
            party.isBlocked(player) -> View.Kicked
            else -> View.NonMember
        }

    private suspend fun ProtectedAccess.action(party: RaidParty<TfaPartySettings>): Boolean =
        when (viewOf(player, party)) {
            View.NonMember -> applyTo(party)
            View.Member -> {
                leaveMessage(party)
                false
            }
            View.Leader -> {
                disband(party)
                false
            }
            View.Applicant -> {
                if (parties.withdraw(player) != null) mes("You have withdrawn your party application.")
                true
            }
            View.Kicked -> {
                mes("You have been declined by this party.")
                true
            }
        }

    private suspend fun ProtectedAccess.applyTo(party: RaidParty<TfaPartySettings>): Boolean {
        val result = parties.apply(player, party)
        when (result) {
            ApplyResult.Applied ->
                mes("You have applied to join the party of ${party.leader?.displayName}.")
            ApplyResult.AlreadyInParty -> mes("You are already in a party.")
            ApplyResult.NotListed,
            ApplyResult.Refused -> {
                mesbox(NO_LONGER_RECRUITING)
                return false
            }
        }
        return true
    }

    private fun ProtectedAccess.leaveMessage(party: RaidParty<TfaPartySettings>) {
        val name = party.leader?.displayName
        if (lobby.leave(player)) mes("You have left the party of $name.")
    }

    private fun ProtectedAccess.disband(party: RaidParty<TfaPartySettings>) {
        val applicants = party.applicants.toList()
        val former = parties.disband(party)
        for (member in former) {
            lobby.markNotInParty(member)
            member.mes("Your party has disbanded.")
            lobby.sendLobbyHud(member)
            lobby.refreshDetails(member)
        }
        for (applicant in applicants) {
            applicant.mes("The party to which you were applying has disbanded.")
            lobby.refreshDetails(applicant)
        }
    }

    private fun ProtectedAccess.memberRow(party: RaidParty<TfaPartySettings>, index: Int): Boolean {
        val target = party.members.getOrNull(index) ?: return true
        if (target === player) {
            if (party.isLeader(player)) disband(party) else leaveMessage(party)
            return false
        }
        if (!party.isLeader(player)) return true
        val left = parties.kick(party, target) ?: return true
        lobby.markNotInParty(target)
        lobby.sendLobbyHud(target)
        lobby.refreshDetails(target)
        lobby.refreshLobbyHud(left.party)
        target.mes("You have been kicked from the party of ${player.displayName}.")
        mes("You have kicked ${target.displayName} from your party.")
        return true
    }

    private fun ProtectedAccess.accept(party: RaidParty<TfaPartySettings>, index: Int) {
        if (!party.isLeader(player)) return
        val target = party.applicants.getOrNull(index) ?: return
        if (party.isFull) {
            mes("Your party is full.")
            return
        }
        if (!parties.accept(party, target)) return
        lobby.markInParty(target)
        lobby.refreshLobbyHud(party)
        mes("You have accepted ${target.displayName} into your party.")
        target.mes("Your application to the party of ${player.displayName} has been accepted.")
    }

    private fun ProtectedAccess.reject(party: RaidParty<TfaPartySettings>, index: Int) {
        if (!party.isLeader(player)) return
        val target = party.applicants.getOrNull(index) ?: return
        if (!parties.decline(party, target)) return
        mes("You have declined the party application from ${target.displayName}.")
        target.mes("Your application to the party of ${player.displayName} has been declined.")
        lobby.refreshDetails(target)
    }

    private fun ProtectedAccess.unblock(party: RaidParty<TfaPartySettings>) {
        if (!party.isLeader(player)) return
        parties.unblockAll(party)
        mes("All players rejected from this party have been unblocked and may apply again.")
    }

    private suspend fun ProtectedAccess.setPreferredSize(party: RaidParty<TfaPartySettings>) {
        if (!party.isLeader(player)) return
        val value = countDialog("Set a preferred party size up to ${TfaPartySettings.MAX_SIZE}:")
        party.settings.preferredSize = value.coerceIn(1, TfaPartySettings.MAX_SIZE)
    }

    private suspend fun ProtectedAccess.setPreferredCompletions(party: RaidParty<TfaPartySettings>) {
        if (!party.isLeader(player)) return
        val value =
            countDialog(
                "Set a preferred number of completions up to ${TfaPartySettings.MAX_COMPLETIONS} " +
                    "(or 0 to clear it):"
            )
        party.settings.preferredCompletions = value.coerceIn(0, TfaPartySettings.MAX_COMPLETIONS)
    }

    private fun ProtectedAccess.toggleMode(party: RaidParty<TfaPartySettings>) {
        if (!party.isLeader(player)) return
        val settings = party.settings
        settings.mode =
            if (settings.mode == TfaPartySettings.NORMAL) TfaPartySettings.PRACTICE
            else TfaPartySettings.NORMAL
    }

    private fun ProtectedAccess.toggleSpectating(party: RaidParty<TfaPartySettings>) {
        if (!party.isLeader(player)) return
        party.settings.spectatingDisabled = !party.settings.spectatingDisabled
    }

    private fun ProtectedAccess.openLobbyHud() {
        ifOpenOverlay(TfaLobby.LOBBY_INTERFACE, LOBBY_TARGET)
        lobby.sync(player)
        lobby.sendLobbyHud(player)
    }

    private fun ProtectedAccess.exitLobby() {
        ifCloseSub(TfaLobby.LOBBY_INTERFACE)
        val party = parties.partyOf(player) ?: return
        if (party.locked || player.pendingLogout || player.loggingOut) return
        if (lobby.leave(player)) mes("You have left the lobby, so you have been removed from your party.")
    }

    private companion object {
        const val LOBBY_AREA = "area.tfa_lobby"
        const val LOBBY_TARGET = "component.toplevel_osrs_stretch:overlay_hud"
        const val LIST_INTERFACE = "interface.tfa_partylist"
        const val LIST_CONTENTS = "component.tfa_partylist:contents"
        const val LIST_ROWS = "component.tfa_partylist:list"

        const val ADD_ROW = "clientscript.tfa_partylist_addline"
        const val ADD_MEMBER = "clientscript.tfa_partydetails_addmember"
        const val ADD_APPLICANT = "clientscript.tfa_partydetails_addapplicant"
        const val MASTER_UPDATE = "clientscript.tfa_partydetails_master"
        const val COMPLETIONS_VARP = "varp.total_completed_thefracturedarchive"
        const val MODAL_TRANSPARENCY = -2

        const val LIST_REFRESH = 0
        const val LIST_MY_PARTY = 1
        const val LIST_FILTER = 2

        const val BACK = 0
        const val UNBLOCK = 2
        const val PREFERRED_SIZE = 3
        const val PREFERRED_COMPLETIONS = 4
        const val PREFERRED_MODE = 5
        const val ACTION = 6
        const val MEMBERS_TAB = 7
        const val APPLICANTS_TAB = 8
        const val SPECTATE_TOGGLE = 9
        const val TAB_MEMBERS = 0
        const val TAB_APPLICANTS = 1
        val MEMBER_ROWS = 10..17
        val ACCEPT_ROWS = 34..40
        val REJECT_ROWS = 41..47
        val TAB_BUTTONS = MEMBERS_TAB..APPLICANTS_TAB
        const val DETAILS_BUTTON_COUNT = 48

        const val NO_LONGER_RECRUITING = "That party is no longer recruiting."

        val SKILLS =
            listOf(
                "stat.attack",
                "stat.strength",
                "stat.ranged",
                "stat.magic",
                "stat.defence",
                "stat.hitpoints",
                "stat.prayer",
            )
    }
}
