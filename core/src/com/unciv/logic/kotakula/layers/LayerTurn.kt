package com.unciv.logic.kotakula.layers

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.TileMap
import yairm210.purity.annotations.Readonly

/**
 * Kotakula: per-turn processing of the three-layer map - the entry points from the turn loop.
 */
object LayerTurn {

    /** Turns a Mine must stand above Solid Rock to turn it into a Cave (7.3) */
    const val MINE_TURNS_TO_CAVE = 20

    /**
     * KL-21: a layer nobody has opened and nobody stands on is not processed at the end of the turn.
     */
    fun isLayerActive(gameInfo: GameInfo, layer: MapLayer): Boolean {
        if (layer == MapLayer.Surface) return true
        if (gameInfo.civilizations.any { it.isMajorCiv() && layer in it.unlockedLayers }) return true
        return gameInfo.civilizations.any { civ -> civ.units.getCivUnits().any { it.hasTile() && it.getTile().layer == layer } }
    }

    /** Once per game turn, after all civilizations have moved */
    fun onNewGameTurn(gameInfo: GameInfo) {
        if (!gameInfo.hasLayers()) return
        LayerBiomes.onNewGameTurn(gameInfo)
        processMines(gameInfo)
    }

    /** 7.3: a Mine standing 20 turns turns the Solid Rock below into a Cave, if the owner has the Underground */
    private fun processMines(gameInfo: GameInfo) {
        val ruleset = gameInfo.ruleset
        if (!ruleset.terrains.containsKey(LayerTerrains.cave)) return
        for (tile in gameInfo.tileMap.values) {
            if (tile.getUnpillagedImprovement() != "Mine") {
                tile.layerMineTurns = 0
                continue
            }
            if (tile.layerMineTurns < MINE_TURNS_TO_CAVE) tile.layerMineTurns++
            if (tile.layerMineTurns < MINE_TURNS_TO_CAVE) continue
            val below = tile.layerTile(MapLayer.Underground) ?: continue
            if (below.baseTerrain != LayerTerrains.solidRock || below.isNaturalWonder()) continue
            val owner = tile.getOwner() ?: continue
            if (!LayerRules.hasAccess(owner, MapLayer.Underground)) continue
            below.baseTerrain = LayerTerrains.cave
            below.setTerrainTransients()
            below.removeImprovement()
            LayerBiomes.applyBiome(below)
            owner.addNotification("The mine of [${tile.getCity()!!.name}] broke through into a cave",
                below.layerKey, NotificationCategory.General, NotificationIcon.Construction)
        }
    }

    /** Start of a civilization's turn */
    fun onTurnStart(civ: Civilization) {
        if (!civ.gameInfo.hasLayers()) return
        LayerRules.updateUnlockedLayers(civ)
    }

    /** All tiles of [map] that units of the layer see - helper for the barbarians (KL-22) */
    @Readonly
    fun layerMaps(gameInfo: GameInfo): List<TileMap> = MapLayer.extraLayers.mapNotNull { gameInfo.map(it) }
}
