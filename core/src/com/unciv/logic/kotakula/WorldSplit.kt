package com.unciv.logic.kotakula

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/**
 * Kotakula Multiverse game setting "World split": the Technocrats start south of the equator (they dig down first),
 * the Humanists north of it (they rise into the sky first). City-states start anywhere.
 *
 * AI factions are known before the start positions are placed (the lottery of [FactionAssignment]). A human picks
 * the side only on the first turn, so [com.unciv.logic.GameStarter] gives every human a start in a random half and
 * reserves a second start in the other half; [onFactionChosen] moves the starting units there if the choice needs it.
 */
object WorldSplit {

    @Readonly
    fun isEnabled(gameInfo: GameInfo): Boolean =
        FactionRules.isFactionGame(gameInfo) && gameInfo.gameParameters.kotakulaWorldSplit

    /** true - the northern half, false - the southern one, null - anywhere */
    @Pure
    fun isNorthFor(faction: Faction): Boolean? = when (faction) {
        Faction.Humanist -> true
        Faction.Technocrat -> false
        else -> null
    }

    /** The equator itself belongs to neither half */
    @Readonly
    fun isInHalf(tile: Tile, north: Boolean): Boolean = if (north) tile.latitude > 0 else tile.latitude < 0

    /** The faction of [civ] is known now: if its starting units stand in the wrong half, they move to the reserved start */
    fun onFactionChosen(civ: Civilization) {
        val gameInfo = civ.gameInfo
        val alternate = gameInfo.factionGameState.splitAlternateStarts.remove(civ.civID) ?: return
        val north = isNorthFor(civ.faction) ?: return
        // Once a city stands, the civilization stays where it is
        if (civ.cities.isNotEmpty()) return
        val units = civ.units.getCivUnits().toList()
        if (units.isEmpty() || units.any { isInHalf(it.getTile(), north) }) return
        moveUnits(civ, gameInfo.tileMap[alternate])
    }

    private fun moveUnits(civ: Civilization, start: Tile) {
        val units = civ.units.getCivUnits().toList()
        for (unit in units) unit.removeFromTile()
        val candidates = start.getTilesInDistance(5).sortedBy { it.aerialDistanceTo(start) }.toList()
        for (unit in units) {
            val target = candidates.firstOrNull { it.isLand && unit.movement.canMoveTo(it) }
                ?: candidates.firstOrNull { unit.movement.canMoveTo(it) }
                ?: continue
            unit.putInTile(target)
            unit.currentMovement = unit.getMaxMovement().toFloat()
        }
        // Forget the land seen around the first start: only what the units see now is explored
        for (tile in civ.gameInfo.tileMap.values)
            if (tile.isExplored(civ)) tile.setExplored(civ, false)
        civ.exploredRegion = com.unciv.logic.civilization.ExploredRegion()
        civ.exploredRegion.setMapParameters(civ.gameInfo.tileMap.mapParameters)
        civ.cache.updateSightAndResources()
    }

    /** Position of the reserved start in the other half, for the save */
    fun reserveAlternateStart(civ: Civilization, tile: Tile) {
        civ.gameInfo.factionGameState.splitAlternateStarts[civ.civID] = HexCoord(tile.position.x, tile.position.y)
    }
}
