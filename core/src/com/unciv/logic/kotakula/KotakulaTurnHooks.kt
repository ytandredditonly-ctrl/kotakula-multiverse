package com.unciv.logic.kotakula

import com.unciv.logic.civilization.Civilization

/**
 * Single entry point from the turn loop ([com.unciv.logic.civilization.managers.TurnManager]) into the
 * Kotakula Multiverse mechanics, so the engine files only get one call each.
 */
object KotakulaTurnHooks {

    /** Start of [civ]'s turn, after the vanilla start-of-turn processing */
    fun onTurnStart(civ: Civilization) {
        if (!FactionRules.isFactionGame(civ.gameInfo) || civ.isSpectator()) return
        FactionAssignment.onTurnStart(civ)
        NewbieTruce.onTurnStart(civ)
        FactionEspionage.onTurnStart(civ)
        Summoning.onTurnStart(civ)
    }

    /** End of [civ]'s turn, before victory is checked */
    fun onTurnEnd(civ: Civilization) {
        if (!FactionRules.isFactionGame(civ.gameInfo) || civ.isSpectator()) return
        FaithActions.onTurnEnd(civ)
        FactionVictory.checkFactionVictory(civ.gameInfo)
    }

    /** Before the automation plays [civ]'s turn (AI, autoplay, skipped multiplayer turns) */
    fun beforeAutomatedTurn(civ: Civilization) {
        if (!FactionRules.isFactionGame(civ.gameInfo)) return
        FactionAssignment.ensureFactionForAutomatedCiv(civ)
    }
}
