package com.unciv.logic.kotakula.layers

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset

/**
 * Kotakula (KL-03, KL-08, 5.5): the three maps of a world - linking them after loading, creating them for a new game,
 * for a custom map without layers and for a save made before the layers existed.
 */
object LayerWorld {

    /** Measurement switch of the autoplay tool (L12): start games without layers on the layered ruleset */
    @Volatile
    var disabledForMeasurement = false

    /**
     * Called by [GameInfo.setTransients] right after the surface got its transients.
     * @return `true` if the layers were generated now (old save) - the caller notifies the players afterwards
     */
    fun setTransients(gameInfo: GameInfo): Boolean {
        val ruleset = gameInfo.ruleset
        var generated = false
        if (ruleset.hasLayers && !gameInfo.hasLayers() && !disabledForMeasurement) {
            // KL-08: a save without layers gets them, seed = seed of the game
            generateLayers(gameInfo.tileMap, ruleset)
            takeLayersFromSurface(gameInfo)
            generated = true
        }
        val underground = gameInfo.undergroundMap
        val sky = gameInfo.skyMap
        gameInfo.tileMap.linkLayers(underground, sky)
        for (map in listOfNotNull(underground, sky)) {
            map.gameInfo = gameInfo
            map.mapParameters = gameInfo.tileMap.mapParameters
            map.setTransients(ruleset)
        }
        return generated
    }

    /** KL-08: "The Multiverse built the missing floors" for the human players of an old save */
    fun notifyLayersAdded(gameInfo: GameInfo) {
        for (civ in gameInfo.civilizations)
            if (civ.isMajorCiv() && civ.isHuman())
                civ.addNotification("The Multiverse built the missing floors", NotificationCategory.General, LayerRules.layerIcon(MapLayer.Underground))
    }

    /** Moves layers stored in a map file ([TileMap.layerMaps]) into the game fields */
    fun takeLayersFromSurface(gameInfo: GameInfo) {
        val layerMaps = gameInfo.tileMap.layerMaps ?: return
        gameInfo.undergroundMap = layerMaps.firstOrNull { it.layer == MapLayer.Underground }
        gameInfo.skyMap = layerMaps.firstOrNull { it.layer == MapLayer.Sky }
        gameInfo.tileMap.layerMaps = null
    }

    /**
     * Section 5: generates the Underground and the Sky for [surface] (which must have its transients) and stores them in
     * [TileMap.layerMaps] - the form of a map file. Biomes are applied as well.
     */
    fun generateLayers(surface: TileMap, ruleset: Ruleset): LayerMapGenerator.Report {
        val generator = LayerMapGenerator(ruleset, surface)
        val (underground, sky) = generator.generate()
        surface.layerMaps = arrayListOf(underground, sky)
        LayerBiomes.applyAll(surface)
        return generator.report
    }

    /** 5.4: fair starts for the major civilizations, after the starting locations are known */
    fun ensureFairStarts(gameInfo: GameInfo, starts: Collection<Tile>): LayerMapGenerator.Report? {
        if (!gameInfo.hasLayers()) return null
        val generator = LayerMapGenerator(gameInfo.ruleset, gameInfo.tileMap)
        generator.ensureFairStarts(starts)
        LayerBiomes.applyAll(gameInfo.tileMap)
        return generator.report
    }
}
