package net.ddns.mindustry.database.plugin.commands.client.unprivileged

import arc.util.CommandHandler
import mindustry.Vars
import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.PlayerSelect
import net.ddns.mindustry.database.plugin.renderPlayerHistory
import net.ddns.mindustry.database.plugin.renderTileHistory
import net.ddns.mindustry.database.plugin.resolveTargetAccount

/**
 * Lets any player inspect what changed and by whom.
 * No arguments : toggles a tap-to-inspect mode for player
 * `<player>` : prints that player's recent actions
 * `<x> <y>` : prints the history of that tile.
 * History is in-memory and resets when the map changes.
 * Made available to everyone so players can identify and votekick griefers.
 */
class History(handler: CommandHandler) : UnprivilegedClientCommand(handler) {
    companion object {
        val inspecting: MutableSet<String> = mutableSetOf()

        init {
            description = "Inspect who changed a tile or what a player did. " +
                    "[lightgray](in-memory, resets on map change)[]\n" +
                    "  [accent]/history[white] - toggle tap-to-inspect, then tap a tile\n" +
                    "  [accent]/history <player>[white] - that player's recent actions (by account name)\n" +
                    "  [accent]/history pick[white] - pick an online player from a menu\n" +
                    "  [accent]/history <x> <y>[white] - history of a single tile"
            parameters = "[player/x] [y]"
        }
    }

    override fun runner(arguments: Array<String>, player: Player) {
        when (arguments.size) {
            0 -> toggleInspect(player)
            1 -> {
                if (arguments[0].equals("pick", ignoreCase = true)) {
                    pickPlayer(player)
                    return
                }
                val target = resolveTargetAccount(arguments[0], player) ?: return
                player.sendMessage(renderPlayerHistory(target.username))
            }
            2 -> showTile(arguments[0], arguments[1], player)
            else -> player.sendMessage("[scarlet]Usage: /history, /history <player>, or /history <x> <y>")
        }
    }

    private fun pickPlayer(player: Player) {
        PlayerSelect.open(player, title = "[gold]Player history", message = "Select a player.") { account ->
            player.sendMessage(renderPlayerHistory(account.username))
        }
    }

    private fun showTile(xArg: String, yArg: String, player: Player) {
        val x = xArg.toIntOrNull()
        val y = yArg.toIntOrNull()
        val tile = if (x == null || y == null) null else Vars.world.tile(x, y)

        if (tile == null) {
            player.sendMessage("[scarlet]Those coordinates are not on the map.")
            return
        }

        player.sendMessage(renderTileHistory(tile.x.toInt(), tile.y.toInt()))
    }

    private fun toggleInspect(player: Player) {
        if (inspecting.remove(player.uuid())) {
            player.sendMessage("[accent]Tile-history inspector [scarlet]off[accent].")
        } else {
            inspecting.add(player.uuid())
            player.sendMessage("[accent]Tile-history inspector [green]on[accent]. Tap a tile to view its history.")
        }
    }
}
