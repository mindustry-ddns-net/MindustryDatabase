package net.ddns.mindustry.database.plugin

import arc.util.CommandHandler
import arc.util.Log
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration
import net.ddns.mindustry.database.client.Database
import net.ddns.mindustry.database.client.SecurityConfig
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.commands.BaseCommand
import net.ddns.mindustry.database.plugin.configs.PluginConfigs.Configs.configAccountLimit
import net.ddns.mindustry.database.plugin.configs.PluginConfigs.Configs.configServerIP
import net.ddns.mindustry.database.plugin.configs.ReloadableConfig.Configs.reloadConfigs
import net.ddns.mindustry.database.plugin.configs.toml.databaseInfo
import net.ddns.mindustry.database.schema.tables.pojos.Account
import net.ddns.mindustry.database.schema.tables.pojos.Ban
import net.ddns.mindustry.database.schema.tables.pojos.Server
import java.security.NoSuchAlgorithmException
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Makes and returns a new `Database` object.
 * @return `Database`
 */
fun newDatabase(): Database? {
    val database: Database
    val securityConfig: SecurityConfig

    try {
        val securityConfigBuilder = SecurityConfig.Builder.create()
            .saltLength(16)
            .hashLength(128)
            .argon2Iteration(10)
            .argon2Memory(20)
            .argon2Parallelism(2)
            .accountLimit(configAccountLimit.num())

        securityConfig = securityConfigBuilder.build()
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException(e)
    }

    try {
        database = Database.newConnection(
            "jdbc:postgresql://" + databaseInfo!!.url + "/mindustry_database",
            databaseInfo!!.username, databaseInfo!!.password, securityConfig
        )
    } catch (e: Exception) {
        Log.debug(e)
        Log.warn("Ensure that the URL, the user, and the user's password is correct.")
        return null
    }

    return database
}

/**
 * Restarts anything that is dependent upon the IP and port configurations of the server. This is always ran at
 * plugin initialization.
 */
fun restartConfigDependentFeatures() {
    reloadConfigs()
    database = newDatabase()

    if (database == null) {
        Log.warn("Database connection cannot be established.")
        Log.warn("Skipping configuration dependent features since database is null. If the configurations are" +
                    " correct, then reload the configurations."
        )
        return
    }

    restartHeartbeatScheduler()
}

fun registerCommands(commandList: List<KClass<out BaseCommand>>, handler: CommandHandler) {
//    if (database == null) { return }

    for (command in commandList) {
        command.primaryConstructor!!.call(handler)
    }
}

/** The server this plugin instance is registered as, resolved from the configured IP and port. */
fun currentServer(): Server =
    database!!.server().find(configServerIP.string(), Administration.Config.port.num()).get()

fun findOnlinePlayer(username: String): Player? {
    val result = Groups.player.find {player -> comparePlayer(username, player)}
    return result
}

fun formatBan(account: Account, ban: Ban): String? {
    val player = findOnlinePlayer(account.username) ?: return null

    return String.format("[scarlet]You've been banned!\n" +
            "[accent]Reason: [white]%s\n" +
            "[accent]Duration: [white]%s", ban.reason(), ban.expirationDate.format(DateTimeFormatter.ISO_DATE))
}

private fun comparePlayer(username: String, player: Player): Boolean {
    val account = database!!.account().find(player.ip(), player.uuid())
    if (account.isEmpty) return false
    return account.get().username() == username
}

//fun kickForBan(player: Player, account: Account) {
//    val ban = database!!.punishment().findBan(account)
//
//    if (ban.isEmpty) {
//        Log.err("Couldn't find ban for account @.", account.username)
//        return;
//    }
//
//    val banExpiration = ban.get().expirationDate
//    val reason = ban.get().reason
//    val until = String.format("%d-%d-%d (DD-MM-YYYY)", banExpiration.dayOfMonth, banExpiration.monthValue,
//        banExpiration.year)
//    val banTemplate = "Reason: $reason\n\nUntil: $until"
//
//    player.kick(banTemplate)
//}
