package com.unciv.logic.kotakula

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.ruleset.FactionAction
import com.unciv.models.ruleset.unique.TemporaryUnique
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

/**
 * KC-12: faith actions - "Corporate initiatives" of the Technocrats, "Prayers and rituals" of the Humanists (section 9.4).
 *
 * - Cost = `baseCost × (1 + 0.5 × era number) × speed faith modifier`, era number 0 (Ancient) to 7 (Information).
 * - Discounts (`KM: Faith actions cost [-N]%`) add up, but never beyond -50%.
 * - One long action at a time; `KM: [+N] Faith action slots` adds more. Instant actions need no slot.
 * - After an action ends it is on cooldown (10 turns by default).
 * - Available once the civilization has at least a pantheon (startup).
 */
object FaithActions {

    /** Strongest discount allowed, in percent */
    const val MAX_DISCOUNT_PERCENT = 50
    const val BASE_SLOTS = 1

    @Readonly
    fun getActions(civ: Civilization): List<FactionAction> =
        civ.gameInfo.ruleset.factionActions.values.filter { it.faction != null && FactionRules.canUse(civ, it.faction) }

    /** Total discount in percent, as a negative number, at least -[MAX_DISCOUNT_PERCENT] */
    @Readonly
    fun getDiscountPercent(civ: Civilization): Int {
        var total = 0
        civ.forEachMatchingUnique(UniqueType.KMFaithActionsCost) { total += it.params[0].toInt() }
        return total.coerceIn(-MAX_DISCOUNT_PERCENT, Int.MAX_VALUE)
    }

    /** The cost formula of section 9.4, isolated for tests (T11) */
    @Readonly
    fun computeCost(baseCost: Int, eraNumber: Int, speedModifier: Float, discountPercent: Int): Int {
        val discount = discountPercent.coerceIn(-MAX_DISCOUNT_PERCENT, Int.MAX_VALUE)
        val cost = baseCost * (1 + 0.5f * eraNumber) * speedModifier * (1 + discount / 100f)
        return cost.roundToInt().coerceAtLeast(0)
    }

    @Readonly
    fun getCost(civ: Civilization, action: FactionAction): Int =
        computeCost(action.baseCost, civ.getEraNumber(), civ.gameInfo.speed.faithCostModifier, getDiscountPercent(civ))

    @Readonly
    fun getSlots(civ: Civilization): Int {
        var slots = BASE_SLOTS
        civ.forEachMatchingUnique(UniqueType.KMFaithActionSlots) { slots += it.params[0].toInt() }
        return slots.coerceAtLeast(0)
    }

    @Readonly
    fun getUsedSlots(civ: Civilization): Int = civ.factionState.activeFaithActions.size

    /** Faith actions open up with the first pantheon (startup) */
    @Readonly
    fun areUnlocked(civ: Civilization): Boolean =
        civ.gameInfo.isReligionEnabled() && civ.religionManager.religionState >= ReligionState.Pantheon

    enum class Unavailability(val text: String) {
        Locked("Found a pantheon first"),
        Active("Already active"),
        Cooldown("On cooldown"),
        NoSlot("No free slot"),
        NotEnoughFaith("Not enough Faith"),
        NoCityState("No known city-state"),
    }

    /** Why [civ] cannot start [action] right now, null if it can */
    @Readonly
    fun getUnavailability(civ: Civilization, action: FactionAction): Unavailability? {
        val state = civ.factionState
        return when {
            !areUnlocked(civ) -> Unavailability.Locked
            action.name in state.activeFaithActions -> Unavailability.Active
            (state.faithActionCooldowns[action.name] ?: 0) > 0 -> Unavailability.Cooldown
            !action.isInstant && getUsedSlots(civ) >= getSlots(civ) -> Unavailability.NoSlot
            civ.religionManager.storedFaith < getCost(civ, action) -> Unavailability.NotEnoughFaith
            needsCityState(action) && getChoosableCityStates(civ).isEmpty() -> Unavailability.NoCityState
            else -> null
        }
    }

    @Readonly
    fun canStart(civ: Civilization, action: FactionAction): Boolean = getUnavailability(civ, action) == null

    @Readonly
    fun needsCityState(action: FactionAction): Boolean = action.hasUnique(UniqueType.KMInfluenceWithChosenCityState)

    /** Known, living city-states [civ] could pick for "Mergers and acquisitions" */
    @Readonly
    fun getChoosableCityStates(civ: Civilization): List<Civilization> =
        civ.getKnownCivs().filter { it.isCityState && !it.isAtWarWith(civ) }.toList()

    /**
     * Starts [action] for [civ], paying its Faith.
     * @param chosenCityState the city-state for `KM: [N] Influence with chosen city-state`
     * @return false if the action was not available
     */
    fun start(civ: Civilization, action: FactionAction, chosenCityState: Civilization? = null): Boolean {
        if (!canStart(civ, action)) return false
        val cost = getCost(civ, action)
        civ.religionManager.storedFaith -= cost

        if (action.isInstant) {
            triggerInstantUniques(civ, action, cost, chosenCityState)
            civ.factionState.faithActionCooldowns[action.name] = action.cooldown
        } else {
            for (unique in action.uniqueObjects) {
                val temporary = TemporaryUnique()
                temporary.unique = unique.text
                temporary.turnsLeft = action.duration
                civ.temporaryUniques.add(temporary)
            }
            civ.factionState.activeFaithActions[action.name] = action.duration
            civ.cache.updateCivResources()
            for (city in civ.cities) city.cityStats.update(updateCivStats = false)
            civ.updateStatsForNextTurn()
        }

        civ.addNotification("[${action.name}] has started!", NotificationCategory.Religion,
            "FactionActionIcons/${action.name}", NotificationIcon.Faith)
        return true
    }

    private fun triggerInstantUniques(civ: Civilization, action: FactionAction, cost: Int, chosenCityState: Civilization?) {
        for (unique in action.uniqueObjects) {
            when (unique.type) {
                UniqueType.KMGainPercentOfActionCost -> {
                    val stat = Stat.safeValueOf(unique.params[1]) ?: continue
                    val amount = (cost * unique.params[0].toInt() / 100f).roundToInt()
                    civ.addStat(stat, amount)
                    civ.addNotification("You received [$amount] [${stat.name}] from [${action.name}]",
                        NotificationCategory.Religion, stat.notificationIcon)
                }
                UniqueType.KMInfluenceWithChosenCityState -> {
                    val cityState = chosenCityState ?: getChoosableCityStates(civ).firstOrNull() ?: continue
                    cityState.getDiplomacyManager(civ)?.addInfluence(unique.params[0].toFloat())
                    civ.addNotification("[${cityState.civName}] became a subsidiary. It does not know yet",
                        NotificationCategory.Diplomacy, cityState.civName)
                }
                else -> {
                    val withSource = Unique(unique.text, UniqueTarget.Global, action.name)
                    if (withSource.isTriggerable)
                        UniqueTriggerActivation.triggerUnique(withSource, civ,
                            triggerNotificationText = "due to [${action.name}]")
                }
            }
        }
    }

    /** End of [civ]'s turn: long actions run down, then go on cooldown; cooldowns run down */
    fun onTurnEnd(civ: Civilization) {
        val state = civ.factionState
        val cooldowns = state.faithActionCooldowns
        for (name in cooldowns.keys.toList()) {
            val left = cooldowns[name]!! - 1
            if (left <= 0) cooldowns.remove(name) else cooldowns[name] = left
        }
        val active = state.activeFaithActions
        for (name in active.keys.toList()) {
            val left = active[name]!! - 1
            if (left > 0) {
                active[name] = left
                continue
            }
            active.remove(name)
            val action = civ.gameInfo.ruleset.factionActions[name]
            cooldowns[name] = action?.cooldown ?: 10
            civ.addNotification("[$name] has ended", NotificationCategory.Religion,
                "FactionActionIcons/$name", NotificationIcon.Faith)
        }
    }
}
