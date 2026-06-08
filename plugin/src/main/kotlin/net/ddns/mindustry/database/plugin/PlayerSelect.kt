package net.ddns.mindustry.database.plugin

import mindustry.gen.Groups
import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.schema.tables.pojos.Account
import net.ddns.mindustry.segment.menuHandler
import net.ddns.mindustry.segment.ui.Child
import net.ddns.mindustry.segment.ui.menu.BaseMenu

/**
 * A "pick an online player" menu. Only players with a visible name are listed, and [onPick] receives the selected
 * player's resolved [Account] (every caller wants the account, so the lookup lives here).
 */
object PlayerSelect {

    /**
     * Opens a menu to a player showing a list of the currently online players for that server.
     * @param filter Filters the options based on the username of the players.
     * @param onPick A callback that expects a `Player` object as a parameter.
     */
    fun get( // due to some odd behavior with Kotlin, I can't overload the `open(...)` method..
        staff: Player,
        title: String = "[gold]Select a player",
        message: String = "Choose a player.",
        filter: (Player) -> Boolean = { it.plainName().isNotEmpty() },
        onPick: (Player) -> Unit
    ) {
        val targets = buildList { Groups.player.forEach { if (filter(it)) add(it) } }
        if (targets.isEmpty()) {
            staff.sendMessage("[scarlet]No matching players are online.")
            return
        }

        val options = targets.map { arrayOf(it.coloredName()) }.toTypedArray()
        val menu = menuHandler.addMenu(title, message, options, callback@ { _: Player, child: Child ->
            if (child !is BaseMenu) return@callback
            val picked = targets.getOrNull(child.option) ?: return@callback

            onPick(picked)
        }, false)

        menu.show(staff.con())
    }

    /**
     * Opens a menu to a player showing a list of the currently online players for that server.
     * @param filter Filters the options based on the username of the players.
     * @param onPick A callback that expects an `Account` object as a parameter.
     */
    fun open(
        staff: Player,
        title: String = "[gold]Select a player",
        message: String = "Choose a player.",
        filter: (Player) -> Boolean = { it.plainName().isNotEmpty() },
        onPick: (Account) -> Unit,
    ) {
        get(staff, title, message, filter) { chosenPlayer ->
            val account = database!!.account().find(chosenPlayer.ip(), chosenPlayer.uuid())
            if (account.isEmpty) {
                staff.sendMessage("[scarlet]${chosenPlayer.plainName()} isn't logged in, so they have no account.")
                return@get
            }

            onPick(account.get())
        }
    }
}
