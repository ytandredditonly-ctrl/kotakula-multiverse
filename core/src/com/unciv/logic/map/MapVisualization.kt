package com.unciv.logic.map

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.Readonly

/** Helper class for making decisions about more abstract information that may be displayed on the world map (or fair to use in AI), but which does not have any direct influence on save state, rules, or behaviour. */
class MapVisualization(val gameInfo: GameInfo, val viewingCiv: Civilization) {

    /** @return Whether a unit's past movements should be visible to the player. */
    @Readonly fun isUnitPastVisible(unit: MapUnit): Boolean {
        if (unit.civ == viewingCiv)
            return true
        val checkPositions = sequenceOf(unit.movementMemories.asSequence().map { it.position }, sequenceOf(unit.getTile().layerKey)).flatten()
        return checkPositions.all { gameInfo.getTileByKey(it) in viewingCiv.viewableTiles } // Kotakula: layer keys
                && unit.isVisibleTo(viewingCiv)
        // Past should always be visible for own units. Past should be visible for foreign units if the unit is visible and both its current tile and previous tiles are visible.
    }

    /** @return Whether an attack by a unit to a target should be visible to the player. */
    @Readonly fun isAttackVisible(attacker: Civilization, source: HexCoord, target: HexCoord) = (attacker == viewingCiv || gameInfo.getTileByKey(source) in viewingCiv.viewableTiles || gameInfo.getTileByKey(target) in viewingCiv.viewableTiles)
    // Attacks by the player civ should always be visible, and attacks by foreign civs should be visible if either the tile they targeted or the attacker's tile are visible. E.G. Civ V shows bombers coming out of the Fog of War.
}
