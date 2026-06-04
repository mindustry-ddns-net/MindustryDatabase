package net.ddns.mindustry.database.plugin.commands.client.privileged.ui

import arc.util.Log
import mindustry.gen.Player
import net.ddns.mindustry.database.client.PunishmentQueries.Issuer
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.commands.client.BaseClientCommand.Companion.playerHasPermission
import net.ddns.mindustry.database.plugin.currentServer
import net.ddns.mindustry.database.schema.tables.pojos.Account
import java.time.Duration

class PunishmentBuilder(val author: Player) {
    var punishmentType: String? = null // although this isn't optimal, the likelihood of a player sending an invalid
                                        // option is unlikely.
    var target: Account? = null
    var reason: String? = null
    var duration: Duration? = null

    fun permissionCheck(name: String): Account? {
        val permission = database!!.role().findPermission(name)
        if (permission.isEmpty) {
            author.sendMessage("[scarlet]Couldn't find the necessary permission!")
            return null
        }
        val issuer = playerHasPermission(permission.get(), author) ?: return null
        return issuer
    }

    fun execute() {
        val server = currentServer()

        if (listOf(target, reason, punishmentType).contains(null)) {
            author.sendMessage("[scarlet]How did you mess up this badly? Just talk to Lett at this point.")

            Log.err("A required variable is null.")
            Log.err("\tpunishmentType: @", punishmentType)
            Log.err("\ttarget: @", target)
            Log.err("\treason: @", reason)

            return
        }

        when (punishmentType) {
            "warn" -> {
                val issuer = permissionCheck("warn")
                database!!.punishment().warn(target, Issuer.Player(issuer), reason, server)
            }
            "kick" -> {
                val issuer = permissionCheck("kick")
                database!!.punishment().kick(target, Issuer.Player(issuer), reason, server)
            }
            "ban" -> {
                if (duration == null) {
                    author.sendMessage("[scarlet]Duration is null, but it is required.")
                    Log.err("Duration is null.")
                    return
                }

                val issuer = permissionCheck("ban")
                database!!.punishment().ban(target, Issuer.Player(issuer), reason, server, duration)
            }
        }
    }
}