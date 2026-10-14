package com.unciv.kotakula

import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.kotakula.EternalWar
import com.unciv.logic.kotakula.Faction
import com.unciv.logic.kotakula.FactionAssignment
import com.unciv.logic.kotakula.FactionRules
import com.unciv.logic.kotakula.WorldSplit
import com.unciv.logic.map.MapSize
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Faction settings of the new game screen: newbie truce length, irreconcilability, world split */
@RunWith(GdxTestRunner::class)
class KotakulaGameSettingsTests {

    private lateinit var game: TestGame

    @Before
    fun setUp() {
        game = TestGame(baseRuleset = KotakulaTestSupport.MOD)
        game.makeHexagonalMap(8, "Plains")
        game.setSpeed("Standard")
    }

    private fun addCiv(nation: String, faction: Faction): Civilization {
        val civ = game.addCiv(game.ruleset.nations[nation]!!)
        FactionAssignment.setFaction(civ, faction, notify = false)
        return civ
    }

    @Test
    fun truceLengthFollowsTheSettingAndTheSpeed() {
        val gameInfo = game.gameInfo
        Assert.assertEquals(25, FactionRules.truceLength(gameInfo))
        gameInfo.gameParameters.kotakulaTruceTurns = 50
        Assert.assertEquals(50, FactionRules.truceLength(gameInfo))
        game.setSpeed("Quick")
        Assert.assertEquals(34, FactionRules.truceLength(gameInfo)) // 50 * 17 / 25
        gameInfo.gameParameters.kotakulaTruceTurns = 0
        Assert.assertFalse(FactionRules.isTruceActive(gameInfo))
    }

    @Test
    fun withoutIrreconcilabilityTheFactionsMayMakePeace() {
        game.gameInfo.gameParameters.kotakulaIrreconcilable = false
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        val humanist = addCiv("Greece", Faction.Humanist)
        val otherTechnocrat = addCiv("China", Faction.Technocrat)
        technocrat.diplomacyFunctions.makeCivilizationsMeet(humanist)
        technocrat.diplomacyFunctions.makeCivilizationsMeet(otherTechnocrat)

        Assert.assertFalse("No eternal war", technocrat.isAtWarWith(humanist))
        Assert.assertTrue(EternalWar.canMakePeace(technocrat, humanist))
        Assert.assertEquals(-10f, technocrat.getDiplomacyManager(humanist)!!.diplomaticModifiers[DiplomaticModifiers.OtherFaction.name]!!, 0.01f)
        Assert.assertEquals(-10f, humanist.getDiplomacyManager(technocrat)!!.diplomaticModifiers[DiplomaticModifiers.OtherFaction.name]!!, 0.01f)
        Assert.assertEquals(10f, technocrat.getDiplomacyManager(otherTechnocrat)!!.diplomaticModifiers[DiplomaticModifiers.SameFaction.name]!!, 0.01f)
    }

    @Test
    fun worldSplitPutsTheFactionsInTheirHalves() {
        val ruleset = KotakulaTestSupport.ensureLoaded()
        val setup = GameSetupInfo()
        setup.gameParameters.apply {
            baseRuleset = ruleset.name
            speed = "Standard"
            numberOfCityStates = 4
            victoryTypes = ArrayList(ruleset.victories.keys)
            kotakulaWorldSplit = true
            players = ArrayList(ruleset.nations.values.filter { it.isMajorCiv }.take(7).map { Player(it.name, PlayerType.AI) })
            players += Player(ruleset.nations.values.filter { it.isMajorCiv }[7].name, PlayerType.Human)
        }
        setup.mapParameters.mapSize = MapSize.Small
        setup.mapParameters.seed = 42
        // GameStarter saves the settings at the end - into a temporary folder, not the real GameSettings.json
        val dataDir = kotlin.io.path.createTempDirectory("kotakula-settings").toFile().apply { deleteOnExit() }
        com.unciv.UncivGame.Current.files = com.unciv.logic.files.UncivFiles(com.badlogic.gdx.Gdx.files, dataDir.absolutePath)
        val gameInfo = GameStarter.startNewGame(setup)

        for (civ in gameInfo.civilizations.filter { it.isMajorCiv() && it.isAI() }) {
            val north = WorldSplit.isNorthFor(civ.faction)
                ?: throw AssertionError(gameInfo.civilizations.joinToString { "${it.civName}:${it.faction}:${it.playerType}:${it.isMajorCiv()}" } + " assigned=" + gameInfo.factionGameState.factionsAssigned)
            val start = civ.units.getCivUnits().first { it.baseUnit.isCityFounder() }.getTile() // the settler stands on the start
            Assert.assertTrue("${civ.civName} (${civ.faction}) starts at ${start.position}", WorldSplit.isInHalf(start, north))
        }

        val human = gameInfo.civilizations.first { it.isMajorCiv() && it.isHuman() }
        Assert.assertNotNull("A start is reserved in the other half", gameInfo.factionGameState.splitAlternateStarts[human.civID])
        val startedNorth = human.units.getCivUnits().first { it.baseUnit.isCityFounder() }.getTile().latitude > 0
        // Choose the side of the other half: the starting units move there
        FactionAssignment.setFaction(human, if (startedNorth) Faction.Technocrat else Faction.Humanist)
        Assert.assertTrue(human.units.getCivUnits().any())
        val newStart = human.units.getCivUnits().first { it.baseUnit.isCityFounder() }.getTile()
        Assert.assertTrue("Moved to the other half: ${newStart.position}", WorldSplit.isInHalf(newStart, !startedNorth))
        Assert.assertTrue("All units moved", human.units.getCivUnits().all { it.getTile().aerialDistanceTo(newStart) <= 3 })
        Assert.assertTrue(gameInfo.factionGameState.splitAlternateStarts.isEmpty())
    }
}
