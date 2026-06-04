package net.ddns.mindustry.database.plugin.commands.client.privileged.ui

import arc.util.CommandHandler
import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.resolveTargetAccount

/**
 * Opens the interactive punishment menu. The whole flow (action -> player -> reason -> duration -> execute) lives in
 * [PunishFlow], which is shared with the no-argument forms of `/ban`, `/kick` and `/warn`.
 *
 * With no argument it opens the online-player picker; given an account name it acts on that account directly, which
 * also works for offline players.
 */
class Punish(handler: CommandHandler) : PrivilegedUiClientCommand(handler) {
    companion object {
        init {
            description = "Punish a player via an interactive menu. Pass an account name to act on an offline player."
            parameters = "[account-name]"
        }
    }

    override fun runner(arguments: Array<String>, player: Player) {
        if (arguments.isEmpty()) {
            PunishFlow.start(player)
            return
        }

        val target = resolveTargetAccount(arguments[0], player) ?: return
        PunishFlow.start(player, presetTarget = target)
    }
}
