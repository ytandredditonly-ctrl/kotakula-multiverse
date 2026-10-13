package com.unciv.logic.kotakula

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

/**
 * KC-14: capturing a city of the other faction (section 2.8).
 *
 * 1. Common buildings follow the usual Unciv capture rules.
 * 2. Buildings of the other faction are destroyed; each gives a trophy of 25% of its production cost as Science
 *    (shown as Mana to Humanists). A building with `KM: Trophy: gain [N]% of building cost as [stat]` overrides that.
 *    Wonders of the other faction stay, but do nothing while the city belongs to the enemy side.
 * 3. The population loses 50% instead of the usual 25%.
 * 4. Resources of the other faction stay visible (after the reveal tech) and keep not working - see [FactionResources].
 * 5. A religion of the other faction is wiped out; the conqueror's religion moves in with pressure 100.
 * 6. Notification "[City] has been liberated from the ...".
 */
object FactionConquest {

    const val DEFAULT_TROPHY_PERCENT = 25
    const val CONQUERED_RELIGION_PRESSURE = 100

    /** Is this a capture across the faction line? */
    @Readonly
    fun isOpposingCapture(conqueror: Civilization, previousOwner: Civilization): Boolean =
        FactionRules.areOpposing(conqueror, previousOwner)

    /**
     * Destroys the other faction's buildings and pays out the trophies. Call before the city changes hands.
     * @return the amounts of Science gained per building (for tests and the notification)
     */
    fun destroyForeignBuildings(city: City, conqueror: Civilization): Int {
        var broken = 0
        var scienceGained = 0
        for (building in city.cityConstructions.getBuiltBuildings().toList()) {
            if (building.faction == null || FactionRules.canUse(conqueror, building.faction)) continue
            if (building.isAnyWonder()) continue // stays, but is inactive for the conqueror
            val trophy = building.getMatchingUniques(UniqueType.KMTrophy).firstOrNull()
            val percent = trophy?.params?.get(0)?.toInt() ?: DEFAULT_TROPHY_PERCENT
            val stat = trophy?.params?.get(1)?.let { Stat.safeValueOf(it) } ?: Stat.Science
            val amount = (building.getProductionCost(conqueror, city) * percent / 100f).roundToInt()
            city.cityConstructions.removeBuilding(building)
            conqueror.addStat(stat, amount)
            broken++
            if (stat == Stat.Science) scienceGained += amount
        }
        val enemyFaction = conqueror.faction.opposite()
        val text = if (enemyFaction == Faction.Technocrat)
            "[${city.name}] has been liberated from the Technocrats. Found [$broken] broken computers. Recycled into [$scienceGained] Science"
        else "[${city.name}] has been liberated from the Humanists. Found [$broken] broken staffs. Recycled into [$scienceGained] Science"
        conqueror.addNotification(text, city.location, NotificationCategory.War,
            "FactionIcons/${enemyFaction.name}", NotificationIcon.Science)
        return scienceGained
    }

    /** Population loss on capture: 50% for an opposing capture (instead of 25%) */
    @Readonly
    fun getPopulationLoss(population: Int): Int =
        if (population <= 1) 0 else (population / 2).coerceAtLeast(1)

    /** After the city changed hands: wipe the enemy religion, bring in ours, rewards and opinions */
    fun afterCapture(city: City, conqueror: Civilization) {
        replaceForeignReligion(city, conqueror)
        city.cityConstructions.refreshActiveBuildings()

        conqueror.forEachMatchingUnique(UniqueType.KMGainStatOnOpposingCityConquered) { unique ->
            val stat = Stat.safeValueOf(unique.params[1]) ?: return@forEachMatchingUnique
            conqueror.addStat(stat, unique.params[0].toInt())
        }
        EternalWar.onOpposingCityConquered(conqueror)
    }

    private fun replaceForeignReligion(city: City, conqueror: Civilization) {
        val gameInfo = city.civ.gameInfo
        if (!gameInfo.isReligionEnabled()) return
        val religionManager = city.religion
        val foreignReligions = religionManager.getPressures().keys.filter { religionName ->
            val religion = gameInfo.religions[religionName] ?: return@filter false
            FactionRules.areOpposing(religion.foundingCiv, conqueror)
        }
        if (foreignReligions.isEmpty()) return
        val ourReligion = conqueror.religionManager.religion?.takeIf { it.isMajorReligion() }
        religionManager.removeReligions(foreignReligions.toSet())
        if (ourReligion != null)
            religionManager.addPressure(ourReligion.name, CONQUERED_RELIGION_PRESSURE)
        if (religionManager.religionThisIsTheHolyCityOf in foreignReligions)
            religionManager.isBlockedHolyCity = true
    }
}
