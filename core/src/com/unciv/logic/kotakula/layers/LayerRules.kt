package com.unciv.logic.kotakula.layers

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.kotakula.Faction
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Kotakula: rules of the three-layer map (docs/kotakula/layers.md) that many engine parts ask about.
 *
 * Inert for rulesets and games without layers: every civilization only has the Surface and no tile has a column.
 */
object LayerRules {

    /** Bits of [transitionsAt] */
    const val DOWN = 1
    const val UP = 2
    /** Directly between the Underground and the Sky (Grav Elevator, Axis Mundi, transit nodes - 7.2) */
    const val DIRECT = 4
    const val ALL = DOWN or UP or DIRECT

    /** Does this ruleset have terrains of the Underground or the Sky? */
    @Readonly
    fun isLayerRuleset(ruleset: Ruleset): Boolean = ruleset.hasLayers

    @Readonly
    fun isLayerGame(gameInfo: GameInfo): Boolean = gameInfo.hasLayers()

    //region Access (KL-05, 4.1)

    /** KL-05: recomputes [Civilization.unlockedLayers] from the techs with `KM: Unlocks [layer] layer` */
    fun updateUnlockedLayers(civ: Civilization, notify: Boolean = true) {
        if (!civ.gameInfo.hasLayers()) {
            civ.unlockedLayers = setOf(MapLayer.Surface)
            return
        }
        val newLayers = HashSet<MapLayer>()
        newLayers += MapLayer.Surface
        if (civ.isBarbarian) {
            // 4.1: barbarians may go where any civilization has gone
            for (other in civ.gameInfo.civilizations)
                if (!other.isBarbarian && !other.isSpectator()) newLayers += other.unlockedLayers
        } else {
            for (unique in civ.getMatchingUniques(UniqueType.KMUnlocksLayer))
                MapLayer.fromString(unique.params[0])?.let { newLayers += it }
        }
        val gained = newLayers - civ.unlockedLayers
        civ.unlockedLayers = newLayers
        if (gained.isEmpty() || civ.isBarbarian) return

        // Barbarians follow the first civilization into a layer (KL-22)
        if (civ.gameInfo.civilizations.any { it.isBarbarian })
            updateUnlockedLayers(civ.gameInfo.getBarbarianCivilization(), notify = false)
        if (!notify) return
        for (layer in gained) {
            civ.addNotification(unlockNotification(civ, layer), NotificationCategory.General, layerIcon(layer))
            civ.cache.updateOurTiles() // cities now see and work the layer (KL-18, KL-20)
            for (city in civ.cities) city.reassignPopulationDeferred()
        }
    }

    /** 4.1: the texts of the unlock notifications by faction and layer */
    @Readonly
    fun unlockNotification(civ: Civilization, layer: MapLayer): String = when {
        civ.faction == Faction.Technocrat && layer == MapLayer.Underground -> "We started digging. We do not know when we will stop"
        civ.faction == Faction.Technocrat && layer == MapLayer.Sky -> "We took off. The Humanists have long been up there and already took offence"
        civ.faction == Faction.Humanist && layer == MapLayer.Sky -> "We learned to soar. The Technocrats look up from below and calculate the lift"
        civ.faction == Faction.Humanist && layer == MapLayer.Underground -> "The earth parted. The Technocrats are already there, and they have helmets"
        else -> "The [${layer.name}] layer is open to us"
    }

    @Readonly fun layerIcon(layer: MapLayer) = "LayerIcons/${layer.name}"

    /** KL-05: has [civ] access to [layer]? */
    @Readonly
    fun hasAccess(civ: Civilization, layer: MapLayer): Boolean =
        layer == MapLayer.Surface || layer in civ.unlockedLayers

    /** Has any civilization (not the barbarians) opened [layer]? Used by KL-21, KL-22 */
    @Readonly
    fun isOpenedByAnyone(gameInfo: GameInfo, layer: MapLayer): Boolean =
        layer == MapLayer.Surface || gameInfo.civilizations.any { !it.isBarbarian && layer in it.unlockedLayers }

    /** The tech that opens [layer] for [civ] - for the "Opens with [tech]" hint of the layer switch (6.1) */
    @Readonly
    fun unlockingTech(civ: Civilization, layer: MapLayer): String? =
        civ.gameInfo.ruleset.technologies.values.firstOrNull { tech ->
            com.unciv.logic.kotakula.FactionRules.canUse(civ, tech) &&
                tech.getMatchingUniques(UniqueType.KMUnlocksLayer).any { it.params[0] == layer.name }
        }?.name

    /**
     * KL-15, 4.2-4.3: may [unit] stand on [layer] at all?
     * Ships and aircraft stay on the Surface, flying units never go underground, burrowing units never fly.
     */
    @Readonly
    fun canUnitBeOnLayer(unit: MapUnit, layer: MapLayer): Boolean {
        if (layer == MapLayer.Surface) return true
        if (!hasAccess(unit.civ, layer)) return false
        val baseUnit = unit.baseUnit
        if (baseUnit.isWaterUnit || baseUnit.isAirUnit()) return false
        if (layer == MapLayer.Underground && unit.cache.isFlying) return false
        if (layer == MapLayer.Sky && unit.cache.isBurrowing) return false
        return true
    }

    /** Can [unit] ever leave the Surface? Shortcut for the path finding */
    @Readonly
    fun mayUseLayers(unit: MapUnit): Boolean =
        unit.civ.unlockedLayers.size > 1 && !unit.baseUnit.isWaterUnit && !unit.baseUnit.isAirUnit()

    //endregion
    //region Transitions (KL-13, 4.2, 7.2)

    @Readonly
    private fun directionBits(direction: String): Int = when (direction) {
        "Down" -> DOWN
        "Up" -> UP
        "All" -> ALL
        else -> 0
    }

    /**
     * Transitions of the column of [tile] usable by units of [civ]: natural features and improvements of the surface tile,
     * and transition buildings of a city on it (only for the owner and its friends, 7.2). Bits [DOWN], [UP], [DIRECT].
     */
    @Readonly
    fun transitionsAt(tile: Tile, civ: Civilization?): Int {
        val surface = tile.surfaceTile()
        var bits = 0
        for (unique in surface.getTerrainMatchingUniques(UniqueType.KMTransition))
            bits = bits or directionBits(unique.params[0])
        val improvement = surface.getUnpillagedTileImprovement() // 4.2.4: pillaged transitions do not work
        if (improvement != null)
            for (unique in improvement.getMatchingUniques(UniqueType.KMTransition))
                bits = bits or directionBits(unique.params[0])
        if (surface.isCityCenter()) {
            val city = surface.getCity()!!
            if (civ == null || city.civ == civ || surface.isFriendlyTerritory(civ))
                bits = bits or cityTransitions(city)
        }
        return bits
    }

    /** 7.2: transitions the buildings of [city] give to its center column */
    @Readonly
    fun cityTransitions(city: City): Int {
        var bits = 0
        for (unique in city.cityConstructions.builtBuildingUniqueMap.getMatchingUniques(UniqueType.KMTransition, city.state))
            bits = bits or directionBits(unique.params[0])
        return bits
    }

    /** Is the column of [tile] a transition for anyone (for the transition marker, 6.1)? */
    @Readonly
    fun isTransition(tile: Tile): Boolean = transitionsAt(tile, null) != 0

    /** Movement points a unit gains when changing the layer in a city with a Grav Elevator or Axis Mundi (7.2) */
    @Readonly
    fun transitMovementBonus(tile: Tile, civ: Civilization): Int {
        val surface = tile.surfaceTile()
        if (!surface.isCityCenter()) return 0
        val city = surface.getCity()!!
        if (city.civ != civ && !surface.isFriendlyTerritory(civ)) return 0
        return city.cityConstructions.builtBuildingUniqueMap.getMatchingUniques(UniqueType.KMLayerTransitMovement, city.state)
            .sumOf { it.params[0].toInt() }
    }

    /**
     * KL-15, KL-16: tiles of other layers [unit] can step to from [from] by changing the layer - the extra edges of the movement graph.
     * Does not check whether the target can be entered; the path finding does that with canPassThrough.
     */
    @Readonly
    fun layerNeighbors(unit: MapUnit, from: Tile): List<Tile> {
        if (!mayUseLayers(unit)) return emptyList()
        if (!from.tileMap.hasLayers()) return emptyList()
        val transitions = transitionsAt(from, unit.civ)
        val surface = from.surfaceTile()
        val flying = unit.cache.isFlying
        val burrowing = unit.cache.isBurrowing
        val toUnderground = transitions and DOWN != 0 || burrowing && surface.isLand
        val toSky = transitions and UP != 0 || flying
        val direct = transitions and DIRECT != 0
        val first: MapLayer?
        val second: MapLayer?
        when (from.layer) {
            MapLayer.Surface -> {
                first = if (toUnderground) MapLayer.Underground else null
                second = if (toSky) MapLayer.Sky else null
            }
            MapLayer.Underground -> {
                first = if (toUnderground) MapLayer.Surface else null
                second = if (direct) MapLayer.Sky else null
            }
            MapLayer.Sky -> {
                first = if (toSky) MapLayer.Surface else null
                second = if (direct) MapLayer.Underground else null
            }
        }
        val firstTile = if (first != null && canUnitBeOnLayer(unit, first)) from.layerTile(first) else null
        val secondTile = if (second != null && canUnitBeOnLayer(unit, second)) from.layerTile(second) else null
        return when {
            firstTile != null && secondTile != null -> listOf(firstTile, secondTile)
            firstTile != null -> listOf(firstTile)
            secondTile != null -> listOf(secondTile)
            else -> emptyList()
        }
    }

    /** Why [unit] cannot change to [layer] here right now, `null` if it can (KL-06, 4.2, L4) */
    @Readonly
    fun cannotChangeLayerReason(unit: MapUnit, layer: MapLayer): String? {
        val from = unit.getTile()
        if (from.layer == layer) return "Already there"
        val target = from.layerTile(layer) ?: return "No such layer"
        if (!hasAccess(unit.civ, layer)) return "Layer is not unlocked"
        if (!canUnitBeOnLayer(unit, layer)) return "The unit cannot go there"
        if (target !in layerNeighbors(unit, from)) return "No transition here"
        if (!unit.hasMovement()) return "No movement left"
        if (!unit.movement.canMoveTo(target)) return "The tile is blocked"
        return null
    }

    /** KL-06: changes the layer of [unit] to [layer] for 1 movement point (all of them if less is left, 4.2.3) */
    fun changeLayer(unit: MapUnit, layer: MapLayer): Boolean {
        if (cannotChangeLayerReason(unit, layer) != null) return false
        val from = unit.getTile()
        val target = from.layerTile(layer)!!
        unit.movement.moveToTile(target)
        return unit.getTile() == target
    }

    //endregion
    //region Tiles

    /** KL-07: one of the two tiles above and below a city center (`isCityColumn`) */
    @Readonly
    fun isCityColumn(tile: Tile): Boolean =
        tile.layer != MapLayer.Surface && tile.getCity()?.location == tile.position

    /** A city center or a tile of its column - attacked, entered and captured like the city (4.4) */
    @Readonly
    fun isCityColumnOrCenter(tile: Tile): Boolean = tile.isCityCenter() || isCityColumn(tile)

    /** The city whose center column [tile] belongs to, if any */
    @Readonly
    fun cityOfColumn(tile: Tile): City? = if (isCityColumnOrCenter(tile)) tile.getCity() else null

    /** Is [tile] a layer route (Tunnel, Magic Passage, Air Platform, Cloud Bridge - 3.5, 7.1)? */
    /** KL-20, 4.4: may [city] put a citizen on [tile]? Surface tiles as usual; layer tiles if the layer is open,
     *  the tile is passable and not in the column of the center */
    @Readonly
    fun isWorkable(city: City, tile: Tile): Boolean {
        if (tile.layer == MapLayer.Surface) return true
        if (!hasAccess(city.civ, tile.layer)) return false
        if (tile.position == city.location) return false
        return !tile.isImpassible()
    }

    @Readonly
    fun hasRoute(tile: Tile): Boolean =
        tile.layer != MapLayer.Surface && tile.getUnpillagedTileImprovement()?.hasUnique(UniqueType.KMMakesTilePassable) == true

    /** 5.3.5: the name of a barbarian camp on [layer] - a text substitution, the camp works as in vanilla */
    @Readonly
    fun layerCampName(improvement: String, layer: MapLayer, ruleset: com.unciv.models.ruleset.Ruleset): String {
        if (ruleset.tileImprovements[improvement]?.isBarbarianCampEquivalent() != true) return improvement
        return when (layer) {
            MapLayer.Underground -> "Goblin Lair"
            MapLayer.Sky -> "Harpy Nest"
            MapLayer.Surface -> improvement
        }
    }

    /** Kotakula (6.1): "Layer: [..]" line of the Civilopedia for objects found or built on layer terrains */
    fun civilopediaLayerLines(terrains: Collection<String>, ruleset: com.unciv.models.ruleset.Ruleset): List<com.unciv.ui.screens.civilopediascreen.FormattedLine> {
        if (!ruleset.hasLayers) return emptyList()
        val layers = terrains.mapNotNull { ruleset.terrains[it]?.layer }.filter { it != "Surface" }.distinct()
        return layers.map { com.unciv.ui.screens.civilopediascreen.FormattedLine("Layer: [$it]", link = "Layers/Three-layer map") }
    }

    //endregion
}
