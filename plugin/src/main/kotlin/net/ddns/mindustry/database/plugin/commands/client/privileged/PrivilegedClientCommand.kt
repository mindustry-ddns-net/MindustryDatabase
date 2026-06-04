package net.ddns.mindustry.database.plugin.commands.client.privileged

import arc.util.CommandHandler
import mindustry.gen.Player
import net.ddns.mindustry.database.client.PunishmentQueries.Issuer
import net.ddns.mindustry.database.plugin.commands.client.BaseClientCommand
import net.ddns.mindustry.database.plugin.commands.client.privileged.ui.PunishFlow
import net.ddns.mindustry.database.plugin.resolveTargetAccount
import net.ddns.mindustry.database.schema.tables.pojos.Account
import net.ddns.mindustry.database.schema.tables.pojos.Permission

sealed class PrivilegedClientCommand(handler: CommandHandler) : BaseClientCommand(handler)

/**
 * The shared front half of the text punishment commands (`/ban`, `/kick`, `/warn`): permission check
 */
fun PrivilegedClientCommand.preparePunishment(
    arguments: Array<String>,
    player: Player,
    permission: Permission,
    type: String,
    expectedArgs: Int,
    usage: String,
): Pair<Issuer, Account>? {
    val issuerAccount = hasPermission(permission, player) ?: return null

    if (arguments.isEmpty()) {
        PunishFlow.start(player, type)
        return null
    }
    if (arguments.size != expectedArgs) {
        player.sendMessage(usage)
        return null
    }

    val target = resolveTargetAccount(arguments[0], player) ?: return null
    return Issuer.Player(issuerAccount) to target
}
