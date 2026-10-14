package com.unciv.logic.kotakula.layers

import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import java.util.PriorityQueue

/**
 * Kotakula (7.1): the "Lay route to..." command of the route builders (Tunnel Borer, Sky Engineer, Cloud Weaver, Earth Shaper),
 * modelled on Unciv's "Connect road". The builder walks the shortest chain on its layer, counting impassable tiles as passable
 * with the cost of building the route there, and builds the route tile by tile.
 */
object LayerRoutes {

    /** [MapUnit.action] while laying a route; destination and path are kept in the road connection fields of the unit */
    const val ACTION = "Lay route"

    /** The route improvements [unit] may ever build (Tunnel, Magic Passage, Air Platform, Cloud Bridge) */
    @Readonly
    fun routeImprovements(unit: MapUnit): List<TileImprovement> =
        unit.civ.gameInfo.ruleset.tileImprovements.values.filter { improvement ->
            improvement.hasUnique(UniqueType.KMMakesTilePassable) &&
                unit.getMatchingUniques(UniqueType.BuildImprovements).any { it.params[0] == improvement.name }
        }

    /** May [unit] use the command where it stands? */
    @Readonly
    fun canLayRoutes(unit: MapUnit): Boolean =
        unit.getTile().layer != MapLayer.Surface && routeImprovements(unit).isNotEmpty()

    /** The route [unit] can build on [tile] now, if any */
    @Readonly
    fun buildableRoute(unit: MapUnit, tile: Tile): TileImprovement? =
        routeImprovements(unit).firstOrNull { route ->
            route.terrainsCanBeBuiltOn.contains(tile.baseTerrain) &&
                tile.improvementFunctions.canBuildImprovement(route, unit.cache.state)
        }

    /**
     * Cheapest chain from the unit to [destination] on the unit's layer: each step costs 1, plus the build time of the route
     * on tiles without one (8 turns on Solid Rock or Open Sky, 4 on passable tiles - 7.1). `null` if there is none.
     */
    fun findRoutePath(unit: MapUnit, destination: Tile): List<Tile>? {
        val start = unit.getTile()
        if (destination.tileMap !== start.tileMap) return null
        val costs = HashMap<Tile, Int>()
        val parents = HashMap<Tile, Tile>()
        val queue = PriorityQueue<Pair<Tile, Int>>(compareBy { it.second })
        costs[start] = 0
        queue += start to 0
        while (queue.isNotEmpty()) {
            val (tile, cost) = queue.poll()
            if (tile == destination) break
            if (cost > costs[tile]!!) continue
            for (neighbor in tile.neighbors) {
                if (!unit.civ.hasExplored(neighbor)) continue
                val stepCost = stepCost(unit, neighbor) ?: continue
                val newCost = cost + 1 + stepCost
                if (newCost >= (costs[neighbor] ?: Int.MAX_VALUE)) continue
                costs[neighbor] = newCost
                parents[neighbor] = tile
                queue += neighbor to newCost
            }
        }
        if (destination !in costs) return null
        val path = ArrayList<Tile>()
        var current = destination
        while (current != start) {
            path += current
            current = parents[current]!!
        }
        path += start
        return path.reversed()
    }

    /** Extra cost of a tile on the route; `null` if the route cannot go there */
    private fun stepCost(unit: MapUnit, tile: Tile): Int? {
        if (LayerRules.hasRoute(tile)) return 0
        val route = buildableRoute(unit, tile)
        if (route != null) return route.getTurnsToBuild(unit.civ, unit)
        return if (unit.movement.canPassThrough(tile)) 0 else null
    }

    /** Starts the command: the path is shown and confirmed in the UI, then the unit is automated */
    fun start(unit: MapUnit, destination: Tile) {
        val path = findRoutePath(unit, destination) ?: return
        unit.automatedRoadConnectionDestination = destination.layerKey
        unit.automatedRoadConnectionPath = path.map { it.layerKey }
        unit.action = ACTION
        unit.automated = true
        automate(unit)
    }

    /** One turn of the command: build the route here, or walk on along the path to the next tile without it */
    fun automate(unit: MapUnit) {
        val path = unit.automatedRoadConnectionPath?.map { unit.civ.gameInfo.getTileByKey(it) }
        if (path == null) {
            stop(unit)
            return
        }
        val index = path.indexOf(unit.getTile())
        if (index == -1) {
            stop(unit)
            notify(unit, "Lay route cancelled!")
            return
        }
        var current = unit.getTile()
        if (!needsRoute(unit, current)) {
            // Walk on to the furthest reachable tile, stopping at the first one still without a route
            for (next in path.drop(index + 1)) {
                if (!unit.movement.canReachInCurrentTurn(next) || !unit.movement.canMoveTo(next)) break
                unit.movement.moveToTile(next)
                current = unit.getTile()
                if (current != next || needsRoute(unit, next)) break
            }
            if (current == path.last() && !needsRoute(unit, current)) {
                stop(unit)
                notify(unit, "Lay route completed")
                return
            }
        }
        if (!unit.hasMovement() || !needsRoute(unit, current)) return
        val route = buildableRoute(unit, current) ?: return
        if (current.improvementInProgress != route.name)
            current.startWorkingOnImprovement(route, unit.civ, unit)
    }

    @Readonly
    private fun needsRoute(unit: MapUnit, tile: Tile): Boolean = !LayerRules.hasRoute(tile) && buildableRoute(unit, tile) != null

    private fun notify(unit: MapUnit, text: String) =
        unit.civ.addNotification(text, MapUnitAction(unit), NotificationCategory.Units, NotificationIcon.Construction)

    fun stop(unit: MapUnit) {
        unit.automated = false
        unit.action = null
        unit.automatedRoadConnectionDestination = null
        unit.automatedRoadConnectionPath = null
    }
}
