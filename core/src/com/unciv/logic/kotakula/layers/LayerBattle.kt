package com.unciv.logic.kotakula.layers

import com.unciv.logic.city.City
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.UnitMovementMemoryType
import yairm210.purity.annotations.Readonly

/**
 * Kotakula (KL-19, 4.4, 4.5): combat between the layers of the three-layer map.
 * Combat happens within one layer; the exceptions are aircraft (Surface and Sky), nukes (whole columns) and cities (all layers).
 */
object LayerBattle {

    /** How far a unit of the old owner may be pushed out of a conquered column before it is lost */
    private const val EVICTION_SEARCH_RADIUS = 6

    /**
     * 4.4, L6: after a city is conquered from [enteredTile], the units of the old owner on the other tiles of the center column
     * move to the nearest tile of the old owner on the same layer - or die if there is none. Aircraft are lost like in vanilla.
     */
    fun evictFromColumn(city: City, enteredTile: Tile) {
        val oldOwner = city.civ
        for (tile in city.getCenterTile().column()) {
            if (tile == enteredTile) continue
            for (unit in tile.getUnits().filter { it.civ == oldOwner }.toList()) {
                if (unit.baseUnit.isAirUnit()) {
                    unit.destroy()
                    continue
                }
                val target = nearestOwnTile(unit, city)
                if (target == null) {
                    unit.destroy()
                    continue
                }
                unit.removeFromTile()
                unit.putInTile(target)
                unit.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported
            }
        }
    }

    private fun nearestOwnTile(unit: MapUnit, conqueredCity: City): Tile? {
        val start = unit.getTile()
        val bfs = BFS(start) { unit.movement.canPassThrough(it) || it == start }
        bfs.maxSize = 400
        var best: Tile? = null
        var bestDistance = Int.MAX_VALUE
        bfs.stepToEnd()
        for (tile in bfs.getReachedTiles()) {
            if (tile == start || tile.getOwner() != unit.civ || tile.getCity() == conqueredCity) continue
            if (!unit.movement.canMoveTo(tile)) continue
            val distance = tile.aerialDistanceTo(start)
            if (distance > EVICTION_SEARCH_RADIUS || distance >= bestDistance) continue
            best = tile
            bestDistance = distance
        }
        return best
    }

    /** 4.4: a city bombards targets in range on every layer its owner has opened */
    @Readonly
    fun bombardRangeTiles(city: City, range: Int): Sequence<Tile> {
        val center = city.getCenterTile()
        @Suppress("DEPRECATION")
        val surfaceTiles = center.getTilesInDistance(range)
        if (!center.tileMap.hasLayers()) return surfaceTiles
        return surfaceTiles.flatMap { surface ->
            surface.column().asSequence().filter { LayerRules.hasAccess(city.civ, it.layer) && it.position != city.location || it == surface }
        }
    }

    /** 4.3, 4.5, L7: aircraft hit the Surface and the Sky in their range, never the Underground */
    @Readonly
    fun airTargetTiles(surfaceTilesInRange: Sequence<Tile>): Sequence<Tile> =
        surfaceTilesInRange.flatMap { tile ->
            val sky = tile.layerTile(MapLayer.Sky)
            if (sky == null) sequenceOf(tile) else sequenceOf(tile, sky)
        }
}
