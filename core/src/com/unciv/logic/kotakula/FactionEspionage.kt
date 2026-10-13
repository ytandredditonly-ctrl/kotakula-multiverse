package com.unciv.logic.kotakula

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import kotlin.math.ceil
import kotlin.random.Random

/**
 * KC-13: faction espionage (section 2.7). Not the Unciv spy system - spies of vanilla Unciv are not used.
 *
 * Level 1 (era 4 tech, `KM: Reveals [25]% of opposing faction military units`): at the start of each turn a share
 * of every opposing civilization's military units is revealed until our next turn starts. Rounded up, at least
 * one unit if there are any. Units closest to our cities are picked first, ties broken randomly
 * (seed `gameSeed + turn + civIndex`). Civilians and units in cities are never revealed.
 *
 * Level 2 (era 7 tech): 50%, plus `KM: Reveals all opposing faction units within [6] tiles of your cities`.
 *
 * Bonus sources (`KM: Reveals [+10]% opposing faction military units`) add percentage points;
 * several level sources do not stack (maximum counts); the total share is capped at 75%.
 *
 * Revealing a unit shows the unit only, not the tiles around it.
 */
object FactionEspionage {

    /** The share of opposing military units [civ] reveals each turn, in percent (0 = no espionage) */
    @Readonly
    fun getRevealPercent(civ: Civilization): Int {
        var base = 0
        civ.forEachMatchingUnique(UniqueType.KMRevealsPercentOpposingUnits) { base = maxOf(base, it.params[0].toInt()) }
        if (base <= 0) return 0
        var bonus = 0
        civ.forEachMatchingUnique(UniqueType.KMRevealsExtraPercentOpposingUnits) { bonus += it.params[0].toInt() }
        return (base + bonus).coerceIn(0, FactionRules.MAX_REVEAL_PERCENT)
    }

    /** Radius around our cities in which all opposing units are always visible (0 = none) */
    @Readonly
    fun getAlwaysVisibleRadius(civ: Civilization): Int {
        var radius = 0
        civ.forEachMatchingUnique(UniqueType.KMRevealsOpposingUnitsNearCities) { radius = maxOf(radius, it.params[0].toInt()) }
        return radius
    }

    /** How many of [militaryUnitCount] units a [percent] share reveals: rounded up, at least 1 if there are any */
    @Readonly
    fun unitsToReveal(militaryUnitCount: Int, percent: Int): Int {
        if (militaryUnitCount <= 0 || percent <= 0) return 0
        return ceil(militaryUnitCount * percent / 100.0).toInt().coerceIn(1, militaryUnitCount)
    }

    /** Can this unit be revealed by espionage at all? */
    @Readonly
    private fun isRevealable(unit: MapUnit): Boolean = unit.isMilitary() && !unit.getTile().isCityCenter()

    /** Start of [civ]'s turn: pick the revealed units for this turn */
    fun onTurnStart(civ: Civilization) {
        val state = civ.factionState
        state.revealedUnitIds.clear()
        nearCityTilesCache.remove(civ)
        if (!civ.faction.isReal()) return
        val percent = getRevealPercent(civ)
        if (percent <= 0) return

        val gameInfo = civ.gameInfo
        val civIndex = gameInfo.civilizations.indexOf(civ)
        val random = Random(gameInfo.tileMap.mapParameters.seed + gameInfo.turns + civIndex)
        val ourCityTiles = civ.cities.map { it.getCenterTile() }

        for (enemy in gameInfo.civilizations) {
            if (!FactionRules.areOpposing(civ, enemy) || enemy.isDefeated()) continue
            val candidates = enemy.units.getCivUnits().filter { isRevealable(it) }.toList()
            val count = unitsToReveal(candidates.size, percent)
            if (count == 0) continue
            // Random tie-breakers first, then a stable sort by distance keeps them for equal distances
            val shuffled = candidates.shuffled(random)
            val chosen = shuffled.sortedBy { unit -> distanceToNearest(unit.getTile(), ourCityTiles) }.take(count)
            for (unit in chosen) state.revealedUnitIds.add(unit.id.toString())
        }
    }

    @Readonly
    private fun distanceToNearest(tile: Tile, cityTiles: List<Tile>): Int =
        cityTiles.minOfOrNull { it.aerialDistanceTo(tile) } ?: Int.MAX_VALUE

    /** Is [unit] shown to [viewer] by espionage, although [viewer] does not see its tile? */
    @Readonly
    fun isRevealedTo(unit: MapUnit, viewer: Civilization): Boolean {
        if (!viewer.faction.isReal() || !FactionRules.areOpposing(viewer, unit.civ)) return false
        if (unit.id.toString() in viewer.factionState.revealedUnitIds) return true
        val radius = getAlwaysVisibleRadius(viewer)
        if (radius <= 0) return false
        return unit.getTile() in getNearCityTiles(viewer, radius)
    }

    /** Ids of units revealed this turn - for tests and the UI eye marker */
    @Readonly
    fun isRevealedThisTurn(unit: MapUnit, viewer: Civilization): Boolean =
        unit.id.toString() in viewer.factionState.revealedUnitIds

    // Transient cache: tiles within the always-visible radius of a civilization's cities, rebuilt each turn
    private val nearCityTilesCache: MutableMap<Civilization, Pair<Int, Set<Tile>>> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    @Readonly
    @Suppress("purity") // fills a transient cache only
    private fun getNearCityTiles(civ: Civilization, radius: Int): Set<Tile> {
        val key = civ.cities.size * 1000 + radius
        val cached = nearCityTilesCache[civ]
        if (cached != null && cached.first == key) return cached.second
        val tiles = civ.cities.asSequence().flatMap { it.getCenterTile().getTilesInDistance(radius) }.toSet()
        nearCityTilesCache[civ] = key to tiles
        return tiles
    }
}
