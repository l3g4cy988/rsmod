package org.rsmod.plugins.content.pathfinder

import org.rsmod.game.collision.CollisionMap
import org.rsmod.game.collision.buildFlags
import org.rsmod.game.coroutine.delay
import org.rsmod.game.model.map.Coordinates
import org.rsmod.pathfinder.SmartPathFinder
import org.rsmod.plugins.api.model.mob.player.GameMessage
import org.rsmod.plugins.api.model.mob.player.clearMinimapFlag
import org.rsmod.plugins.api.model.mob.player.sendMessage
import org.rsmod.plugins.api.model.mob.player.sendMinimapFlag
import org.rsmod.plugins.api.protocol.packet.MapMove
import org.rsmod.plugins.api.protocol.packet.ObjectClick

val collision: CollisionMap by inject()

onAction<MapMove> {
    val pf = SmartPathFinder()
    val route = pf.findPath(
        collision.buildFlags(player.coords, pf.searchMapSize),
        player.coords.x,
        player.coords.y,
        destination.x,
        destination.y
    )
    val coordsList = route.map { Coordinates(it.x, it.y, player.coords.level) }
    player.clearQueues()
    player.steps.clear()
    player.steps.addAll(coordsList)
    if (route.alternative && coordsList.isNotEmpty()) {
        val dest = coordsList.last()
        player.sendMinimapFlag(dest.x, dest.y)
    } else if (route.failed) {
        player.clearMinimapFlag()
    }
}

onAction<ObjectClick> {
    val pf = SmartPathFinder()
    val route = pf.findPath(
        clipFlags = collision.buildFlags(player.coords, pf.searchMapSize),
        srcX = player.coords.x,
        srcY = player.coords.y,
        destX = coords.x,
        destY = coords.y,
        destWidth = if (rot == 0 || rot == 2) type.width else type.length,
        destHeight = if (rot == 0 || rot == 2) type.length else type.width,
        objRot = rot,
        objShape = shape,
        accessBitMask = accessBitMask(rot, type.clipMask)
    )
    val coordsList = route.map { Coordinates(it.x, it.y, player.coords.level) }
    player.clearQueues()
    player.steps.clear()
    player.steps.addAll(coordsList)
    if (coordsList.isEmpty()) {
        player.clearMinimapFlag()
        if (route.failed) {
            player.sendMessage(GameMessage.CANNOT_REACH_THAT)
        } else if (!route.alternative) {
            val published = actions.publish(action, type.id)
            if (!published) {
                player.warn { "Unhandled object action: $action" }
            }
        }
        return@onAction
    }
    val destCoords = coordsList.last()
    player.sendMinimapFlag(destCoords.x, destCoords.y)
    player.normalQueue {
        delay()
        var reached = false
        while (true) {
            /*if (approach) {
                // TODO: los check
            }*/
            if (player.coords == destCoords) {
                reached = true
                break
            }
            delay()
        }
        if (!reached) {
            player.sendMessage(GameMessage.CANNOT_REACH_THAT)
            return@normalQueue
        }
        val published = actions.publish(action, type.id)
        if (!published) {
            player.warn { "Unhandled object action: $action" }
        }
    }
}

fun accessBitMask(rot: Int, mask: Int): Int = if (rot == 0) {
    mask
} else {
    ((mask shl rot) and 0xF) + (mask shr (4 - rot))
}
