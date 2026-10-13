package com.unciv.logic.kotakula

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import yairm210.purity.annotations.Readonly

/**
 * KC-21 / section 6.6: summoned units.
 *
 * `KM: Summons best [unitType] unit every [N] turns <KM: Expires after [M] turns>`:
 * every N turns the strongest unit of that type the civilization can build appears in the capital
 * (or in the city with the source building), if there is room. It gets the "Summoned" status
 * (Promotion with `KM: Summoned`: no upkeep, no upgrade, no gifting) and disappears after M turns.
 *
 * A unit with the unique `KM: Expires after [N] turns` disappears N turns after it appeared.
 */
object Summoning {
    /** Status (promotion in UnitPromotions.json) marking summoned units. Its turns are the remaining lifetime. */
    const val SUMMONED_STATUS = "Summoned"
    /** Status for units that simply expire (`KM: Expires after [N] turns` on the unit) */
    const val EXPIRING_STATUS = "Expiring"
    /** Lifetime used when a source gives no `KM: Expires after` */
    private const val UNLIMITED_TURNS = 100000

    @Readonly
    fun isSummoned(unit: MapUnit): Boolean =
        unit.hasStatus(SUMMONED_STATUS) || unit.hasUnique(UniqueType.KMSummoned)

    /** Does losing this status mean the unit vanishes? */
    @Readonly
    fun isExpiryStatus(statusName: String) = statusName == SUMMONED_STATUS || statusName == EXPIRING_STATUS

    /** The unit's lifetime ran out: it returns to its own dimension */
    fun dismiss(unit: MapUnit) {
        val name = unit.displayName()
        unit.civ.addNotification("The summoned [$name] has returned to its own dimension",
            unit.currentTile.position, NotificationCategory.Units, unit.name, NotificationIcon.Death)
        unit.destroy()
    }

    /** Hook for every newly created unit: units with `KM: Expires after [N] turns` start their countdown */
    fun onUnitCreated(unit: MapUnit) {
        val expiry = unit.baseUnit.getMatchingUniques(UniqueType.KMExpiresAfter).firstOrNull() ?: return
        unit.setStatus(EXPIRING_STATUS, expiry.params[0].toInt())
    }

    /** Start of turn: advance every summoning source of [civ] */
    fun onTurnStart(civ: Civilization) {
        if (civ.cities.isEmpty()) return
        val seenKeys = HashSet<String>()
        civ.forEachMatchingUnique(UniqueType.KMSummons) { unique ->
            val key = "${unique.sourceObjectName}|${unique.params[0]}|${unique.params[1]}"
            if (!seenKeys.add(key)) return@forEachMatchingUnique // the same source counted once per turn
            val interval = unique.params[1].toInt()
            val counter = (civ.factionState.summonCounters[key] ?: 0) + 1
            if (counter < interval) {
                civ.factionState.summonCounters[key] = counter
                return@forEachMatchingUnique
            }
            val summoned = summon(civ, unique)
            // When there was no room or no unit, try again next turn
            civ.factionState.summonCounters[key] = if (summoned != null) 0 else counter
        }
        // Forget sources that are gone (e.g. a removed policy)
        civ.factionState.summonCounters.keys.retainAll(seenKeys)
    }

    /** The strongest unit of [unitType] [civ] can build right now, as its own equivalent; null if none */
    @Readonly
    fun getBestUnit(civ: Civilization, unitType: String): BaseUnit? =
        civ.gameInfo.ruleset.units.values.asSequence()
            .filter { it.unitType == unitType || it.matchesFilter(unitType, civ.state) }
            .filter { it.isMilitary }
            .map { civ.getEquivalentUnit(it) }
            .distinct()
            .filter { it.isBuildable(civ) }
            .maxByOrNull { maxOf(it.strength, it.rangedStrength) * 1000 + it.cost }

    /** Summons the unit of [unique] for [civ]; returns it, or null when nothing could be summoned */
    fun summon(civ: Civilization, unique: Unique): MapUnit? {
        val baseUnit = getBestUnit(civ, unique.params[0]) ?: return null
        val city = getSummoningCity(civ, unique) ?: return null
        val unit = civ.units.addUnit(baseUnit, city) ?: return null
        val lifetime = unique.getModifiers(UniqueType.KMExpiresAfter).firstOrNull()?.params?.get(0)?.toInt()
            ?: UNLIMITED_TURNS
        unit.setStatus(SUMMONED_STATUS, lifetime)
        unit.currentMovement = 0f
        civ.addNotification("[${unit.displayName()}] has been summoned in [${city.name}]!",
            MapUnitAction(unit), NotificationCategory.Units, unit.name)
        return unit
    }

    /** The capital, or a city with the building the summoning unique comes from */
    @Readonly
    private fun getSummoningCity(civ: Civilization, unique: Unique): City? {
        if (unique.sourceObjectType == UniqueTarget.Building || unique.sourceObjectType == UniqueTarget.Wonder) {
            val city = civ.cities.firstOrNull { it.cityConstructions.isBuilt(unique.sourceObjectName ?: "") }
            if (city != null) return city
        }
        return civ.getCapital()
    }
}
