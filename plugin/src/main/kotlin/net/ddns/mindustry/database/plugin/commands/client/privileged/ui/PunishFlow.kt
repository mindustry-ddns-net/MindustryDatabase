package net.ddns.mindustry.database.plugin.commands.client.privileged.ui

import arc.util.Log
import mindustry.gen.Call
import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.PlayerSelect
import net.ddns.mindustry.database.schema.tables.pojos.Account
import net.ddns.mindustry.segment.menuHandler
import net.ddns.mindustry.segment.textInputHandler
import net.ddns.mindustry.segment.ui.Child
import net.ddns.mindustry.segment.ui.menu.BaseMenu
import net.ddns.mindustry.segment.ui.textInput.BaseTextInput
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * The shared interactive "punish a player" flow: action -> player -> reason -> (duration) -> execute.
 *
 * [start] seeds whatever is already known and [proceed] drives the rest, prompting only for the missing pieces:
 *  - `/punish` (no arguments): nothing known -> action menu -> player picker -> reason ...
 *  - `/ban`, `/kick`, `/warn` (no arguments): action known -> player picker -> reason ...
 *  - `/punish <account-name>`: target known (and may be offline) -> action menu -> reason ... (the picker is skipped).
 *
 * The menus and text inputs are registered once, lazily, on first use.
 */
object PunishFlow {

    private val actionOptions = arrayOf(arrayOf("Ban", "Kick", "Warn"))
    private val builders: MutableMap<Player, PunishmentBuilder> = mutableMapOf()

    private val actionMenu: BaseMenu by lazy {
        menuHandler.addMenu("[gold]Punish UI", "Select an action.", actionOptions, ::actionSelected, true)
    }
    private val reasonInput: BaseTextInput by lazy {
        textInputHandler.addTextInput(
            "[gold]Punish UI", "Why are you taking action against this person?", ::reasonGiven, 200, persist = true)
    }
    private val durationInput: BaseTextInput by lazy {
        textInputHandler.addTextInput(
            "[gold]Punish UI", "How long should the ban last? (ex: 365d)", ::gotDuration, 5, persist = true)
    }

    /**
     * Starts the flow for [staff], pre-filling the [presetType] ("ban"/"kick"/"warn") and/or [presetTarget] when the
     * caller already knows them. Anything left null is prompted for interactively.
     */
    fun start(staff: Player, presetType: String? = null, presetTarget: Account? = null) {
        val allowed = if (presetType != null) hasPermission(staff, presetType.lowercase())
                      else actionOptions[0].any { hasPermission(staff, it.lowercase()) }
        if (!allowed) {
            staff.sendMessage("[scarlet]You do not have permission to run this command.")
            return
        }

        builders[staff] = PunishmentBuilder(staff).apply {
            punishmentType = presetType?.lowercase()
            target = presetTarget
        }
        proceed(staff)
    }

    /** Advances the flow, prompting for whichever piece is still missing. */
    private fun proceed(staff: Player) {
        val builder = builders[staff] ?: return

        when {
            builder.punishmentType == null -> actionMenu.show(staff.con())
            builder.target == null -> selectPlayer(staff)
            else -> reasonInput.show(staff.con())
        }
    }

    private fun actionSelected(staff: Player, child: Child) {
        if (child !is BaseMenu || child.option !in actionOptions[0].indices) return

        val type = actionOptions[0][child.option].lowercase()
        if (!hasPermission(staff, type)) {
            actionMenu.show(staff.con())
            return
        }

        val builder = builders[staff] ?: return
        builder.punishmentType = type
        proceed(staff)
    }

    private fun selectPlayer(staff: Player) {
        PlayerSelect.open(staff, title = "[gold]Punish UI", message = "Select a player.") { account ->
            val builder = builders[staff] ?: return@open
            builder.target = account
            proceed(staff)
        }
    }

    private fun reasonGiven(staff: Player, child: Child) {
        val reason = requireText(staff, child, reasonInput, "[yellow]Cannot have an empty reason.") ?: return

        val builder = builders[staff] ?: return
        builder.reason = reason

        if (builder.punishmentType == "ban") {
            durationInput.show(staff.con())
            return
        }

        builder.execute()
        builders.remove(staff)
    }

    private fun gotDuration(staff: Player, child: Child) {
        val text = requireText(staff, child, durationInput, "[yellow]Cannot have an empty duration.") ?: return

        val builder = builders[staff] ?: return
        val duration = try {
            Duration.parse(text)
        } catch (_: IllegalArgumentException) {
            Call.infoMessage(staff.con(), "[scarlet]Must have a valid duration.")
            durationInput.show(staff.con())
            return
        }

        builder.duration = duration.toJavaDuration()
        builder.execute()
        builders.remove(staff)
    }

    /** Reads the submitted text, re-prompting (with [emptyMessage]) when it's blank. Returns null if not yet given. */
    private fun requireText(staff: Player, child: Child, input: BaseTextInput, emptyMessage: String): String? {
        if (child !is BaseTextInput) return null

        val text = child.text
        if (text.isNullOrEmpty()) {
            if (text != null) {
                Call.infoMessage(staff.con(), emptyMessage)
                input.show(staff.con())
            }
            return null
        }
        return text
    }

    /** Quiet permission check (no player messages) so the "any of" gate at entry doesn't spam denials. */
    private fun hasPermission(staff: Player, permissionName: String): Boolean {
        val account = database!!.account().find(staff.ip(), staff.uuid())
        if (account.isEmpty) return false

        val permission = database!!.role().findPermission(permissionName)
        if (permission.isEmpty) {
            Log.err("Couldn't find permission for @.", permissionName)
            return false
        }
        return database!!.role().hasPermissions(account.get(), permission.get())
    }
}
