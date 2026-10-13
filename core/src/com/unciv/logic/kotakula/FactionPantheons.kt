package com.unciv.logic.kotakula

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.TemporaryUnique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.SplittableRandom

/**
 * Gods (Humanists) and startups (Technocrats): the faction pantheons of the "gods and startups" addendum.
 *
 * - effects of a pantheon that act on units or the whole civilization belong to the civ that took it
 *   ([civWideTypes], see [com.unciv.models.Religion.pantheonCivUniqueMap]);
 * - Thunder Grandpa's smite, the gift of Chaos, the free trial and Open Source ([UniqueType.KMSmite] etc.);
 * - the God's Whim and Investor Round events every 25 turns, and the one pivot of a startup.
 */
object FactionPantheons {
    const val GODS_WHIM_EVENT = "GodsWhim"
    const val INVESTOR_ROUND_EVENT = "InvestorRound"
    /** Placeholder of the God's Whim text replaced by the name of the god */
    const val PANTHEON_PLACEHOLDER = "[pantheon]"

    const val EVENT_INTERVAL = 25
    const val GIFT_DURATION = 10
    const val GIFT_FAITH_BONUS = 20
    const val IGNORE_UNHAPPINESS = 2
    const val PIVOT_COST = 50
    const val SMITE_RADIUS = 3
    val chaosStats = listOf(Stat.Production, Stat.Gold, Stat.Culture, Stat.Science, Stat.Faith)

    /** Pantheon uniques that act on units or the whole civ, so they work for the civ that took the pantheon */
    val civWideTypes = setOf(
        UniqueType.Movement, UniqueType.Strength, UniqueType.GoldFromEncampmentsAndCities, UniqueType.SpecificImprovementTime,
        UniqueType.KMPillagingYields, UniqueType.KMSummons, UniqueType.KMSmite, UniqueType.KMChaosGift,
    )

    @Readonly
    fun pantheonOf(civ: Civilization): Belief? =
        civ.religionManager.religion?.getBeliefs(BeliefType.Pantheon)?.firstOrNull()

    /** 25 turns on Standard speed, scaled like everything else by the game speed */
    @Readonly
    fun eventInterval(civ: Civilization): Int = max(1, (EVENT_INTERVAL * civ.gameInfo.speed.modifier).roundToInt())

    /** Called when [civ] takes a pantheon (founding or pivot): the free trial counts from here */
    fun onPantheonTaken(civ: Civilization) {
        civ.factionState.pantheonTurn = civ.gameInfo.turns
    }

    // region Free trial and Open Source (city stats)

    /** "KM: Free trial [stats] for [N] turns then [stats]": is the first part still running for the pantheon of [civ]? */
    @Readonly
    fun isFreeTrialActive(civ: Civilization?, turns: Int): Boolean {
        if (civ == null) return false
        val start = civ.factionState.pantheonTurn
        return start >= 0 && civ.gameInfo.turns - start < turns
    }

    /** "KM: [stats] in all cities per other living civilization of your faction" */
    @Readonly
    fun otherLivingCivsOfFaction(civ: Civilization): Int {
        if (!civ.faction.isReal()) return 0
        return FactionRules.aliveMajorCivsOf(civ.gameInfo, civ.faction).count { it != civ }
    }

    // endregion

    // region Turn start: smite, Chaos, events

    fun onTurnStart(civ: Civilization) {
        if (!civ.isMajorCiv() || civ.isDefeated()) return
        smite(civ)
        chaosGift(civ)
        triggerEvents(civ)
    }

    /**
     * Roll 0..100 of a unit on this turn: a Weyl sequence (golden ratio step) from a random start per unit.
     * Each turn looks random, but over many turns exactly P% of them hit - no streak of five lightnings in a row
     */
    fun smiteRoll(civ: Civilization, unitId: Int): Double {
        val start = SplittableRandom(civ.gameInfo.tileMap.mapParameters.seed * 31 + unitId).nextDouble()
        val value = (start + civ.gameInfo.turns * 0.6180339887498949) % 1.0
        return value * 100
    }

    /** "KM: Smite [N] damage with [P]% chance": opposing units within 3 tiles of our cities, never killed */
    fun smite(civ: Civilization): Int {
        var hits = 0
        for (unique in civ.getMatchingUniques(UniqueType.KMSmite)) {
            val damage = unique.params[0].toInt()
            val chance = unique.params[1].toInt()
            val targets = civ.cities.asSequence()
                .flatMap { it.getCenterTile().getTilesInDistance(SMITE_RADIUS) }
                .flatMap { it.getUnits() }
                .filter { FactionRules.areOpposing(civ, it.civ) }
                .distinct().toList()
            for (unit in targets) {
                if (smiteRoll(civ, unit.id) >= chance) continue
                hits++
                unit.health = max(1, unit.health - damage)
                civ.addNotification("Thunder Grandpa is displeased with [${unit.name}]",
                    LocationAction(unit.getTile().position), NotificationCategory.War, NotificationIcon.War, unit.name)
                unit.civ.addNotification("Lightning struck our [${unit.name}]",
                    LocationAction(unit.getTile().position), NotificationCategory.War, NotificationIcon.War, unit.name)
            }
        }
        return hits
    }

    /**
     * The stat Chaos gives on this turn: a random start from gameSeed + civIndex, then a golden ratio step per turn.
     * Unpredictable for the player, but every stat comes out as often over a game (open question 17)
     */
    fun chaosStat(civ: Civilization): Stat {
        val civIndex = civ.gameInfo.civilizations.indexOf(civ)
        val start = SplittableRandom(civ.gameInfo.tileMap.mapParameters.seed + civIndex).nextDouble()
        val value = (start + civ.gameInfo.turns * 0.6180339887498949) % 1.0
        return chaosStats[(value * chaosStats.size).toInt().coerceIn(0, chaosStats.size - 1)]
    }

    /** "KM: Chaos gift every [N] turns": 25 x (era number + 1) of a random stat */
    fun chaosGift(civ: Civilization) {
        val turns = civ.gameInfo.turns
        for (unique in civ.getMatchingUniques(UniqueType.KMChaosGift)) {
            val interval = unique.params[0].toInt()
            if (turns == 0 || turns % interval != 0) continue
            val stat = chaosStat(civ)
            val amount = 25 * (civ.getEraNumber() + 1)
            civ.addStat(stat, amount)
            civ.addNotification("Chaos gave you [$amount] [${stat.name}]. Or took it away - it is not sure itself",
                NotificationCategory.General, NotificationIcon.Faith, "StatIcons/${stat.name}")
        }
    }

    /** Is the God's Whim / Investor Round of [civ] due this turn? */
    @Readonly
    fun isEventDue(civ: Civilization): Boolean {
        val turns = civ.gameInfo.turns
        return turns > 0 && turns % eventInterval(civ) == 0 && pantheonOf(civ) != null
    }

    @Readonly
    fun eventFor(civ: Civilization): String? = when (civ.faction) {
        Faction.Humanist -> GODS_WHIM_EVENT
        Faction.Technocrat -> INVESTOR_ROUND_EVENT
        else -> null
    }

    private fun triggerEvents(civ: Civilization) {
        if (!isEventDue(civ)) return
        val eventName = eventFor(civ) ?: return
        if (civ.gameInfo.ruleset.events[eventName] == null) return
        if (civ.isAI()) {
            decideForAI(civ, eventName)
            return
        }
        if (civ.popupAlerts.none { it.type == AlertType.Event && it.value == eventName })
            civ.popupAlerts.add(PopupAlert(AlertType.Event, eventName))
    }

    /** AI of the addendum, section 3 */
    fun decideForAI(civ: Civilization, eventName: String) {
        when (eventName) {
            GODS_WHIM_EVENT -> {
                val cost = giftCost(civ)
                val faithLeft = civ.religionManager.storedFaith - cost
                if (faithLeft >= 0 && faithLeft >= civ.religionManager.faithForNextGreatProphet()) bringGift(civ)
                else ignoreGod(civ)
            }
            INVESTOR_ROUND_EVENT -> {
                val atWarWithOtherFaction = civ.getKnownCivs().any { FactionRules.areOpposing(civ, it) && civ.isAtWarWith(it) }
                if (civ.gold < 100 || atWarWithOtherFaction) takeInvestorMoney(civ)
            }
        }
    }

    // endregion

    // region Event choices

    /** "[pantheon] looks at you..." with the name of the god of [civ] */
    @Readonly
    fun eventText(text: String, civ: Civilization): String {
        if (!text.contains(PANTHEON_PLACEHOLDER)) return text
        val name = pantheonOf(civ)?.name ?: return text
        return text.replace(PANTHEON_PLACEHOLDER, "[$name]")
    }

    @Readonly
    fun giftCost(civ: Civilization): Int = 30 * (civ.getEraNumber() + 1)

    @Readonly
    fun canAffordGift(civ: Civilization): Boolean = civ.religionManager.storedFaith >= giftCost(civ)

    fun bringGift(civ: Civilization): Boolean {
        if (!canAffordGift(civ)) return false
        civ.religionManager.storedFaith -= giftCost(civ)
        addTemporary(civ, "[+$GIFT_FAITH_BONUS]% [Faith] [in all cities]")
        return true
    }

    fun ignoreGod(civ: Civilization): Boolean {
        addTemporary(civ, "[-$IGNORE_UNHAPPINESS Happiness] [in capital]")
        return true
    }

    /** Same way as the long faith actions: a temporary unique of the civ, then every stat recomputed */
    private fun addTemporary(civ: Civilization, unique: String) {
        val temporary = TemporaryUnique()
        temporary.unique = unique
        temporary.turnsLeft = GIFT_DURATION
        civ.temporaryUniques.add(temporary)
        for (city in civ.cities) city.cityStats.update(updateCivStats = false)
        civ.updateStatsForNextTurn()
    }

    fun takeInvestorMoney(civ: Civilization): Boolean {
        val faith = civ.religionManager.storedFaith
        civ.addGold(faith / 2)
        civ.religionManager.storedFaith -= faith / 4
        return true
    }

    // endregion

    // region Pivot

    @Readonly
    fun canPivot(civ: Civilization): Boolean =
        civ.faction == Faction.Technocrat
            && civ.hasUnique(UniqueType.KMPivot)
            && !civ.factionState.pivotUsed
            && pantheonOf(civ) != null
            && civ.religionManager.religionState < com.unciv.logic.civilization.managers.ReligionState.Religion
            && civ.religionManager.storedFaith >= pivotCost(civ)

    @Readonly
    fun pivotCost(civ: Civilization): Int = (PIVOT_COST * civ.gameInfo.speed.faithCostModifier).roundToInt()

    /** Startups of the faction nobody holds, the current one excluded */
    @Readonly
    fun freePantheons(civ: Civilization): List<Belief> = civ.gameInfo.ruleset.beliefs.values
        .filter { it.type == BeliefType.Pantheon && FactionRules.canUse(civ, it) }
        .filter { civ.religionManager.getReligionWithBelief(it) == null }

    /** Replaces the startup of [civ] by [newPantheon]: once per game, before founding a corporation */
    fun pivot(civ: Civilization, newPantheon: Belief): Boolean {
        if (!canPivot(civ) || newPantheon !in freePantheons(civ)) return false
        val religion = civ.religionManager.religion ?: return false
        val old = pantheonOf(civ) ?: return false
        civ.religionManager.storedFaith -= pivotCost(civ)
        religion.replaceBelief(old, newPantheon)
        civ.factionState.pivotUsed = true
        onPantheonTaken(civ)
        civ.cities.forEach { it.cityStats.update() }
        civ.addNotification("We are no longer [${old.name}]. Now we are [${newPantheon.name}]. The investors did not notice",
            NotificationCategory.Religion, NotificationIcon.Faith)
        return true
    }

    // endregion

    @Readonly
    fun isAtPeace(civ: Civilization, other: Civilization) = civ.getDiplomacyManager(other)?.diplomaticStatus == DiplomaticStatus.Peace
}
