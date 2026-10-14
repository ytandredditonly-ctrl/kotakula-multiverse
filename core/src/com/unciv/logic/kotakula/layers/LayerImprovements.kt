package com.unciv.logic.kotakula.layers

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Kotakula (3.5, 4.2, 4.4, 7.1, 7.2): which improvements may stand on which tiles of the three-layer map,
 * and terraforming.
 */
object LayerImprovements {

    /** 7.1: at most this many terraformed tiles per city of the civilization */
    const val TERRAFORMS_PER_CITY = 3

    /**
     * Layer rules for building [improvement] on [tile]: no improvements in the column of a city center (4.4), great improvements
     * and roads only on the Surface (4.2.6), transitions only on the Surface above/below a passable tile (3.5, 7.2),
     * terraforming next to a passable tile and within the limit of the civilization (7.1).
     */
    @Readonly
    fun mayBuildHere(tile: Tile, improvement: TileImprovement, civ: Civilization?): Boolean {
        val isTransition = improvement.hasUnique(UniqueType.KMTransition)
        if (!tile.isTilemapInitialized() || !tile.tileMap.hasLayers()) return !isTransition
        if (tile.layer != MapLayer.Surface) {
            if (LayerRules.isCityColumn(tile)) return false
            if (improvement.isGreatImprovement() || improvement.isRoad() || isTransition) return false
        }
        if (isTransition) {
            val bits = improvement.getMatchingUniques(UniqueType.KMTransition).fold(0) { acc, unique ->
                acc or when (unique.params[0]) { "Down" -> LayerRules.DOWN; "Up" -> LayerRules.UP; else -> LayerRules.ALL }
            }
            if (bits and LayerRules.DOWN != 0 && !isPassableLayerTile(tile.layerTile(MapLayer.Underground))) return false
            if (bits and LayerRules.UP != 0 && !isPassableLayerTile(tile.layerTile(MapLayer.Sky))) return false
        }
        if (improvement.hasUnique(UniqueType.KMTurnsTileInto)) {
            if (tile.layer == MapLayer.Surface || tile.isCityCenter() || tile.isNaturalWonder()) return false
            if (!hasTerraformNeighbor(tile)) return false
            if (civ != null && !mayTerraformMore(civ)) return false
        }
        return true
    }

    @Readonly
    private fun isPassableLayerTile(tile: Tile?): Boolean = tile != null && !tile.isImpassible()

    /** 7.1: "a passable tile next to it" - in the Sky a Floating Island or Clouds */
    @Readonly
    private fun hasTerraformNeighbor(tile: Tile): Boolean =
        if (tile.layer == MapLayer.Sky)
            tile.neighbors.any { it.baseTerrain == LayerTerrains.floatingIsland || it.baseTerrain == LayerTerrains.clouds }
        else tile.neighbors.any { !it.isImpassible() }

    @Readonly
    fun mayTerraformMore(civ: Civilization): Boolean =
        civ.factionState.layerTerraformedTiles < TERRAFORMS_PER_CITY * civ.cities.size

    /** 7.1: the tile becomes the terrain of [improvement] forever - no resource, no improvement, new biome */
    fun terraform(tile: Tile, improvement: TileImprovement, civ: Civilization?) {
        val unique = improvement.getMatchingUniques(UniqueType.KMTurnsTileInto).firstOrNull() ?: return
        val terrain = tile.ruleset.terrains[unique.params[0]] ?: return
        tile.setImprovementBasic(null)
        tile.tileResource = null
        tile.resourceAmount = 0
        tile.baseTerrain = terrain.name
        tile.setTerrainFeatures(tile.terrainFeatures.filter { tile.ruleset.terrains[it]?.isLayerBiome == true })
        tile.setTerrainTransients()
        LayerBiomes.applyBiome(tile)
        if (civ != null) civ.factionState.layerTerraformedTiles++
        tile.owningCity?.reassignPopulationDeferred()
    }
}
