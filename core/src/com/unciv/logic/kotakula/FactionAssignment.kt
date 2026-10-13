package com.unciv.logic.kotakula

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PopupAlert
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.random.Random

/**
 * KC-02 / KC-03 / KC-16: who is on which side.
 *
 * - AI major civilizations get a 50/50 lottery at game creation, seeded by the game seed and the civ index.
 * - City-states get a hidden "culture" faction by the same lottery (section 2.5), their diplomatic faction stays None.
 * - Human players choose on their first turn through the `ChooseFaction` event of Events.json; the event choices
 *   trigger [UniqueType.KMSetFaction][com.unciv.models.ruleset.unique.UniqueType.KMSetFaction].
 * - A human whose turn is played automatically (skipped or resigned in multiplayer) gets the AI lottery.
 * - Once every major civilization has a side, the game checks that both sides exist (section 2.2).
 */
object FactionAssignment {

    /** Name of the event in Events.json that shows the faction choice window */
    const val CHOOSE_FACTION_EVENT = "ChooseFaction"

    /** The lottery of section 2.2: `Random(gameSeed + civIndex)`, 50/50, no balancing */
    @Pure
    fun lottery(gameSeed: Long, civIndex: Int): Faction =
        if (Random(gameSeed + civIndex).nextBoolean()) Faction.Technocrat else Faction.Humanist

    @Readonly
    private fun getGameSeed(gameInfo: GameInfo): Long = gameInfo.tileMap.mapParameters.seed

    /** Lottery result for [civ] in its game */
    @Readonly
    fun lotteryFor(civ: Civilization): Faction =
        lottery(getGameSeed(civ.gameInfo), civ.gameInfo.civilizations.indexOf(civ))

    /** At game creation (after civilization transients are set): AI sides, city-state culture sides, choice prompts */
    fun assignAtGameStart(gameInfo: GameInfo) {
        if (!FactionRules.isFactionGame(gameInfo)) return
        val state = gameInfo.factionGameState
        if (state.factionsAssigned) return
        state.factionsAssigned = true

        for (civ in gameInfo.civilizations) {
            if (civ.isBarbarian || civ.isSpectator()) continue
            when {
                civ.isCityState -> {
                    civ.cultureFaction = lotteryFor(civ)
                    civ.cache.updateUniqueObjects()
                }
                civ.isMajorCiv() && !civ.isHuman() -> setFaction(civ, lotteryFor(civ), notify = false)
                civ.isMajorCiv() -> promptChoice(civ)
            }
        }
        checkFactionPresence(gameInfo)
    }

    /** Queue the faction choice window for a human without a side (once) */
    fun promptChoice(civ: Civilization) {
        if (!needsChoice(civ)) return
        if (civ.popupAlerts.any { it.type == AlertType.Event && it.value == CHOOSE_FACTION_EVENT }) return
        if (civ.gameInfo.ruleset.events[CHOOSE_FACTION_EVENT] == null) {
            // Ruleset without the event - fall back to the lottery rather than blocking the game forever
            setFaction(civ, lotteryFor(civ), notify = true)
            return
        }
        civ.popupAlerts.add(0, PopupAlert(AlertType.Event, CHOOSE_FACTION_EVENT))
    }

    /** Does [civ] still have to pick a side (and can therefore not end its turn)? */
    @Readonly
    fun needsChoice(civ: Civilization): Boolean =
        FactionRules.isFactionGame(civ.gameInfo) && civ.isMajorCiv() && !civ.isSpectator() && civ.faction == Faction.None

    /** Turn start hook: humans without a side get the window again */
    fun onTurnStart(civ: Civilization) {
        if (civ.isHuman()) promptChoice(civ)
    }

    /** A civilization played by the automation (AI, skipped or resigned human) without a side gets the AI lottery */
    fun ensureFactionForAutomatedCiv(civ: Civilization) {
        if (!needsChoice(civ)) return
        setFaction(civ, lotteryFor(civ), notify = civ.isHuman())
        civ.popupAlerts.removeAll { it.type == AlertType.Event && it.value == CHOOSE_FACTION_EVENT }
    }

    /**
     * Gives [civ] its side, permanently (section 2.1: "this was a release, not a beta").
     * Applies everything at once: available content, resource visibility, eternal war with known enemies.
     */
    fun setFaction(civ: Civilization, faction: Faction, notify: Boolean = true) {
        if (!faction.isReal() || civ.faction.isReal()) return
        civ.faction = faction
        civ.cultureFaction = faction
        civ.cache.updateUniqueObjects()
        civ.cache.updateCivResources()
        civ.updateStatsForNextTurn()
        for (city in civ.cities) city.reassignPopulationDeferred()

        if (notify && !civ.factionState.factionChoiceAnnounced) {
            civ.factionState.factionChoiceAnnounced = true
            val text = if (faction == Faction.Technocrat)
                "You chose the path of the Technocrat. The Humanists already know and are offended."
            else "You chose the path of the Humanist. The Technocrats already know and have drawn up a table of complaints."
            civ.addNotification(text, NotificationCategory.General, "FactionIcons/${faction.name}")
        }

        EternalWar.onFactionChosen(civ)
        checkFactionPresence(civ.gameInfo)
    }

    /**
     * Section 2.2: when one side has no civilization at all (humans included), the faction victory is cancelled
     * and everyone is told so. Runs once, when every major civilization has a side.
     */
    fun checkFactionPresence(gameInfo: GameInfo) {
        val state = gameInfo.factionGameState
        if (state.factionPresenceChecked || !FactionRules.isFactionGame(gameInfo)) return
        val majors = gameInfo.civilizations.filter { it.isMajorCiv() && !it.isSpectator() }
        if (majors.any { it.faction == Faction.None }) return
        state.factionPresenceChecked = true

        val missing = Faction.playable.firstOrNull { faction -> majors.none { it.faction == faction } } ?: return
        state.factionVictoryDisabled = true
        val text = if (missing == Faction.Technocrat)
            "Nobody in this multiverse wanted to be a Technocrat. The faction victory is cancelled for lack of an enemy. You may fight each other like in the good old days"
        else "Nobody in this multiverse wanted to be a Humanist. The faction victory is cancelled for lack of an enemy. You may fight each other like in the good old days"
        for (civ in gameInfo.civilizations)
            if (civ.isMajorCiv()) civ.addNotification(text, NotificationCategory.General, "FactionIcons/${missing.name}")
    }
}
