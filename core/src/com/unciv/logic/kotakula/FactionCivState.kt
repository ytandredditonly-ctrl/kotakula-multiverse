package com.unciv.logic.kotakula

import com.unciv.logic.IsPartOfGameInfoSerialization

/**
 * Serialized per-civilization state of the Kotakula Multiverse mechanics.
 *
 * Kept in one place so [com.unciv.logic.civilization.Civilization] only gets a single extra field.
 */
class FactionCivState : IsPartOfGameInfoSerialization {

    /** KC-12: long faith actions currently running, action name to turns left */
    var activeFaithActions = HashMap<String, Int>()

    /** KC-12: faith actions on cooldown, action name to turns left */
    var faithActionCooldowns = HashMap<String, Int>()

    /** KC-13: ids (as strings, to keep Gdx Json happy) of opposing-faction units revealed by espionage until our next turn starts */
    var revealedUnitIds = HashSet<String>()

    /** KC-21: turns since each summoning source last summoned, keyed by "sourceName|unitType" */
    var summonCounters = HashMap<String, Int>()

    /** KC-02: the "You chose the path..." notification was already sent */
    var factionChoiceAnnounced = false

    /** KC-06: the "truce ends" notification was already sent */
    var truceEndAnnounced = false

    /** KC-11: the free follower belief for entering the Industrial era was already granted */
    var industrialBeliefGranted = false

    /** Gods and startups: turn the current pantheon was taken (free trial), -1 without a pantheon */
    var pantheonTurn = -1

    /** Gods and startups: the one pivot of the game was used */
    var pivotUsed = false

    /** Three-layer map (7.1): tiles terraformed by this civilization, at most 3 per city */
    var layerTerraformedTiles = 0

    /** Three-layer map (6.2): layers the AI has finished exploring - its explorer is back to normal duty */
    var layersExplored = HashSet<String>()

    fun clone(): FactionCivState {
        val toReturn = FactionCivState()
        toReturn.activeFaithActions.putAll(activeFaithActions)
        toReturn.faithActionCooldowns.putAll(faithActionCooldowns)
        toReturn.revealedUnitIds.addAll(revealedUnitIds)
        toReturn.summonCounters.putAll(summonCounters)
        toReturn.factionChoiceAnnounced = factionChoiceAnnounced
        toReturn.truceEndAnnounced = truceEndAnnounced
        toReturn.industrialBeliefGranted = industrialBeliefGranted
        toReturn.pantheonTurn = pantheonTurn
        toReturn.pivotUsed = pivotUsed
        toReturn.layerTerraformedTiles = layerTerraformedTiles
        toReturn.layersExplored.addAll(layersExplored)
        return toReturn
    }
}
