package com.unciv.logic.kotakula

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.ruleset.FactionAction
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * KC-19: AI rules of the Kotakula Multiverse (section 12) that are not plain multipliers inside vanilla automation.
 */
object FactionAutomation {

    // Faith action names of FactionActions.json (section 9.4)
    private const val SHAREHOLDER_MOBILIZATION = "Shareholder Mobilization"
    private const val CRUSADE = "Crusade"
    private const val MIRACLE_OF_HEALING = "Miracle of Healing"
    private const val CORPORATE_PARTY = "Corporate Party"
    private const val HOLY_FEAST = "Holy Feast"
    private const val QUARTERLY_BONUS = "Quarterly Bonus"
    private const val CRAFT_BLESSING = "Craft Blessing"
    private const val HACKATHON = "Hackathon"
    private const val STARFALL_NIGHT = "Starfall Night"
    private const val AD_CAMPAIGN = "Ad Campaign"
    private const val FESTIVAL_OF_MUSES = "Festival of Muses"
    private const val MERGERS_AND_ACQUISITIONS = "Mergers and Acquisitions"
    private const val IPO_BUBBLE = "IPO Bubble"
    private const val PROPHET_VISION = "Prophet Vision"

    /** Allied status with a city-state needs this much influence */
    private const val ALLY_INFLUENCE = 60f
    /** Section 12.4 rule 7 */
    private const val MERGER_INFLUENCE_GAP = 40f

    /** One AI faith action per turn, chosen by the first matching rule of section 12.4 */
    fun useFaithActions(civ: Civilization) {
        if (!FactionRules.isFactionGame(civ.gameInfo) || !civ.faction.isReal()) return
        if (!FaithActions.areUnlocked(civ)) return
        val isTechnocrat = civ.faction == Faction.Technocrat

        fun pick(technocratAction: String?, humanistAction: String?): String? =
            if (isTechnocrat) technocratAction else humanistAction

        val candidates = sequence {
            if (isEnemyArmyNearOurCities(civ)) yield(pick(SHAREHOLDER_MOBILIZATION, CRUSADE))
            if (shareOfWoundedMilitary(civ) >= 0.4f) yield(pick(null, MIRACLE_OF_HEALING))
            if (civ.getHappiness() < 0) yield(pick(CORPORATE_PARTY, HOLY_FEAST))
            if (isBuildingWonderOrVictoryPart(civ)) yield(pick(QUARTERLY_BONUS, CRAFT_BLESSING))
            val preferred = civ.getPreferredVictoryTypes()
            if (preferred.any { it == "Scientific" || it == "Interuniverse Flight" || it == "Gates of Paradise" })
                yield(pick(HACKATHON, STARFALL_NIGHT))
            if ("Cultural" in preferred) yield(pick(AD_CAMPAIGN, FESTIVAL_OF_MUSES))
            if (getCityStateNearAlliance(civ) != null) yield(pick(MERGERS_AND_ACQUISITIONS, null))
            yield(pick(IPO_BUBBLE, PROPHET_VISION)) // rule 8 is checked below with its own threshold
        }

        val ruleset = civ.gameInfo.ruleset
        for (actionName in candidates) {
            val action = ruleset.factionActions[actionName ?: continue] ?: continue
            if (!FaithActions.canStart(civ, action)) continue
            if (!canAfford(civ, action)) continue
            val isLastRule = actionName == IPO_BUBBLE || actionName == PROPHET_VISION
            if (isLastRule && civ.religionManager.storedFaith <= 3 * FaithActions.getCost(civ, action)) continue
            val chosenCityState = if (FaithActions.needsCityState(action)) getCityStateNearAlliance(civ) else null
            FaithActions.start(civ, action, chosenCityState)
            return
        }
    }

    /** Section 12.4: keep enough Faith for the next Great Prophet until the religion is founded and enhanced */
    @Readonly
    private fun canAfford(civ: Civilization, action: FactionAction): Boolean {
        val cost = FaithActions.getCost(civ, action)
        val left = civ.religionManager.storedFaith - cost
        if (left < 0) return false
        if (civ.religionManager.religionState >= ReligionState.EnhancedReligion) return true
        return left >= civ.religionManager.faithForNextGreatProphet()
    }

    @Readonly
    private fun isEnemyArmyNearOurCities(civ: Civilization): Boolean {
        val enemies = civ.getCivsAtWarWith().filter { FactionRules.areOpposing(civ, it) }
        if (enemies.isEmpty()) return false
        val ourCityTiles = civ.cities.map { it.getCenterTile() }
        return enemies.any { enemy ->
            enemy.units.getCivUnits().any { unit ->
                unit.isMilitary() && ourCityTiles.any { it.aerialDistanceTo(unit.getTile()) <= 5 }
            }
        }
    }

    @Readonly
    private fun shareOfWoundedMilitary(civ: Civilization): Float {
        val military = civ.units.getCivUnits().filter { it.isMilitary() }.toList()
        if (military.isEmpty()) return 0f
        return military.count { it.health < 50 }.toFloat() / military.size
    }

    @Readonly
    private fun isBuildingWonderOrVictoryPart(civ: Civilization): Boolean = civ.cities.any { city ->
        val construction = city.cityConstructions.getCurrentConstruction()
        construction is com.unciv.models.ruleset.Building && construction.isAnyWonder()
            || construction is com.unciv.models.ruleset.unit.BaseUnit && construction.hasUnique(UniqueType.SpaceshipPart)
    }

    /** A known city-state we are at most [MERGER_INFLUENCE_GAP] influence short of allying (section 12.1 / 12.4) */
    @Readonly
    fun getCityStateNearAlliance(civ: Civilization): Civilization? =
        FaithActions.getChoosableCityStates(civ)
            .filter { it.allyCiv != civ }
            .map { it to (it.getDiplomacyManager(civ)?.getInfluence() ?: 0f) }
            .filter { (_, influence) -> ALLY_INFLUENCE - influence in 0f..MERGER_INFLUENCE_GAP }
            .maxByOrNull { it.second }?.first

    /** Section 12.1: during the truce the AI plans no attacks on the other side, except in the last 5 turns */
    @Readonly
    fun isTargetBlockedByTruce(civ: Civilization, target: Civilization): Boolean =
        FactionRules.isTruceBetween(civ, target) && FactionRules.truceTurnsLeft(civ.gameInfo) > 5

    /** Section 12.1: how attractive [target] is as a war target for [civ] - a multiplier of the vanilla evaluation */
    @Readonly
    fun getTargetAttractiveness(civ: Civilization, target: Civilization): Float {
        if (FactionRules.areOpposing(civ, target)) {
            val enemiesLeft = FactionRules.aliveMajorCivsOf(civ.gameInfo, target.faction).size
            return if (enemiesLeft <= 2) 1.5f * 2f else 1.5f // "hunting the faction" doubles it
        }
        if (FactionRules.areSameFaction(civ, target)) return 0.5f
        return 1f
    }

    /** Section 12.1 applied to the vanilla motivation to attack (positive motivation is scaled, negative kept) */
    @Readonly
    fun adjustMotivation(civ: Civilization, target: Civilization, motivation: Float): Float {
        if (isTargetBlockedByTruce(civ, target)) return minOf(motivation, 0f)
        if (motivation <= 0f) return motivation
        return motivation * getTargetAttractiveness(civ, target)
    }

    /** Section 12.3: summoned units are temporary and do not count as a real army */
    @Readonly
    fun countsAsArmy(unit: com.unciv.logic.map.mapunit.MapUnit): Boolean = !Summoning.isSummoned(unit)
}
