package com.unciv.kotakula

import com.unciv.Constants
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.Nuke
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.kotakula.Faction
import com.unciv.logic.kotakula.FactionAssignment
import com.unciv.logic.kotakula.layers.LayerMapGenerator
import com.unciv.logic.kotakula.layers.LayerRules
import com.unciv.logic.kotakula.layers.LayerTerrains
import com.unciv.logic.kotakula.layers.LayerWorld
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** docs/kotakula/layers.md, section 6.5: tests L1-L11 of the three-layer map (L12, L13 are measured by the autoplay tool) */
@RunWith(GdxTestRunner::class)
class KotakulaLayerTests {

    private lateinit var game: TestGame

    @Before
    fun setUp() {
        game = TestGame(baseRuleset = KotakulaTestSupport.MOD)
        game.makeHexagonalMap(6, "Plains")
        game.setSpeed("Standard")
    }

    /** Underground and Sky of the test map, filled with [underground] and [sky] */
    private fun addLayers(underground: String = LayerTerrains.cave, sky: String = LayerTerrains.clouds) {
        val surface = game.tileMap
        fun layerMap(base: String, layer: MapLayer) = TileMap(surface.tileList.size).apply {
            this.layer = layer
            mapParameters = surface.mapParameters
            for (tile in surface.tileList) tileList.add(Tile().apply { position = tile.position; baseTerrain = base })
        }
        game.gameInfo.undergroundMap = layerMap(underground, MapLayer.Underground)
        game.gameInfo.skyMap = layerMap(sky, MapLayer.Sky)
        surface.linkLayers(game.gameInfo.undergroundMap, game.gameInfo.skyMap)
        for (map in listOf(game.gameInfo.undergroundMap!!, game.gameInfo.skyMap!!)) {
            map.gameInfo = game.gameInfo
            map.setTransients(game.ruleset)
        }
    }

    private fun addCiv(nation: String, faction: Faction): Civilization {
        val civ = game.addCiv(game.ruleset.nations[nation]!!)
        FactionAssignment.setFaction(civ, faction, notify = false)
        return civ
    }

    private fun tile(x: Int, y: Int, layer: MapLayer = MapLayer.Surface) = game.getTile(x, y).layerTile(layer)!!

    private fun setTerrain(tile: Tile, terrain: String) {
        tile.baseTerrain = terrain
        tile.setTerrainTransients()
    }

    private fun generate(seed: Long, size: MapSize, ruleset: com.unciv.models.ruleset.Ruleset = game.ruleset): TileMap {
        val parameters = MapParameters().apply {
            mapSize = size
            shape = MapShape.hexagonal
            this.seed = seed
        }
        val map = MapGenerator(ruleset).generateMap(parameters)
        map.setTransients(ruleset)
        LayerWorld.generateLayers(map, ruleset)
        return map
    }

    // L1
    @Test
    fun l1_sameSeedGivesSameLayers() {
        val first = generate(4242L, MapSize.Tiny)
        val second = generate(4242L, MapSize.Tiny)
        for (layer in MapLayer.extraLayers) {
            val a = first.layerMaps!!.first { it.layer == layer }
            val b = second.layerMaps!!.first { it.layer == layer }
            Assert.assertEquals(a.tileList.size, b.tileList.size)
            for (i in a.tileList.indices) {
                Assert.assertEquals("$layer tile ${a.tileList[i].position}", a.tileList[i].toString(), b.tileList[i].toString())
            }
        }
        // ...and the layers only hold the terrains of their layer (KL-09)
        for (map in first.layerMaps!!)
            for (tile in map.values)
                Assert.assertEquals("${tile.baseTerrain} on ${map.layer}", map.layer, tile.getBaseTerrain().mapLayer)
    }

    // L2
    @Test
    fun l2_passableShareOverLandIs40To50Percent() {
        val failures = ArrayList<String>()
        for (size in listOf(MapSize.Tiny, MapSize.Small, MapSize.Medium, MapSize.Large, MapSize.Huge)) {
            for (seed in 1L..20L) {
                val map = generate(seed * 1000 + size.radius, size)
                for (layer in MapLayer.extraLayers) {
                    val share = LayerMapGenerator.passableShareOverLand(map, layer)
                    if (share !in 0.40f..0.50f) failures += "${size.name} seed $seed $layer: ${(share * 100).toInt()}%"
                }
            }
        }
        Assert.assertTrue("Passable share over land outside 40-50%: $failures", failures.isEmpty())
    }

    // L3
    @Test
    fun l3_everyStartIsFair() {
        val map = generate(77L, MapSize.Small)
        val starts = map.values.filter { it.isLand && !it.isImpassible() }.shuffled(kotlin.random.Random(3)).take(6)
        val generator = LayerMapGenerator(game.ruleset, map)
        generator.ensureFairStarts(starts)
        for (start in starts) {
            var entrance = false
            var updraft = false
            start.forEachTileInDistance(5) {
                if (LayerTerrains.caveEntrance in it.terrainFeatures) entrance = true
                if (LayerTerrains.updraft in it.terrainFeatures) updraft = true
            }
            Assert.assertTrue("Cave entrance near $start", entrance)
            Assert.assertTrue("Updraft near $start", updraft)
            for (layer in MapLayer.extraLayers) {
                val center = start.layerTile(layer)!!
                val inRadius = ArrayList<Tile>()
                center.forEachTileInDistance(4) { if (!it.isImpassible()) inRadius += it }
                val biggest = inRadius.maxOf { tile ->
                    val bfs = com.unciv.logic.map.BFS(tile) { !it.isImpassible() && it.aerialDistanceTo(center) <= 4 }
                    bfs.stepToEnd()
                    bfs.getReachedTiles().size
                }
                Assert.assertTrue("Passable group of 8+ tiles in $layer near $start, was $biggest", biggest >= 8)
            }
        }
    }

    // L4
    @Test
    fun l4_transitionsNeedTheLayerCostOneMovementAndAreBlockedByEnemies() {
        addLayers()
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        val surfaceTile = tile(0, 0)
        surfaceTile.addTerrainFeature(LayerTerrains.caveEntrance)
        val unit = game.addUnit("Warrior", technocrat, surfaceTile)
        Assert.assertNotNull("No transition without the layer", LayerRules.cannotChangeLayerReason(unit, MapLayer.Underground))
        technocrat.tech.addTechnology("Metallurgy")
        Assert.assertTrue(LayerRules.hasAccess(technocrat, MapLayer.Underground))
        Assert.assertNull(LayerRules.cannotChangeLayerReason(unit, MapLayer.Underground))
        val movementBefore = unit.currentMovement
        Assert.assertTrue(LayerRules.changeLayer(unit, MapLayer.Underground))
        Assert.assertEquals(MapLayer.Underground, unit.getTile().layer)
        Assert.assertEquals("A layer change costs 1 movement", movementBefore - 1f, unit.currentMovement, 0.001f)
        // A tile of the column held by an enemy blocks the transition
        Assert.assertTrue(LayerRules.changeLayer(unit, MapLayer.Surface))
        val humanist = addCiv("Greece", Faction.Humanist)
        technocrat.diplomacyFunctions.makeCivilizationsMeet(humanist)
        humanist.tech.addTechnology("High Elementalism")
        game.addUnit("Warrior", humanist, tile(0, 0, MapLayer.Underground))
        unit.currentMovement = 2f
        Assert.assertTrue(technocrat.isAtWarWith(humanist))
        Assert.assertNotNull("Enemy below blocks", LayerRules.cannotChangeLayerReason(unit, MapLayer.Underground))
    }

    // L5
    @Test
    fun l5_flyingAndBurrowingUnits() {
        addLayers(underground = LayerTerrains.solidRock, sky = LayerTerrains.openSky)
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        technocrat.tech.addTechnology("Metallurgy")
        technocrat.tech.addTechnology("Aeronautics")
        val humanist = addCiv("Greece", Faction.Humanist)
        humanist.tech.addTechnology("Rune Carving")
        humanist.tech.addTechnology("High Elementalism")

        val weaver = game.addUnit("Cloud Weaver", humanist, tile(1, 1))
        Assert.assertTrue("Flying passes Open Sky", weaver.movement.canPassThrough(tile(1, 1, MapLayer.Sky)))
        Assert.assertFalse("Flying never goes underground", weaver.movement.canPassThrough(tile(1, 1, MapLayer.Underground)))
        Assert.assertTrue(LayerRules.changeLayer(weaver, MapLayer.Sky))

        val borer = game.addUnit("Tunnel Borer", technocrat, tile(-1, -1))
        Assert.assertTrue("Burrowing passes Solid Rock", borer.movement.canPassThrough(tile(-1, -1, MapLayer.Underground)))
        Assert.assertFalse("Burrowing never flies", borer.movement.canPassThrough(tile(-1, -1, MapLayer.Sky)))
        Assert.assertTrue(LayerRules.changeLayer(borer, MapLayer.Underground))
        // Ordinary units may not enter Solid Rock or Open Sky
        val warrior = game.addUnit("Warrior", technocrat, tile(2, 2))
        Assert.assertFalse(warrior.movement.canPassThrough(tile(2, 2, MapLayer.Underground)))
    }

    // L6
    @Test
    fun l6_cityIsCapturedFromTheUnderground() {
        addLayers()
        val attackerCiv = addCiv("Babylon", Faction.Technocrat)
        attackerCiv.tech.addTechnology("Metallurgy")
        val defenderCiv = addCiv("Greece", Faction.Humanist)
        defenderCiv.tech.addTechnology("Rune Carving")
        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        val city = game.addCity(defenderCiv, tile(0, 0))
        game.addCity(defenderCiv, tile(4, 4)) // so the old owner has somewhere to go
        city.health = 1
        val skyUnit = game.addUnit("Warrior", defenderCiv, tile(0, 0, MapLayer.Sky))
        val attacker = game.addUnit("Warrior", attackerCiv, tile(1, 0, MapLayer.Underground))
        val target = Battle.getMapCombatantOfTile(tile(0, 0, MapLayer.Underground))
        Assert.assertTrue("The column tile is the city", target is CityCombatant)
        Battle.attack(MapUnitCombatant(attacker), target!!)
        Assert.assertEquals("Captured from below", attackerCiv, city.civ)
        Assert.assertEquals(tile(0, 0, MapLayer.Underground), attacker.getTile())
        Assert.assertTrue("The defender in the Sky was pushed out or died",
            skyUnit.isDestroyed || skyUnit.getTile() != tile(0, 0, MapLayer.Sky))
        if (!skyUnit.isDestroyed) Assert.assertEquals(MapLayer.Sky, skyUnit.getTile().layer)
    }

    // L7
    @Test
    fun l7_aircraftCannotTargetTheUnderground() {
        addLayers()
        val attackerCiv = addCiv("Babylon", Faction.Technocrat)
        attackerCiv.tech.addTechnology("Metallurgy")
        attackerCiv.tech.addTechnology("Aeronautics")
        val defenderCiv = addCiv("Greece", Faction.Humanist)
        defenderCiv.tech.addTechnology("Rune Carving")
        defenderCiv.tech.addTechnology("High Elementalism")
        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        game.addCity(attackerCiv, tile(0, 0))
        val bomber = game.addUnit("Bomber", attackerCiv, tile(0, 0))
        game.addUnit("Warrior", defenderCiv, tile(2, 2, MapLayer.Underground))
        game.addUnit("Warrior", defenderCiv, tile(2, 1, MapLayer.Sky))
        val visible = game.gameInfo.allTiles().toList()
        val targets = TargetHelper.getAttackableEnemies(bomber, bomber.movement.getDistanceToTiles(), visible)
            .map { it.tileToAttack }
        Assert.assertTrue("Sky target in range", tile(2, 1, MapLayer.Sky) in targets)
        Assert.assertFalse("No underground target", targets.any { it.layer == MapLayer.Underground })
    }

    // L8
    @Test
    fun l8_nukeHitsTheColumnsByLayer() {
        addLayers()
        val attackerCiv = addCiv("Babylon", Faction.Technocrat)
        val defenderCiv = addCiv("Greece", Faction.Humanist)
        defenderCiv.tech.addTechnology("Rune Carving")
        defenderCiv.tech.addTechnology("High Elementalism")
        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        game.addCity(attackerCiv, tile(-4, -4))
        // A bomb without a resource requirement: a missing Uranium would halve the damage
        val bombType = game.createBaseUnit("Missile", "Nuclear weapon of Strength [1]", "Self-destructs when attacking", "Blast radius [2]")
        val bomb = game.addUnit(bombType.name, attackerCiv, tile(-4, -4))
        val surface = game.addUnit("Warrior", defenderCiv, tile(2, 2))
        val sky = game.addUnit("Warrior", defenderCiv, tile(2, 2, MapLayer.Sky))
        val underground = game.addUnit("Warrior", defenderCiv, tile(2, 2, MapLayer.Underground))
        Nuke.NUKE(MapUnitCombatant(bomb), tile(2, 2))
        Assert.assertTrue("Surface: full damage", surface.isDestroyed)
        Assert.assertTrue("Sky: full damage", sky.isDestroyed)
        Assert.assertFalse("Underground: 50% damage", underground.isDestroyed)
        Assert.assertEquals(50, underground.health)
        Assert.assertFalse("No fallout underground", "Fallout" in tile(2, 2, MapLayer.Underground).terrainFeatures)
    }

    // L9
    @Test
    fun l9_visibilityByLayer() {
        addLayers()
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        technocrat.tech.addTechnology("Metallurgy")
        technocrat.tech.addTechnology("Aeronautics")
        val below = game.addUnit("Warrior", technocrat, tile(0, 0, MapLayer.Underground))
        below.updateVisibleTiles()
        Assert.assertTrue("From the Underground the Surface is not visible", below.viewableTiles.none { it.layer == MapLayer.Surface })
        val engineer = game.addUnit("Sky Engineer", technocrat, tile(3, 0, MapLayer.Sky))
        engineer.updateVisibleTiles()
        val surfaceSeen = engineer.viewableTiles.filter { it.layer == MapLayer.Surface }
        Assert.assertTrue(tile(3, 0) in surfaceSeen)
        Assert.assertTrue(surfaceSeen.all { it.aerialDistanceTo(tile(3, 0)) <= 1 })
        Assert.assertEquals(7, surfaceSeen.size)
    }

    // L10
    @Test
    fun l10_savesKeepThreeLayersAndOldSavesGetThem() {
        addLayers()
        val civ = addCiv("Babylon", Faction.Technocrat)
        civ.playerType = com.unciv.logic.civilization.PlayerType.Human
        game.gameInfo.currentPlayer = civ.civID
        game.gameInfo.currentPlayerCiv = civ
        game.gameInfo.gameParameters.baseRuleset = KotakulaTestSupport.MOD
        tile(1, 1, MapLayer.Underground).baseTerrain = LayerTerrains.fungalGrotto
        val saved = UncivFiles.gameInfoToString(game.gameInfo, forceZip = false)
        val loaded = UncivFiles.gameInfoFromString(saved)
        Assert.assertNotNull(loaded.undergroundMap)
        Assert.assertNotNull(loaded.skyMap)
        Assert.assertEquals(LayerTerrains.fungalGrotto, loaded.undergroundMap!![1, 1].baseTerrain)
        Assert.assertEquals(LayerTerrains.fungalGrotto, loaded.tileMap.getByKey(MapLayer.toKey(HexCoord(1, 1), MapLayer.Underground))!!.baseTerrain)
        Assert.assertTrue(loaded.tileMap.hasLayers())

        // A save of the time before the layers gets generated layers and a notification
        game.gameInfo.undergroundMap = null
        game.gameInfo.skyMap = null
        game.tileMap.linkLayers(null, null)
        val oldSave = UncivFiles.gameInfoToString(game.gameInfo, forceZip = false)
        val upgraded = UncivFiles.gameInfoFromString(oldSave)
        Assert.assertNotNull(upgraded.undergroundMap)
        Assert.assertNotNull(upgraded.skyMap)
        Assert.assertTrue(upgraded.getCivilization(civ.civID).notifications.any { it.text == "The Multiverse built the missing floors" })
    }

    // L11
    @Test
    fun l11_unitTakesTheShorterTunnel() {
        addLayers(underground = LayerTerrains.solidRock)
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        technocrat.tech.addTechnology("Metallurgy")
        // A wall of mountains between x = 0 ... and a tunnel below it with entrances at both ends
        for (tile in game.tileMap.values) if (tile.position.y == 0 && tile.position.x in -6..5) setTerrain(tile, Constants.mountain)
        val start = tile(-1, -3)
        val goal = tile(-1, 3)
        start.addTerrainFeature(LayerTerrains.caveEntrance)
        goal.addTerrainFeature(LayerTerrains.caveEntrance)
        // Straight tunnel under the wall
        var x = -1
        var y = -3
        while (y <= 3) {
            val underground = tile(x, y, MapLayer.Underground)
            setTerrain(underground, LayerTerrains.cave)
            underground.setImprovement("Tunnel")
            y++
        }
        for (tile in game.gameInfo.allTiles()) tile.setExplored(technocrat, true) // unknown tiles count as passable
        val unit = game.addUnit("Warrior", technocrat, start)
        val path = unit.movement.getShortestPath(goal)
        Assert.assertTrue("A path exists", path.isNotEmpty())
        Assert.assertEquals(goal, path.last())
        Assert.assertTrue("The path goes through the Underground: $path", path.any { it.layer == MapLayer.Underground })
    }
    /** Regression (1.3.0 crashed on start): Civilopedia and font icons draw single tiles without a backing map */
    @Test
    fun singleTileViewsHaveNoLayerData() {
        for (terrain in game.ruleset.terrains.values) {
            val tile = Tile()
            tile.ruleset = game.ruleset
            tile.baseTerrain = if (terrain.type.isBaseTerrain) terrain.name else "Plains"
            tile.setTerrainTransients()
            val view = com.unciv.view.TileView.forSingleTile(tile)
            Assert.assertTrue(terrain.name, view.layerCityImages().isEmpty())
            Assert.assertFalse(terrain.name, view.isLayerTransition())
            Assert.assertNull(terrain.name, view.layerRouteName())
            Assert.assertFalse(terrain.name, view.isCityColumn())
        }
    }
    /** Regression (1.3.0): the new game screen refills one Ruleset when the base changes - the flags must follow */
    @Test
    fun rulesetFlagsFollowARefilledRuleset() {
        val refilled = com.unciv.models.ruleset.Ruleset()
        refilled.add(com.unciv.models.ruleset.RulesetCache[com.unciv.models.metadata.BaseRuleset.Civ_V_GnK.fullName]!!)
        Assert.assertFalse(refilled.hasFactions)
        Assert.assertFalse(refilled.hasLayers)
        refilled.clear()
        refilled.add(game.ruleset)
        Assert.assertTrue(refilled.hasFactions)
        Assert.assertTrue(refilled.hasLayers)
    }
    /** Regression (autoplay crash): barbarian camps of the layers are stored by layer key and must be found by it */
    @Test
    fun layerBarbarianCampsAreFoundByKey() {
        addLayers()
        val camp = game.gameInfo.undergroundMap!![HexCoord(2, 1)]
        game.gameInfo.barbarians.setTransients(game.gameInfo)
        game.gameInfo.barbarians.encampments.add(com.unciv.logic.automation.civilization.BarbarianEncampment(camp.layerKey))
        Assert.assertEquals(listOf(camp), game.gameInfo.barbarians.getEncampmentTiles())
    }
}
