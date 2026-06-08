package net.ddns.mindustry.database.plugin

import arc.Events
import arc.util.Log
import mindustry.game.EventType.PlayerConnect
import mindustry.game.EventType.PlayerLeave
import mindustry.game.EventType.PlayEvent
import mindustry.game.EventType.TapEvent
import mindustry.game.EventType.WorldLoadEvent
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.Administration
import mindustry.net.Administration.Config
import net.ddns.mindustry.database.client.PunishmentListener
import net.ddns.mindustry.database.client.PunishmentQueries.Issuer
import net.ddns.mindustry.database.client.ServerAccountQueries
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.commands.client.unprivileged.History
import net.ddns.mindustry.database.plugin.configs.PluginConfigs.Configs.configServerIP
import net.ddns.mindustry.database.plugin.events.PlayerLogin
import net.ddns.mindustry.database.plugin.events.ServerExit
import net.ddns.mindustry.database.schema.enums.PunishmentType
import net.ddns.mindustry.database.schema.tables.pojos.Account
import net.ddns.mindustry.database.schema.tables.pojos.Warn
import kotlin.io.encoding.ExperimentalEncodingApi

fun loadMindustryEvents() {
    Events.on(PlayerConnect::class.java) {e -> playerConnect(e)}
    Events.on(PlayerLogin::class.java) {e -> playerLogin(e)}

    Events.on(PlayerLeave::class.java) {e -> playerLeave(e)}
    Events.on(PlayEvent::class.java) {_ -> startHeartbeatScheduler()}
    Events.on(ServerExit::class.java) { _ -> stopHeartbeatScheduler()}

    Events.on(TapEvent::class.java) {e -> tileTapped(e)}
    Events.on(WorldLoadEvent::class.java) {_ -> TileHistoryStore.clear()}
}

private fun tileTapped(event: TapEvent) {
    if (event.tile == null || !History.inspecting.contains(event.player.uuid())) {
        return
    }

    event.player.sendMessage(renderTileHistory(event.tile.x.toInt(), event.tile.y.toInt()))
}

fun loadDatabaseEvents() {
    val server = database!!.server().find(configServerIP.string(), Config.port.num())

    if (server.isEmpty) {
        Log.warn("Not loading database events. Server configuration either corrupt or server not registered in " +
                "database.")
        return
    }

    database!!.listeners().register(PunishmentType.warn, server.get()) { e -> playerWarn(e)}
    database!!.listeners().register(PunishmentType.kick, server.get()) { e -> playerKick(e)}
    database!!.listeners().register(PunishmentType.ban , server.get()) { e -> playerBan(e)}
}

private fun showWarn(warn: Warn, player: Player) {
    val warnString = String.format("[orange]Warning![]\nYou have been warned for:\n%s", warn.reason)
    Call.infoMessage(player.con(), warnString)
    database!!.punishment().markWarnSeen(warn)
}

private fun sharePunishment(action: String, account: Account, issuer: Issuer, server: String) {
    val name = when (issuer) {
        is Issuer.Player -> issuer.account.username
        is Issuer.Console -> "server"
    }

    Call.sendMessage("[scarlet]---------- [white]$action [scarlet]----------[white]\n" +
            "[accent]Issuer[gray]:[white] $name\n" +
            "[accent]Player[gray]:[white] ${account.username}\n" +
            "[accent]Server[gray]:[white] $server")
}

private fun playerWarn(event: PunishmentListener.Event) {
    when (event) {
        is PunishmentListener.Event.Value -> {
            val warn = database!!.punishment().findWarn(event.id()).get()
            val account = database!!.account().find(warn.accountId()).get()

            val issuer = database!!.punishment().findIssuer(warn.issuerId())
            val server = database!!.server().find(warn.serverId())
            sharePunishment("Warn", account, issuer.get(), server.get().name())

            val player = findOnlinePlayer(account.username) ?: return

            showWarn(warn, player)
        }

        is PunishmentListener.Event.Failure -> Log.err(event.exception())
    }
}

private fun playerKick(event: PunishmentListener.Event) {
    when (event) {
        is PunishmentListener.Event.Value -> {
            val kick = database!!.punishment().findKick(event.id()).get()
            val account = database!!.account().find(kick.accountId()).get()

            val issuer = database!!.punishment().findIssuer(kick.issuerId())
            val server = database!!.server().find(kick.serverId)
            sharePunishment("Kick", account, issuer.get(), server.get().name())

            val player = findOnlinePlayer(account.username) ?: return
            Call.kick(player.con(), kick.reason())
//            player.kick(kick.reason())
        }

        is PunishmentListener.Event.Failure -> Log.err(event.exception())
    }
}

private fun playerBan(event: PunishmentListener.Event) {
    when (event) {
        is PunishmentListener.Event.Value -> {
            val ban = database!!.punishment().findBan(event.id()).get()
            val account = database!!.account().find(ban.accountId()).get()

            val issuer = database!!.punishment().findIssuer(ban.issuerId())
            val server = database!!.server().find(ban.serverId)
            sharePunishment("Ban", account, issuer.get(), server.get().name())

            val player = findOnlinePlayer(account.username) ?: return
            Call.infoMessage(player.con(), formatBan(account, ban))
            PlayerName.hide(player)
        }

        is PunishmentListener.Event.Failure -> Log.err(event.exception())
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun playerConnect(event: PlayerConnect) {

    PlayerName.capture(event.player)

    val port = Administration.Config.port.num()
    val server = database!!.server().find(configServerIP.string(), port)

    if (server.isEmpty) {
        Log.err("Server is not in the database.")
        event.player.kick("Invalid database configuration. Please contact a staff member.")
        return
    }

    val status = database!!.serverAccount().joinsServer(server.get(), event.player.name(), event.player.ip(), event.player.uuid())

    when (status) {
        is ServerAccountQueries.JoinStatus.NotAuthenticated -> {
            Call.infoMessage(event.player.con(), "You are not logged in. Please log in using the [gold]/login[]" +
                    " command or signup with the [gold]/signup[] command.")
            event.player.team(Team.derelict)
            PlayerName.hide(event.player)
        }

        is ServerAccountQueries.JoinStatus.AlreadyInServer -> event.player.kick("You're already in one of the servers!", 0)

        is ServerAccountQueries.JoinStatus.NotWhitelisted -> event.player.kick("You're not whitelisted in this server.", 0)

        is ServerAccountQueries.JoinStatus.Joined -> {
            event.player.sendMessage("[gold]Welcome back to the server!")
            Events.fire(PlayerLogin(event.player, status.account))
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun playerLogin(event: PlayerLogin) {
    for (warn in database!!.punishment().unseenWarns(event.account)) {
        showWarn(warn, event.player)
    }

    val bans = database!!.punishment().activeBans(event.account)
    if (bans.isNotEmpty()) {
        val message = formatBan(event.account, bans[0])
        // if the player just joined, then their player object cannot be found, and the message will be null
        // since there are multiple ways for a player to login, there's also a case for when it isn't null
        if (message != null) Call.infoMessage(event.player.con(), message)
        else event.player.sendMessage(String.format("[scarlet]You are banned! Reason: %s", bans[0].reason))
        PlayerName.hide(event.player)
    } else if (PlayerName.isHidden(event.player)) {
        PlayerName.show(event.player)
        Call.sendMessage("[accent]${event.player.plainName()} has connected.")
    }

    applyRoleTag(event.player, event.account)
}

fun applyRoleTag(player: Player, account: Account) {
    val roles = database!!.role().accountRoles(account)
    if (roles.isEmpty()) {
        PlayerName.setTag(player, null)
        return
    }
    roles.sortByDescending { role -> role.priority }
    PlayerName.setTag(player, roles[0].symbol)
}

private fun playerLeave(event: PlayerLeave) {
    val account = database!!.account().find(event.player.ip(), event.player.uuid())

    if (account.isEmpty) {
        Log.warn("A player left but they could not be found in the database. They may not have a session.")
        return
    }
    val port = Administration.Config.port.num()
    val server = database!!.server().find(configServerIP.string(), port)
    database!!.serverAccount().leavesServer(account.get(), server.get())
    PlayerName.forget(event.player)
    pausedPlayers.remove(event.player)    // if a player is banned for an hour while paused, then they could
                                                    // potentially still be paused upon returning after that ban. This
                                                    // would prevent such a scenario.
}
