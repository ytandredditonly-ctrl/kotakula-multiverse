package com.unciv.logic.kotakula

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.Ruleset
import yairm210.purity.annotations.Readonly
import kotlin.math.max

/**
 * KC-11: religions and corporations (section 9.2 / 9.3).
 *
 * - Separate pools: at most `max(2, floor(major civs of the faction / 2) + 1)` religions per faction.
 * - Humanists pick from the vanilla religion names, Technocrats from the corporation list of section 9.3.
 * - No religious pressure between cities of different factions; missionaries and prophets cannot spread there.
 * - City-states and barbarians take religions of both sides.
 */
object FactionReligion {

    /** Corporation ids of Religions.json (section 9.3). Technocrats found corporations, everyone else religions. */
    val corporations = listOf(
        "Kotakula Holdings",
        "Horns and Hooves LLC",
        "Megacorp",
        "Evil Corporation (Rebranded)",
        "Startup Inc",
        "Pixel and Sons",
        "Global Deadline",
        "Infinite Coffee Inc",
        "Synergy of Synergies",
        "Cloud Solutions of Clouds",
        "Quarterly Report Group",
        "Effective Managers",
    )

    @Readonly
    fun isCorporation(religionName: String) = religionName in corporations

    /** Religion names [civ] may choose from when founding (section 9.2.1) */
    @Readonly
    fun getReligionNames(ruleset: Ruleset, civ: Civilization): List<String> {
        if (!ruleset.hasFactions) return ruleset.religions
        return when (civ.faction) {
            Faction.Technocrat -> ruleset.religions.filter { isCorporation(it) }
            Faction.Humanist -> ruleset.religions.filterNot { isCorporation(it) }
            Faction.None -> ruleset.religions
        }
    }

    /** Section 9.2.1: maximum number of religions of [civ]'s faction; null when the faction rules do not apply */
    @Readonly
    fun maxReligionsOfFaction(civ: Civilization): Int? {
        if (!FactionRules.isFactionGame(civ.gameInfo) || !civ.faction.isReal()) return null
        val factionMajors = civ.gameInfo.civilizations.count { it.isMajorCiv() && it.faction == civ.faction }
        val limit = max(2, factionMajors / 2 + 1)
        return minOf(limit, getReligionNames(civ.gameInfo.ruleset, civ).size)
    }

    /** Religions (from [minState] on) founded by civilizations of [civ]'s faction */
    @Readonly
    fun countOfFaction(civ: Civilization, minState: ReligionState): Int =
        civ.gameInfo.civilizations.count {
            it.faction == civ.faction && it.religionManager.religionState >= minState
                && (minState < ReligionState.Religion || it.religionManager.religion != null)
        }

    /** Section 9.2.2: no pressure from [religionName] onto [city] if the religion's founder is on the other side */
    @Readonly
    fun isPressureBlocked(religionName: String, city: City): Boolean {
        val gameInfo = city.civ.gameInfo
        if (!FactionRules.isFactionGame(gameInfo)) return false
        val religion = gameInfo.religions[religionName] ?: return false
        return FactionRules.areOpposing(religion.foundingCiv, city.civ)
    }

    /** Section 9.2.3: can [unit] (missionary, prophet) spread its religion into [city]? */
    @Readonly
    fun canSpreadInto(unit: MapUnit, city: City): Boolean {
        val religionName = unit.religion ?: return true
        return !isPressureBlocked(religionName, city)
    }

    /** Section 9.1: `KM: Holy city bonus` gives [+10]% Production and Gold in the holy city of the owner's own religion */
    @Readonly
    fun getHolyCityBonus(city: City): com.unciv.models.stats.Stats? {
        val holyOf = city.religion.religionThisIsTheHolyCityOf ?: return null
        if (city.civ.religionManager.religion?.name != holyOf) return null
        if (!city.civ.hasUnique(com.unciv.models.ruleset.unique.UniqueType.KMHolyCityBonus)) return null
        return com.unciv.models.stats.Stats(production = HOLY_CITY_BONUS_PERCENT, gold = HOLY_CITY_BONUS_PERCENT)
    }

    private const val HOLY_CITY_BONUS_PERCENT = 10f

    /** The hint shown instead of the spread action (section 9.2.3), depends on the city's side */
    @Readonly
    fun getSpreadBlockedHint(city: City): String =
        if (city.civ.faction == Faction.Technocrat)
            "Technocrats do not believe in your gods. They believe in the quarterly report"
        else "Humanists do not buy your shares. They cannot read reports"
}
