package net.ddns.mindustry.database.plugin.commands.client.privileged

import arc.util.CommandHandler
import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.currentServer
import net.ddns.mindustry.database.schema.tables.pojos.Permission
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class Ban(handler: CommandHandler) : PrivilegedClientCommand(handler) {
    companion object {
        val banPermission: Permission

        init {
            description = "Bans a player by account name, or run with no arguments to pick an online player from a menu."
            parameters = "[account-name] [duration] [reason...]"

            database!!.role().newPermission("ban")
            banPermission = database!!.role().findPermission("ban").get()
        }
    }

    override fun runner(arguments: Array<String>, player: Player) {
        val (issuer, target) = preparePunishment(arguments, player, banPermission, "ban", 3,
            "[scarlet]Usage: /ban <account-name> <duration> <reason...>  (or /ban with no arguments to pick from a menu)") ?: return

        val duration = Duration.parse(arguments[1]).toJavaDuration()
        database!!.punishment().ban(target, issuer, arguments[2], currentServer(), duration)
        player.sendMessage("${target.username} was banned.")
    }
}