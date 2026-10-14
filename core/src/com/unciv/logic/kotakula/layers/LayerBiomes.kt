package com.unciv.logic.kotakula.layers

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.LayerBiome
import com.unciv.models.ruleset.Ruleset
import yairm210.purity.annotations.Readonly

/**
 * Kotakula (7.3): biomes - the Surface changes the other layers. A biome is a hidden terrain feature of a layer tile chosen
 * by the Surface tile of its column; it is recomputed at the end of every game turn, and when a city is founded.
 */
object LayerBiomes {

    private const val NEAR_CITY_WITH = "Near city with "

    /** The biome [layerTile] should have now, `null` for none */
    @Readonly
    fun biomeFor(layerTile: Tile, ruleset: Ruleset = layerTile.ruleset): LayerBiome? {
        if (layerTile.layer == MapLayer.Surface) return null
        val surface = layerTile.surfaceTile()
        if (surface === layerTile) return null
        return ruleset.layerBiomes.values.asSequence()
            .filter { it.layer == layerTile.layer.name && matchesSurface(surface, it.surfaceFilter) }
            .maxByOrNull { it.priority }
    }

    /** [LayerBiome.surfaceFilter]: tile filters joined by " or ", `City center`, `Near city with [A] or [B]` */
    @Readonly
    fun matchesSurface(surface: Tile, filter: String): Boolean {
        if (filter.startsWith(NEAR_CITY_WITH)) {
            val buildings = Regex("""\[([^\]]+)]""").findAll(filter).map { it.groupValues[1] }.toList()
            var found = false
            surface.forEachTileInDistance(1) { tile ->
                if (!found && tile.isCityCenter() && buildings.any { tile.getCity()!!.cityConstructions.isBuilt(it) }) found = true
            }
            return found
        }
        return filter.split(" or ").any { part ->
            when (part) {
                "City center" -> surface.isCityCenter()
                else -> surface.matchesFilter(part)
            }
        }
    }

    /** Puts the right biome on [layerTile]; returns the new biome name if it changed (`""` if a biome was removed) */
    fun applyBiome(layerTile: Tile): String? {
        val ruleset = layerTile.ruleset
        val newBiome = biomeFor(layerTile, ruleset)?.name
        val oldBiome = layerTile.terrainFeatures.firstOrNull { ruleset.terrains[it]?.isLayerBiome == true }
        if (newBiome == oldBiome) return null
        val features = layerTile.terrainFeatures.filter { ruleset.terrains[it]?.isLayerBiome != true }.toMutableList()
        if (newBiome != null) features += newBiome
        layerTile.setTerrainFeatures(features)
        return newBiome ?: ""
    }

    /** Biomes of a whole world, e.g. after the generation or after loading a save */
    fun applyAll(surface: TileMap) {
        for (layer in MapLayer.extraLayers)
            for (tile in surface.layerMap(layer)?.values ?: continue) applyBiome(tile)
    }

    /**
     * End of a game turn: recompute the biomes; tell the civilizations that see a changed tile (7.3).
     * Layers nobody has opened and nobody stands on are skipped (KL-21) - their biomes catch up once they are opened.
     */
    fun onNewGameTurn(gameInfo: GameInfo) {
        for (layer in MapLayer.extraLayers) {
            val map = gameInfo.map(layer) ?: continue
            if (!LayerTurn.isLayerActive(gameInfo, layer)) continue
            for (tile in map.values) {
                val changed = applyBiome(tile) ?: continue
                if (changed.isEmpty()) continue
                for (civ in gameInfo.civilizations) {
                    if (!civ.isMajorCiv() || civ.isAI() || !tile.isVisible(civ)) continue
                    civ.addNotification("[${layer.name}] changed under [${tile.surfaceTile().lastTerrain.name}]: now there is [$changed]",
                        tile.layerKey, NotificationCategory.General, LayerRules.layerIcon(layer))
                }
            }
        }
    }

    /** 4.4, 7.3: the column of a new city becomes passable (Cave, Clouds) and gets the Catacombs and the City Haze */
    fun onCityFounded(city: City) {
        val center = city.getCenterTile()
        if (!center.tileMap.hasLayers()) return
        val ruleset = city.getRuleset()
        for (layer in MapLayer.extraLayers) {
            val tile = center.layerTile(layer) ?: continue
            if (tile.isImpassible() || tile.isNaturalWonder()) {
                val passable = if (layer == MapLayer.Underground) LayerTerrains.cave else LayerTerrains.clouds
                if (ruleset.terrains.containsKey(passable)) {
                    tile.naturalWonder = null
                    tile.baseTerrain = passable
                    tile.setTerrainFeatures(tile.terrainFeatures.filter { ruleset.terrains[it]?.isLayerBiome == true })
                    tile.setTerrainTransients()
                }
            }
            tile.removeImprovement()
            applyBiome(tile)
        }
    }
}
