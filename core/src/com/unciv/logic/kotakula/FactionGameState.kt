package com.unciv.logic.kotakula

import com.unciv.logic.IsPartOfGameInfoSerialization

/** Serialized game-wide state of the Kotakula Multiverse mechanics, held by [com.unciv.logic.GameInfo]. */
class FactionGameState : IsPartOfGameInfoSerialization {
    /** KC-03/KC-16: AI civs and city-states got their faction lottery */
    var factionsAssigned = false

    /** Section 2.2: the "does every faction exist" check ran (after every human chose a side) */
    var factionPresenceChecked = false

    /** Section 2.2 / 11.3: faction victory was cancelled because one faction never existed in this game */
    var factionVictoryDisabled = false

    /** Section 11.3: the faction that won the faction victory, [Faction.None] while nobody did */
    var winningFaction = Faction.None

    /** World split (game setting): civID of a human without a side -> the start reserved for it in the other half */
    var splitAlternateStarts = HashMap<String, com.unciv.logic.map.HexCoord>()

    fun clone(): FactionGameState {
        val toReturn = FactionGameState()
        toReturn.factionsAssigned = factionsAssigned
        toReturn.factionPresenceChecked = factionPresenceChecked
        toReturn.factionVictoryDisabled = factionVictoryDisabled
        toReturn.winningFaction = winningFaction
        toReturn.splitAlternateStarts = HashMap(splitAlternateStarts)
        return toReturn
    }
}
