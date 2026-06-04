package net.ddns.mindustry.database.plugin

import arc.util.Log
import mindustry.gen.Player
import kotlin.random.Random

/**
 * Stores a player's displayed name.
 *
 * A player's name is composed from four independent inputs:
 *   - [State.id]     a temp two-character id assigned on connect, shown as an `[id]` prefix. A display handle so
 * staff can tell players apart at a glance; it is not typed into commands (the interactive picker covers that).
 *   - [State.base]   the player's real chosen name, captured once on connect.
 *   - [State.tag]    an optional role symbol, shown as a `<symbol>` prefix.
 *   - [State.hidden] whether the name is hidden (unauthenticated or banned).
 */
object PlayerName {

    private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val ID_LENGTH = 2

    private data class State(val id: String, val base: String, var tag: String? = null, var hidden: Boolean = false)

    private val states: MutableMap<String, State> = mutableMapOf()

    private val idIndex: MutableMap<String, String> = mutableMapOf()

    fun capture(player: Player) {
        val uuid = player.uuid()
        if (states.containsKey(uuid)) return

        val id = allocateId()
        states[uuid] = State(id, player.coloredName())
        idIndex[id] = uuid
    }

    fun setTag(player: Player, symbol: String?) {
        val state = state(player) ?: return
        state.tag = symbol
        render(player, state)
    }

    fun hide(player: Player) {
        val state = state(player) ?: return
        state.hidden = true
        render(player, state)
    }

    fun show(player: Player) {
        val state = state(player) ?: return
        state.hidden = false
        render(player, state)
    }

    fun isHidden(player: Player): Boolean = states[player.uuid()]?.hidden ?: false

    fun forget(player: Player) {
        val state = states.remove(player.uuid()) ?: return
        idIndex.remove(state.id)
    }

    private fun state(player: Player): State? {
        val state = states[player.uuid()]
        if (state == null) {
            Log.warn("No tracked name state for @. Did capture() run on connect?", player.uuid())
        }
        return state
    }

    private fun render(player: Player, state: State) {
        if (state.hidden) {
            player.name("")
            return
        }

        val idPrefix = "[white][[${state.id}][]"
        val body = if (state.tag != null) "[accent]<[white]${state.tag}[accent]>[white] ${state.base}"
                   else state.base
        player.name("$idPrefix$body")
    }

    private fun allocateId(): String {
        repeat(64) {
            val candidate = randomId()
            if (!idIndex.containsKey(candidate)) return candidate
        }

        // Fallback: a server would need 1296 concurrent players to reach this.
        for (first in ID_ALPHABET) {
            for (second in ID_ALPHABET) {
                val candidate = "$first$second"
                if (!idIndex.containsKey(candidate)) return candidate
            }
        }

        Log.warn("Ran out of in-game ids; reusing a random one. This should never happen.")
        return randomId()
    }

    private fun randomId(): String =
        buildString { repeat(ID_LENGTH) { append(ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)]) } }
}
