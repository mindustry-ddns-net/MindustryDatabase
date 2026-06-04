package net.ddns.mindustry.database.plugin

import mindustry.gen.Player
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.schema.tables.pojos.Account

/**
 * Looks up the [Account] a command argument refers to by its account name ([login]).
 *
 * This works for offline players
 */
fun resolveTargetAccount(login: String, sender: Player): Account? {
    val account = database!!.account().find(login)
    if (account.isEmpty) {
        sender.sendMessage("[scarlet]Couldn't find an account named \"$login\".")
        return null
    }
    return account.get()
}
