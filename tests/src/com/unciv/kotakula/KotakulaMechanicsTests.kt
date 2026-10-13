package com.unciv.kotakula

import com.unciv.UncivGame
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.kotakula.EternalWar
import com.unciv.logic.kotakula.FaithActions
import com.unciv.logic.kotakula.Faction
import com.unciv.logic.kotakula.FactionAssignment
import com.unciv.logic.kotakula.FactionConquest
import com.unciv.logic.kotakula.FactionEspionage
import com.unciv.logic.kotakula.FactionResources
import com.unciv.logic.kotakula.FactionRules
import com.unciv.logic.kotakula.FactionVictory
import com.unciv.logic.kotakula.Summoning
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.UnitTurnManager
import com.unciv.logic.trade.TradeLogic
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.translations.FactionTextContext
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Spec section 14.2: automated tests T1-T12 of the Kotakula Multiverse mechanics */
@RunWith(GdxTestRunner::class)
class KotakulaMechanicsTests {

    private lateinit var game: TestGame

    @Before
    fun setUp() {
        game = TestGame(baseRuleset = KotakulaTestSupport.MOD)
        game.makeHexagonalMap(8, "Plains")
        game.gameInfo.gameParameters.victoryTypes = ArrayList(game.ruleset.victories.keys)
        game.setSpeed("Standard")
    }

    private fun addCiv(nation: String, faction: Faction, human: Boolean = false): Civilization {
        val civ = game.addCiv(game.ruleset.nations[nation]!!, isPlayer = human)
        if (faction.isReal()) FactionAssignment.setFaction(civ, faction, notify = human)
        return civ
    }

    private fun meet(a: Civilization, b: Civilization) = a.diplomacyFunctions.makeCivilizationsMeet(b)

    // T1
    @Test
    fun t1_aiFactionLotteryIsFair() {
        var technocrats = 0
        for (seed in 0 until 10_000)
            if (FactionAssignment.lottery(seed * 7919L, seed % 12) == Faction.Technocrat) technocrats++
        val share = technocrats / 100.0
        Assert.assertTrue("Technocrat share $share% must be 49-51%", share in 49.0..51.0)
    }

    // T2
    @Test
    fun t2_differentFactionsAreAtWarOnContact() {
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        val humanist = addCiv("Greece", Faction.Humanist)
        meet(technocrat, humanist)
        Assert.assertTrue(technocrat.isAtWarWith(humanist))
        Assert.assertFalse(EternalWar.canMakePeace(technocrat, humanist))
        technocrat.getDiplomacyManager(humanist)!!.makePeace()
        Assert.assertTrue("Peace must be impossible", technocrat.isAtWarWith(humanist))
        Assert.assertTrue("No trade offers between the factions", TradeLogic(technocrat, humanist).ourAvailableOffers.isEmpty())
        // Same faction: ordinary diplomacy
        val other = addCiv("China", Faction.Technocrat)
        meet(technocrat, other)
        Assert.assertFalse(technocrat.isAtWarWith(other))
    }

    // T3
    @Test
    fun t3_newbieTruceProtectsCitiesAndWorkers() {
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        val humanist = addCiv("Greece", Faction.Humanist)
        meet(technocrat, humanist)
        val city = game.addCity(technocrat, game.getTile(HexCoord(0, 0)))
        val worker = game.addUnit("Worker", technocrat, game.getTile(HexCoord(2, 0)))
        val attacker = game.addUnit("Warrior", humanist, game.getTile(HexCoord(1, 0)))
        val warrior = game.addUnit("Warrior", technocrat, game.getTile(HexCoord(1, 1)))
        Assert.assertEquals(25, FactionRules.truceLength(game.gameInfo))

        game.gameInfo.turns = 24 // the 25th turn
        Assert.assertFalse(TargetHelper.containsAttackableEnemy(city.getCenterTile(), MapUnitCombatant(attacker)))
        Assert.assertFalse(attacker.movement.canMoveTo(worker.getTile()))
        Assert.assertTrue("Military units may be attacked", TargetHelper.containsAttackableEnemy(warrior.getTile(), MapUnitCombatant(attacker)))

        game.gameInfo.turns = 25 // the 26th turn
        Assert.assertTrue(TargetHelper.containsAttackableEnemy(city.getCenterTile(), MapUnitCombatant(attacker)))
        Assert.assertTrue(attacker.movement.canMoveTo(worker.getTile()))
    }

    // T4
    @Test
    fun t4_resourcesOfTheOtherFaction() {
        val humanist = addCiv("Greece", Faction.Humanist)
        val coal = game.ruleset.tileResources["Coal"]!!
        val tile = game.getTile(HexCoord(1, 1))
        tile.baseTerrain = "Grassland"
        tile.addTerrainFeature("Hill")
        tile.setTileResource("Coal")
        tile.resourceAmount = 6
        tile.setTerrainTransients()
        game.addCity(humanist, game.getTile(HexCoord(0, 0)))
        humanist.tech.addTechnology("Mining")

        Assert.assertFalse(humanist.canSeeResource(coal))
        Assert.assertFalse(FactionResources.canSeeForeignResource(humanist, coal))
        humanist.tech.addTechnology("Dowsing")
        Assert.assertTrue("Visible after Dowsing", FactionResources.canSeeForeignResource(humanist, coal))
        Assert.assertFalse("Still unusable", humanist.canSeeResource(coal))
        val mine = game.ruleset.tileImprovements["Mine"]!!
        Assert.assertFalse("No improvement on the other faction's resource", tile.improvementFunctions.canBuildImprovement(mine, humanist.state))
    }

    // T5
    @Test
    fun t5_humanistUpgradesThroughReplacements() {
        val humanist = addCiv("Greece", Faction.Humanist)
        val warrior = game.ruleset.units["Warrior"]!!
        Assert.assertEquals("Initiate Guard", humanist.getEquivalentUnit(warrior.upgradesTo!!).name)
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        Assert.assertEquals("Phalanx", technocrat.getEquivalentUnit(warrior.upgradesTo!!).name)
        // The mirrored chain continues on the Humanist side
        Assert.assertEquals("Rune Guard", game.ruleset.units["Initiate Guard"]!!.upgradesTo)
    }

    // T6
    @Test
    fun t6_textsFollowTheViewerFaction() {
        val russian = HashMap<String, String>()
        for (file in listOf(File("jsons/translations/Russian.properties"), File(KotakulaTestSupport.MOD_FOLDER, "jsons/translations/Russian.properties")))
            for (line in file.readLines()) if (" = " in line && !line.startsWith("#")) russian[line.substringBefore(" = ")] = line.substringAfter(" = ")
        UncivGame.Current.translations.clear()
        UncivGame.Current.translations.createTranslations("Russian", russian)
        UncivGame.Current.settings.language = "Russian"
        try {
            Assert.assertEquals("Мана", FactionTextContext.withViewer(Faction.Humanist) { "Science".tr(hideIcons = true, hideStats = true) })
            Assert.assertEquals("Наука", FactionTextContext.withViewer(Faction.Technocrat) { "Science".tr(hideIcons = true, hideStats = true) })
            Assert.assertEquals("Корпоративность", FactionTextContext.withViewer(Faction.Technocrat) { "Faith".tr(hideIcons = true, hideStats = true) })
        } finally {
            UncivGame.Current.settings.language = "English"
        }
    }

    // T7
    @Test
    fun t7_espionageRevealsAShareOfTheEnemyArmy() {
        val humanist = addCiv("Greece", Faction.Humanist)
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        meet(humanist, technocrat)
        game.addCity(humanist, game.getTile(HexCoord(0, 0)))
        game.addCity(technocrat, game.getTile(HexCoord(-6, -6)))
        val enemyUnits = (0 until 8).map { i -> game.addUnit("Warrior", technocrat, game.getTile(HexCoord(i, 4))) }

        humanist.tech.addTechnology("Clairvoyance")
        Assert.assertEquals(25, FactionEspionage.getRevealPercent(humanist))
        FactionEspionage.onTurnStart(humanist)
        Assert.assertEquals(2, humanist.factionState.revealedUnitIds.size)

        humanist.tech.addTechnology("All-Seeing Eye")
        Assert.assertEquals(50, FactionEspionage.getRevealPercent(humanist))
        FactionEspionage.onTurnStart(humanist)
        Assert.assertEquals(4, humanist.factionState.revealedUnitIds.size)
        val nearOurCity = enemyUnits.filter { it.getTile().aerialDistanceTo(game.getTile(HexCoord(0, 0))) <= 6 }
        Assert.assertTrue(nearOurCity.isNotEmpty())
        Assert.assertTrue("Everything within 6 tiles is visible", nearOurCity.all { FactionEspionage.isRevealedTo(it, humanist) })
        Assert.assertTrue(FactionEspionage.unitsToReveal(8, 75) == 6)
    }

    // T8
    @Test
    fun t8_capturingACityOfTheOtherFaction() {
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        val humanist = addCiv("Greece", Faction.Humanist)
        meet(technocrat, humanist)
        game.addCity(technocrat, game.getTile(HexCoord(-5, 0)))
        val city = game.addCity(technocrat, game.getTile(HexCoord(0, 0)), initialPopulation = 8)
        city.cityConstructions.addBuilding("Workshop")
        city.cityConstructions.addBuilding("Monument")
        game.addCity(humanist, game.getTile(HexCoord(5, 0)))
        val workshop = game.ruleset.buildings["Workshop"]!!
        val expectedTrophy = (workshop.getProductionCost(humanist, city) * FactionConquest.DEFAULT_TROPHY_PERCENT / 100f).let { kotlin.math.round(it).toInt() }

        val scienceBefore = humanist.tech.getOverflowScience()
        city.puppetCity(humanist)

        Assert.assertEquals(humanist, city.civ)
        Assert.assertFalse("Buildings of the other faction are destroyed", city.cityConstructions.isBuilt("Workshop"))
        Assert.assertEquals(expectedTrophy, humanist.tech.getOverflowScience() - scienceBefore)
        Assert.assertEquals("Half the population is lost", 4, city.population.population)
    }

    // T9
    @Test
    fun t9_factionVictory() {
        // All civilizations exist before the sides are assigned, as at game start
        val technocrat1 = addCiv("Babylon", Faction.None)
        val technocrat2 = addCiv("China", Faction.None)
        val humanist = addCiv("Greece", Faction.None)
        FactionAssignment.setFaction(technocrat1, Faction.Technocrat)
        FactionAssignment.setFaction(technocrat2, Faction.Technocrat)
        FactionAssignment.setFaction(humanist, Faction.Humanist)
        Assert.assertFalse(game.gameInfo.factionGameState.factionVictoryDisabled)
        game.addCity(technocrat1, game.getTile(HexCoord(0, 0)))
        game.addCity(technocrat2, game.getTile(HexCoord(5, 0)))
        humanist.hasEverOwnedOriginalCapital = true // no cities left: defeated
        Assert.assertTrue(humanist.isDefeated())
        Assert.assertTrue(game.gameInfo.factionGameState.factionPresenceChecked)

        Assert.assertTrue(FactionVictory.hasDestroyedOpposingFaction(technocrat1))
        Assert.assertTrue(game.gameInfo.checkForVictory())
        Assert.assertEquals("Faction Victory", game.gameInfo.victoryData!!.victoryType)
        Assert.assertTrue(FactionVictory.isWinner(game.gameInfo, technocrat1))
        Assert.assertTrue(FactionVictory.isWinner(game.gameInfo, technocrat2))
        Assert.assertFalse(FactionVictory.isWinner(game.gameInfo, humanist))
    }

    // T10
    @Test
    fun t10_factionVictoryDisabledWithoutAnEnemy() {
        val player = addCiv("Babylon", Faction.Technocrat, human = true)
        addCiv("China", Faction.Technocrat)
        Assert.assertTrue(game.gameInfo.factionGameState.factionVictoryDisabled)
        Assert.assertTrue(player.notifications.any { it.text.startsWith("Nobody in this multiverse wanted to be a Humanist") })
        Assert.assertTrue(player.victoryManager.getAvailableVictories().none { FactionVictory.isFactionVictory(it) })
    }

    // T11
    @Test
    fun t11_faithActions() {
        Assert.assertEquals(160, FaithActions.computeCost(80, 2, 1f, 0))
        Assert.assertEquals("Discounts stop at -50%", 80, FaithActions.computeCost(80, 2, 1f, -75))
        Assert.assertEquals(120, FaithActions.computeCost(80, 2, 1f, -25))

        val technocrat = addCiv("Babylon", Faction.Technocrat)
        game.addCity(technocrat, game.getTile(HexCoord(0, 0)))
        technocrat.religionManager.chooseBeliefs(listOf(game.ruleset.beliefs.values.first { it.type == com.unciv.models.ruleset.BeliefType.Pantheon }), useFreeBeliefs = true)
        technocrat.religionManager.storedFaith = 1000
        val ipo = game.ruleset.factionActions["IPO Bubble"]!!
        val cost = FaithActions.getCost(technocrat, ipo)
        Assert.assertEquals(100, cost) // Ancient era, standard speed
        val goldBefore = technocrat.gold
        Assert.assertTrue(FaithActions.start(technocrat, ipo))
        Assert.assertEquals(1000 - cost, technocrat.religionManager.storedFaith)
        Assert.assertEquals("Gain [200]% of action cost as [Gold]", 2 * cost, technocrat.gold - goldBefore)
        Assert.assertEquals(10, technocrat.factionState.faithActionCooldowns["IPO Bubble"])
        Assert.assertFalse(FaithActions.canStart(technocrat, ipo))

        val bonus = game.ruleset.factionActions["Quarterly Bonus"]!!
        Assert.assertTrue(FaithActions.start(technocrat, bonus))
        Assert.assertFalse("Only one long action slot", FaithActions.canStart(technocrat, game.ruleset.factionActions["Ad Campaign"]!!))
        repeat(10) { FaithActions.onTurnEnd(technocrat) }
        Assert.assertFalse(technocrat.factionState.activeFaithActions.containsKey("Quarterly Bonus"))
        Assert.assertEquals("Cooldown of 10 turns after the end", 10, technocrat.factionState.faithActionCooldowns["Quarterly Bonus"])
    }

    // T12
    @Test
    fun t12_summonedUnits() {
        val humanist = addCiv("Greece", Faction.Humanist)
        game.addCity(humanist, game.getTile(HexCoord(0, 0)))
        humanist.tech.addTechnology("Oath of Blades")
        val upkeepBefore = humanist.stats.getStatMapForNextTurn()["Unit upkeep"]?.gold ?: 0f
        repeat(4) { game.addUnit("Warrior", humanist, null) } // over the free units, so upkeep counts
        val upkeepWithArmy = humanist.stats.getStatMapForNextTurn()["Unit upkeep"]?.gold ?: 0f

        val unit = Summoning.summon(humanist, Unique("KM: Summons best [Melee] unit every [10] turns <KM: Expires after [3] turns>"))
        Assert.assertNotNull(unit)
        Assert.assertEquals("The strongest buildable melee unit", "Initiate Guard", unit!!.name)
        Assert.assertTrue(Summoning.isSummoned(unit))
        Assert.assertEquals("No upkeep", upkeepWithArmy, humanist.stats.getStatMapForNextTurn()["Unit upkeep"]?.gold ?: 0f)
        Assert.assertTrue(upkeepWithArmy <= upkeepBefore)

        repeat(2) { UnitTurnManager(unit).startTurn() }
        Assert.assertTrue(unit in humanist.units.getCivUnits())
        UnitTurnManager(unit).startTurn()
        Assert.assertFalse("Gone after 3 turns", unit in humanist.units.getCivUnits())
    }

    @Test
    fun humanPlayerMustChooseAFaction() {
        val player = addCiv("Babylon", Faction.None, human = true)
        Assert.assertTrue(FactionAssignment.needsChoice(player))
        FactionAssignment.promptChoice(player)
        Assert.assertTrue(player.popupAlerts.any { it.value == FactionAssignment.CHOOSE_FACTION_EVENT })
        val choice = game.ruleset.events[FactionAssignment.CHOOSE_FACTION_EVENT]!!.choices[1]
        choice.triggerChoice(player)
        Assert.assertEquals(Faction.Humanist, player.faction)
        Assert.assertTrue(player.notifications.any { it.text.startsWith("You chose the path of the Humanist") })
        // Irreversible
        FactionAssignment.setFaction(player, Faction.Technocrat)
        Assert.assertEquals(Faction.Humanist, player.faction)
        player.playerType = PlayerType.Human
    }
}
