package net.ddns.mindustry.database.plugin

import arc.Core
import arc.math.geom.Point2
import arc.util.I18NBundle
import arc.util.Log
import mindustry.Vars.netServer
import mindustry.content.Blocks
import mindustry.ctype.UnlockableContent
import mindustry.gen.Building
import mindustry.gen.Player
import mindustry.net.Administration
import mindustry.net.Administration.ActionType
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import net.ddns.mindustry.database.plugin.Main.Companion.database
import net.ddns.mindustry.database.plugin.configs.PluginConfigs.Configs.configTileHistoryLimit

val pausedPlayers = mutableSetOf<Player>()

private val recordedActions = setOf(
    ActionType.placeBlock,
    ActionType.breakBlock,
    ActionType.configure,
    ActionType.rotate,
)

// The server didn't retrieve the bundle, so I fetched it manually
private val contentBundle: I18NBundle? by lazy {
    try {
        I18NBundle.createBundle(Core.files.internal("bundles/bundle"))
    } catch (e: Throwable) {
        Log.err("Tile-history: couldn't load locale bundle, falling back to internal block ids.", e)
        null
    }
}

private fun localizedName(content: UnlockableContent): String =
    contentBundle?.get("${content.contentType.name}.${content.name}.name", content.localizedName)
        ?: content.localizedName

fun loadActionFilters() {
    TileHistoryStore.capacity = configTileHistoryLimit.num()
    netServer.admins.actionFilters.add(Administration.ActionFilter(::noBanned))
    netServer.admins.actionFilters.add(Administration.ActionFilter(::recordTileHistory))
    netServer.admins.actionFilters.add(Administration.ActionFilter { action -> return@ActionFilter action.player !in pausedPlayers })
}

// literally 1984...
private fun noBanned(action: Administration.PlayerAction): Boolean {
    val account = database!!.account().find(action.player.ip(), action.player.uuid())

    if (account.isEmpty) {
        return false
    } else if (database!!.punishment().activeBans(account.get()).size != 0) {
        action.player.sendMessage("[scarlet]You are banned!")
        return false
    }

    return true
}

private fun recordTileHistory(action: Administration.PlayerAction): Boolean {
    if (action.type !in recordedActions || action.tile == null) {
        return true
    }

    val account = database?.account()?.find(action.player.ip(), action.player.uuid())
    if (account == null || account.isEmpty) {
        return true
    }

    val build = action.tile.build
    val block = resolveRealBlock(action.block ?: build?.block ?: action.tile.block(), build)
    val size = block?.size ?: 1
    // A placed block's origin is its bottom-left tile: an existing building exposes it directly,
    // otherwise the clicked (centre) tile is shifted by the block's size offset.
    val originX = if (build != null) build.tile.x.toInt() else action.tile.x + (block?.sizeOffset ?: 0)
    val originY = if (build != null) build.tile.y.toInt() else action.tile.y + (block?.sizeOffset ?: 0)

    TileHistoryStore.record(
        TileEvent(
            actorName = account.get().username(),
            accountId = account.get().id(),
            coord = Point2.pack(originX, originY),
            coords = footprintCoords(originX, originY, size),
            action = action.type,
            blockName = block?.let { localizedName(it) },
            configSummary = if (action.type == ActionType.configure) summarizeConfig(action.config) else null,
            rotation = action.rotation,
            epochMillis = System.currentTimeMillis(),
        )
    )

    return true
}

// While a block is deconstructing, the tile holds a ConstructBlock placeholder ("build<size>").
// Resolve it back to the real block so streamed actions report the correct name.
private fun resolveRealBlock(block: Block?, build: Building?): Block? {
    if (block !is ConstructBlock) return block
    val construct = build as? ConstructBlock.ConstructBuild ?: return block
    return sequenceOf(construct.current, construct.previous)
        .firstOrNull { it != null && it != Blocks.air && it !is ConstructBlock } ?: block
}

private fun footprintCoords(originX: Int, originY: Int, size: Int): IntArray {
    val coords = IntArray(size * size)
    var i = 0
    for (dx in 0 until size) {
        for (dy in 0 until size) {
            coords[i++] = Point2.pack(originX + dx, originY + dy)
        }
    }
    return coords
}

private fun summarizeConfig(config: Any?): String? = when (config) {
    null -> null
    is Boolean -> if (config) "on" else "off"
    is UnlockableContent -> localizedName(config)
    is Int -> if (config == -1) "disconnected" else "linked to (${Point2.x(config)}, ${Point2.y(config)})"
    is IntArray -> if (config.isEmpty()) "no links" else "${config.size} links"
    is Array<*> -> if (config.isEmpty()) "no links" else "${config.size} links"
    else -> config.toString()
}
