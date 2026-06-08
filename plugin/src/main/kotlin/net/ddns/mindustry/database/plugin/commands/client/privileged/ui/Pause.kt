package net.ddns.mindustry.database.plugin.commands.client.privileged.ui

import arc.util.CommandHandler
import arc.util.Log
import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.PlayerSelect
import net.ddns.mindustry.database.plugin.pausedPlayers
import net.ddns.mindustry.database.schema.tables.pojos.Permission

class Pause(handler: CommandHandler) : PrivilegedUiClientCommand(handler) {
    companion object {
        val permission: Permission
        private const val PERMISSION_NAME = "pause"

        init {
            description = "Pauses/unpauses a player's actions."
            parameters = ""

            database!!.role().newPermission(PERMISSION_NAME)
            permission = database!!.role().findPermission(PERMISSION_NAME).get()
        }
    }

    override fun runner(arguments: Array<String>, player: Player) {
        if (hasPermission(permission, player) == null) { return; }

        PlayerSelect.get(player, message = "Choose a player to pause.") { target: Player ->
            if (pausedPlayers.contains(target)) {
                pausedPlayers.remove(target)
                return@get
            }

            pausedPlayers.add(target)
        };
    }
}