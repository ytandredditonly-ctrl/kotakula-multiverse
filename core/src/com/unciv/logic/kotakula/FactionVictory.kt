package com.unciv.logic.kotakula

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.Victory
import yairm210.purity.annotations.Readonly

/**
 * KC-15: victories by faction (section 11).
 *
 * - A victory with a `faction` field is available and shown only to civilizations of that faction.
 * - The faction victory (`KM: Destroy all civilizations of the opposing faction` milestone) is won by a whole side
 *   when no major civilization of the other side is alive. All living civilizations of the winning side are winners.
 * - It is disabled when one side never existed in this game (section 2.2).
 * - City-states and barbarians take no part.
 */
object FactionVictory {

    /** A nation or personality preferring "Scientific" prefers the science victory of its own faction */
    const val SCIENTIFIC_PREFERENCE = "Scientific"

    @Readonly
    fun mapPreferredVictory(civ: Civilization, preference: String): String {
        if (preference != SCIENTIFIC_PREFERENCE || preference in civ.gameInfo.ruleset.victories) return preference
        return civ.gameInfo.ruleset.victories.values.firstOrNull { victory ->
            victory.faction != null && victory.faction == civ.faction
                && victory.milestoneObjects.any { it.type == MilestoneType.AddedSSPartsInCapital }
        }?.name ?: preference
    }

    /** Is [victory] available to [civ] as far as factions go? */
    @Readonly
    fun isAvailableTo(victory: Victory, civ: Civilization): Boolean {
        if (!FactionRules.canUse(civ, victory.faction)) return false
        if (isFactionVictory(victory) && !FactionRules.isFactionVictoryEnabled(civ.gameInfo)) return false
        return true
    }

    @Readonly
    fun isFactionVictory(victory: Victory): Boolean =
        victory.milestoneObjects.any { it.type == MilestoneType.DestroyOpposingFaction }

    /** Milestone check: [civ]'s side has destroyed the other side */
    @Readonly
    fun hasDestroyedOpposingFaction(civ: Civilization): Boolean {
        val gameInfo = civ.gameInfo
        if (!FactionRules.isFactionVictoryEnabled(gameInfo) || !civ.isMajorCiv() || !civ.faction.isReal()) return false
        // Both sides must have existed - every major civ has picked its side by now
        if (!gameInfo.factionGameState.factionPresenceChecked) return false
        return FactionRules.aliveMajorCivsOf(gameInfo, civ.faction.opposite()).isEmpty()
    }

    /** End of every turn: remember the winning side, for the victory screen */
    fun checkFactionVictory(gameInfo: GameInfo) {
        val state = gameInfo.factionGameState
        if (state.winningFaction.isReal() || !FactionRules.isFactionVictoryEnabled(gameInfo)) return
        if (!state.factionPresenceChecked) return
        for (faction in Faction.playable) {
            if (FactionRules.aliveMajorCivsOf(gameInfo, faction).isEmpty()) continue
            if (FactionRules.aliveMajorCivsOf(gameInfo, faction.opposite()).isNotEmpty()) continue
            state.winningFaction = faction
            return
        }
    }

    /** Is [civ] among the winners of the game's victory? Handles the shared faction victory. */
    @Readonly
    fun isWinner(gameInfo: GameInfo, civ: Civilization): Boolean {
        val victoryData = gameInfo.victoryData ?: return false
        if (victoryData.winningCiv == civ.civID) return true
        val victory = gameInfo.ruleset.victories[victoryData.victoryType] ?: return false
        if (!isFactionVictory(victory)) return false
        return civ.isMajorCiv() && civ.faction.isReal() && civ.faction == victoryData.winningCivObject.faction
    }

    /** All winners of a faction victory (living civilizations of the winning side) */
    @Readonly
    fun getWinners(gameInfo: GameInfo): List<Civilization> {
        val victoryData = gameInfo.victoryData ?: return emptyList()
        val victory = gameInfo.ruleset.victories[victoryData.victoryType]
        if (victory == null || !isFactionVictory(victory)) return listOf(victoryData.winningCivObject)
        return FactionRules.aliveMajorCivsOf(gameInfo, victoryData.winningCivObject.faction)
    }
}
