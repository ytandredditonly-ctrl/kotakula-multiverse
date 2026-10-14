package com.unciv.logic.kotakula.layers

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Kotakula (6.2): decisions of the AI about the three-layer map.
 */
object LayerAutomation {

    //region Exploration

    /**
     * 6.2: after a layer opens, one scout or the fastest military unit goes to explore it.
     * Called first in the automation of a military unit; `true` if this unit is that explorer and has moved.
     */
    fun tryLayerExplore(unit: MapUnit): Boolean {
        val civ = unit.civ
        if (!civ.gameInfo.hasLayers() || civ.isBarbarian || !unit.isMilitary()) return false
        for (layer in civ.unlockedLayers) {
            if (layer == MapLayer.Surface || chooseExplorer(civ, layer) != unit) continue
            if (unit.getTile().layer == layer) {
                if (com.unciv.logic.automation.unit.UnitAutomation.tryExplore(unit)) return true
                civ.factionState.layersExplored += layer.name // nothing left to see: back to normal duty
                return false
            }
            val entry = nearestEntry(unit, layer) ?: return false
            unit.movement.headTowards(entry)
            return true
        }
        return false
    }

    /** The fastest military unit able to go to [layer] that does not garrison a city */
    private fun chooseExplorer(civ: Civilization, layer: MapLayer): MapUnit? {
        if (layer.name in civ.factionState.layersExplored) return null
        val units = civ.units.getCivUnits()
        units.firstOrNull { it.isMilitary() && it.getTile().layer == layer }?.let { return it }
        return units.filter { it.isMilitary() && !it.isTransported && LayerRules.canUnitBeOnLayer(it, layer) }
            .filter { unit -> unit.getTile().getCity()?.getGarrison() != unit }
            .sortedWith(compareByDescending<MapUnit> { it.getMaxMovement() }.thenBy { it.id })
            .firstOrNull()
    }

    /** The nearest tile of [layer] the unit can step to from a transition or by its own ability */
    private fun nearestEntry(unit: MapUnit, layer: MapLayer): Tile? {
        val start = unit.getTile()
        val candidates = ArrayList<Tile>()
        start.forEachTileInDistance(8) { surface ->
            val target = surface.layerTile(layer) ?: return@forEachTileInDistance
            if (target.isImpassible()) return@forEachTileInDistance
            if (target in LayerRules.layerNeighbors(unit, surface)) candidates += target
        }
        return candidates.sortedBy { it.aerialDistanceTo(start) }.take(5)
            .firstOrNull { unit.movement.canReach(it) }
    }

    //endregion
    //region Workers: transitions near cities

    /**
     * 6.2: workers build a transition within 2 tiles of every city that has no natural transition within 3 tiles.
     * Returns a bonus for the improvement ranking of the worker automation.
     */
    @Readonly
    fun improvementBonus(tile: Tile, improvement: TileImprovement, civ: Civilization): Float {
        if (!tile.tileMap.hasLayers() || !improvement.hasUnique(UniqueType.KMTransition)) return 0f
        val city = tile.getCity() ?: return 0f
        if (city.civ != civ || tile.aerialDistanceTo(city.getCenterTile()) > 2) return 0f
        var hasTransition = false
        city.getCenterTile().forEachTileInDistance(3) { if (LayerRules.transitionsAt(it, civ) != 0) hasTransition = true }
        return if (hasTransition) -2f else 6f
    }

    //endregion
    //region Route builders

    /**
     * 6.2: route builders connect the cities of the civilization through their layer when the way on the Surface is
     * 1.5 times longer than the distance or blocked. Returns `true` if the unit was given something to do.
     */
    fun automateRouteBuilder(unit: MapUnit): Boolean {
        if (!unit.civ.gameInfo.hasLayers() || unit.civ.isHuman() && !unit.isAutomated()) return false
        val routes = LayerRoutes.routeImprovements(unit)
        if (routes.isEmpty()) return false
        val layer = layerOfRoutes(unit) ?: return false
        if (!LayerRules.hasAccess(unit.civ, layer)) return false
        val cities = unit.civ.cities
        if (cities.size < 2) return false

        // Go to the layer first - builders fly or burrow, they change the layer anywhere
        if (unit.getTile().layer != layer) {
            if (unit.getTile().layer == MapLayer.Surface && unit.hasMovement()) LayerRules.changeLayer(unit, layer)
            return unit.getTile().layer == layer || unit.isMoving()
        }
        // Start from the column of a city
        val here = unit.getTile()
        val startCity = cities.firstOrNull { it.location == here.position }
        if (startCity == null) {
            val nearest = cities.minBy { it.getCenterTile().aerialDistanceTo(here) }
            val column = nearest.getCenterTile().layerTile(layer) ?: return false
            unit.movement.headTowards(column)
            return true
        }
        val target = cities.filter { it != startCity }
            .filter { needsLayerConnection(startCity, it, layer) }
            .minByOrNull { it.getCenterTile().aerialDistanceTo(startCity.getCenterTile()) } ?: return false
        val targetTile = target.getCenterTile().layerTile(layer) ?: return false
        LayerRoutes.findRoutePath(unit, targetTile) ?: return false
        LayerRoutes.start(unit, targetTile)
        return true
    }

    @Readonly
    private fun layerOfRoutes(unit: MapUnit): MapLayer? = when {
        unit.cache.isBurrowing -> MapLayer.Underground
        unit.cache.isFlying -> MapLayer.Sky
        else -> null
    }

    /** No route between the city columns yet, and the Surface way is 1.5 times the distance or blocked */
    private fun needsLayerConnection(from: City, to: City, layer: MapLayer): Boolean {
        val start = from.getCenterTile().layerTile(layer) ?: return false
        val end = to.getCenterTile().layerTile(layer) ?: return false
        val bfs = com.unciv.logic.map.BFS(start) { LayerRules.isCityColumn(it) || LayerRules.hasRoute(it) }
        bfs.maxSize = 2000
        bfs.stepUntilDestination(end)
        if (bfs.hasReachedTile(end)) return false
        val distance = from.getCenterTile().aerialDistanceTo(to.getCenterTile())
        val surfacePath = from.getRoadPath(to) ?: return true
        return surfacePath.size > distance * 1.5f
    }

    //endregion
    //region Buildings

    /** 6.2: Bunker / Sky Bastion get a high priority when an enemy of the other faction stands on their layer near the city */
    @Readonly
    fun buildingValue(city: City, building: Building): Float {
        if (!city.getCenterTile().tileMap.hasLayers()) return 0f
        var value = 0f
        for (unique in building.getMatchingUniques(UniqueType.KMCityStrengthVsLayer)) {
            val layer = MapLayer.fromString(unique.params[1]) ?: continue
            if (enemyNearOnLayer(city, layer)) value += 30f
        }
        if (building.hasUnique(UniqueType.KMTransition)) {
            var hasTransition = false
            city.getCenterTile().forEachTileInDistance(3) { if (LayerRules.transitionsAt(it, city.civ) != 0) hasTransition = true }
            if (!hasTransition) value += 4f
        }
        return value
    }

    @Readonly
    private fun enemyNearOnLayer(city: City, layer: MapLayer): Boolean {
        val center = city.getCenterTile().layerTile(layer) ?: return false
        var found = false
        center.forEachTileInDistance(5) { tile ->
            val unit = tile.militaryUnit ?: return@forEachTileInDistance
            if (tile.isVisible(city.civ) && city.civ.isAtWarWith(unit.civ) &&
                com.unciv.logic.kotakula.FactionRules.areOpposing(city.civ, unit.civ)) found = true
        }
        return found
    }

    //endregion
}
