package com.unciv.kotakula

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.kotakula.Faction
import com.unciv.logic.kotakula.FactionAssignment
import com.unciv.logic.kotakula.FactionPantheons
import com.unciv.logic.kotakula.FactionRules
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.stats.Stat
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** docs/kotakula/gods-and-startups.md, section 4.2: tests T14-T18 of the gods and startups */
@RunWith(GdxTestRunner::class)
class KotakulaPantheonTests {

    private lateinit var game: TestGame

    @Before
    fun setUp() {
        game = TestGame(baseRuleset = KotakulaTestSupport.MOD)
        game.makeHexagonalMap(8, "Plains")
        game.setSpeed("Standard")
    }

    private fun addCiv(nation: String, faction: Faction, human: Boolean = false): Civilization {
        val civ = game.addCiv(game.ruleset.nations[nation]!!, isPlayer = human)
        FactionAssignment.setFaction(civ, faction, notify = false)
        return civ
    }

    private fun takePantheon(civ: Civilization, name: String) =
        civ.religionManager.chooseBeliefs(listOf(game.ruleset.beliefs[name]!!), useFreeBeliefs = true)

    private val gods = listOf("Purr Goddess", "God of Sleeping In", "Lord of Puddles", "Thunder Grandpa", "Mushroom Lady",
        "God of Lost Things", "Mandrake Spirit", "Hearth Keepers", "Ancient Sloth", "Bard Goddess", "Kraken Patron",
        "Chaos Nameless", "Hoard Dragon")
    private val startups = listOf("Coffee Cult", "Deadline Cult", "15 Minute Delivery", "Aggregator", "Neural Cats",
        "Horse Sharing", "Selling Air", "Mining Farm", "Crowdfunding", "Free Trial", "Open Source", "Schrodinger Security",
        "Hire a Merc")

    // T14
    @Test
    fun t14_eachFactionSeesOnlyItsPantheons() {
        val humanist = addCiv("Greece", Faction.Humanist)
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        fun pantheonsOf(civ: Civilization) = game.ruleset.beliefs.values
            .filter { it.type == BeliefType.Pantheon && FactionRules.canUse(civ, it) }.map { it.name }.sorted()
        Assert.assertEquals(gods.sorted(), pantheonsOf(humanist))
        Assert.assertEquals(startups.sorted(), pantheonsOf(technocrat))
        Assert.assertEquals("No vanilla pantheons", 26, game.ruleset.beliefs.values.count { it.type == BeliefType.Pantheon })
        Assert.assertEquals(13, humanist.religionManager.numberOfBeliefsAvailable(BeliefType.Pantheon))
        Assert.assertEquals(13, technocrat.religionManager.numberOfBeliefsAvailable(BeliefType.Pantheon))
        // Faction beliefs of section 9.5: 4 per faction now
        Assert.assertEquals(4, game.ruleset.beliefs.values.count { it.type != BeliefType.Pantheon && it.faction == Faction.Humanist })
        Assert.assertEquals(4, game.ruleset.beliefs.values.count { it.type != BeliefType.Pantheon && it.faction == Faction.Technocrat })
    }

    // T15
    @Test
    fun t15_thunderGrandpaHitsOneTimeInTenAndNeverKills() {
        val humanist = addCiv("Greece", Faction.Humanist)
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        game.addCity(humanist, game.getTile(HexCoord(0, 0)))
        takePantheon(humanist, "Thunder Grandpa")
        val enemy = game.addUnit("Warrior", technocrat, game.getTile(HexCoord(2, 0)))
        val far = game.addUnit("Warrior", technocrat, game.getTile(HexCoord(7, 0)))
        var hits = 0
        for (turn in 1..1000) {
            game.gameInfo.turns = turn
            enemy.health = 100
            far.health = 100
            if (FactionPantheons.smite(humanist) > 0) {
                hits++
                Assert.assertEquals(80, enemy.health)
            }
            Assert.assertEquals("Out of reach (more than 3 tiles)", 100, far.health)
        }
        val share = hits / 10.0
        Assert.assertTrue("Hit share $share% must be 9-11%", share in 9.0..11.0)
        // Never dies: even a nearly dead unit keeps 1 HP
        var turn = 1000
        while (true) {
            game.gameInfo.turns = ++turn
            enemy.health = 5
            if (FactionPantheons.smite(humanist) > 0) break
        }
        Assert.assertEquals(1, enemy.health)
        Assert.assertTrue(enemy in technocrat.units.getCivUnits())
    }

    // T16
    @Test
    fun t16_pivotWorksOnceBeforeTheCorporation() {
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        val other = addCiv("China", Faction.Technocrat)
        game.addCity(technocrat, game.getTile(HexCoord(0, 0)))
        takePantheon(technocrat, "Coffee Cult")
        technocrat.religionManager.storedFaith = 1000
        val aggregator = game.ruleset.beliefs["Aggregator"]!!
        Assert.assertTrue(FactionPantheons.canPivot(technocrat))
        Assert.assertTrue(FactionPantheons.pivot(technocrat, aggregator))
        Assert.assertEquals("Aggregator", FactionPantheons.pantheonOf(technocrat)!!.name)
        Assert.assertEquals(1000 - FactionPantheons.pivotCost(technocrat), technocrat.religionManager.storedFaith)
        Assert.assertTrue("The old startup is free again",
            game.ruleset.beliefs["Coffee Cult"]!! in FactionPantheons.freePantheons(other))
        Assert.assertFalse("Only once per game", FactionPantheons.canPivot(technocrat))
        Assert.assertFalse(FactionPantheons.pivot(technocrat, game.ruleset.beliefs["Neural Cats"]!!))

        // Not after founding the corporation, and never for the Humanists
        takePantheon(other, "Neural Cats")
        other.religionManager.storedFaith = 1000
        Assert.assertTrue(FactionPantheons.canPivot(other))
        com.unciv.logic.civilization.managers.ReligionManager::class.java.getDeclaredField("religionState")
            .apply { isAccessible = true }.set(other.religionManager, ReligionState.Religion)
        Assert.assertFalse(FactionPantheons.canPivot(other))
        val humanist = addCiv("Greece", Faction.Humanist)
        takePantheon(humanist, "Purr Goddess")
        humanist.religionManager.storedFaith = 1000
        Assert.assertFalse(FactionPantheons.canPivot(humanist))
    }

    // T17
    @Test
    fun t17_eventsComeEvery25TurnsToTheirFactionOnly() {
        val humanist = addCiv("Greece", Faction.Humanist, human = true)
        val technocrat = addCiv("Babylon", Faction.Technocrat, human = true)
        game.addCity(humanist, game.getTile(HexCoord(0, 0)))
        game.addCity(technocrat, game.getTile(HexCoord(5, 0)))
        takePantheon(humanist, "Purr Goddess")
        takePantheon(technocrat, "Aggregator")
        val whims = ArrayList<Int>()
        val rounds = ArrayList<Int>()
        for (turn in 1..100) {
            game.gameInfo.turns = turn
            for (civ in listOf(humanist, technocrat)) {
                civ.popupAlerts.clear()
                FactionPantheons.onTurnStart(civ)
                for (alert in civ.popupAlerts) when (alert.value) {
                    FactionPantheons.GODS_WHIM_EVENT -> { Assert.assertEquals(humanist, civ); whims += turn }
                    FactionPantheons.INVESTOR_ROUND_EVENT -> { Assert.assertEquals(technocrat, civ); rounds += turn }
                }
            }
        }
        Assert.assertEquals(listOf(25, 50, 75, 100), whims)
        Assert.assertEquals(listOf(25, 50, 75, 100), rounds)

        // The choices of the events
        humanist.religionManager.storedFaith = 100
        Assert.assertTrue(FactionPantheons.bringGift(humanist))
        Assert.assertEquals(100 - FactionPantheons.giftCost(humanist), humanist.religionManager.storedFaith)
        humanist.religionManager.storedFaith = 0
        Assert.assertFalse("Not available without enough Faith", FactionPantheons.canAffordGift(humanist))
        technocrat.religionManager.storedFaith = 200
        val gold = technocrat.gold
        FactionPantheons.takeInvestorMoney(technocrat)
        Assert.assertEquals(100, technocrat.gold - gold)
        Assert.assertEquals(150, technocrat.religionManager.storedFaith)
    }

    // T18
    @Test
    fun t18_chaosGivesEveryStatAsOften() {
        val humanist = addCiv("Greece", Faction.Humanist)
        val counts = HashMap<Stat, Int>()
        for (gift in 1..1000) {
            game.gameInfo.turns = gift * 10
            counts[FactionPantheons.chaosStat(humanist)] = (counts[FactionPantheons.chaosStat(humanist)] ?: 0) + 1
        }
        for (stat in FactionPantheons.chaosStats) {
            val share = (counts[stat] ?: 0) / 10.0
            Assert.assertTrue("$stat share $share% must be 18-22%", share in 18.0..22.0)
        }
    }

    @Test
    fun freeTrialAndOpenSourceStats() {
        val technocrat = addCiv("Babylon", Faction.Technocrat)
        game.addCity(addCiv("China", Faction.Technocrat), game.getTile(HexCoord(4, 0)))
        game.addCity(addCiv("Egypt", Faction.Technocrat), game.getTile(HexCoord(-4, 0)))
        val city = game.addCity(technocrat, game.getTile(HexCoord(0, 0)))
        game.gameInfo.turns = 10
        takePantheon(technocrat, "Free Trial")
        Assert.assertTrue(FactionPantheons.isFreeTrialActive(technocrat, 30))
        game.gameInfo.turns = 40
        Assert.assertFalse(FactionPantheons.isFreeTrialActive(technocrat, 30))
        Assert.assertEquals(2, FactionPantheons.otherLivingCivsOfFaction(technocrat))
        Assert.assertNotNull(city)
    }
}
