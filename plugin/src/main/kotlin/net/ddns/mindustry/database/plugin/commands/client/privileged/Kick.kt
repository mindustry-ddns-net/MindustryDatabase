package net.ddns.mindustry.database.plugin.commands.client.privileged

import arc.util.CommandHandler
import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.currentServer
import net.ddns.mindustry.database.schema.tables.pojos.Permission

class Kick(handler: CommandHandler) : PrivilegedClientCommand(handler) {
    companion object {
        val permission: Permission
        private const val PERMISSION_NAME = "kick"

        init {
            description = "Kicks a player by account name, or run with no arguments to pick an online player from a menu."
            parameters = "[account-name] [reason...]"

            database!!.role().newPermission(PERMISSION_NAME)
            permission = database!!.role().findPermission(PERMISSION_NAME).get()
        }
    }

    override fun runner(arguments: Array<String>, player: Player) {
        val (issuer, target) = preparePunishment(arguments, player, permission, PERMISSION_NAME, 2,
            "[scarlet]Usage: /kick <account-name> <reason...>  (or /kick with no arguments to pick from a menu)") ?: return

        database!!.punishment().kick(target, issuer, arguments[1], currentServer())
        player.sendMessage("${target.username} was kicked.")
    }
}