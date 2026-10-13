package com.unciv.logic.kotakula

import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import yairm210.purity.annotations.Readonly

/**
 * KC-06: the newbie truce (section 2.4).
 *
 * For the first [FactionRules.truceLength] turns, civilizations of different factions may not attack cities,
 * capture civilians, pillage or enter city tiles of each other. Fighting military units and moving through
 * foreign land is allowed.
 *
 * The checks themselves are spread over the engine (TargetHelper, UnitMovement, UnitActionsPillage) and all
 * go through [FactionRules.isTruceBetween] or [protectsTarget].
 */
object NewbieTruce {

    /** Does the truce protect [target] from an attack by [attacker]? Cities and civilians are protected, military units are not */
    @Readonly
    fun protectsTarget(attacker: Civilization, target: ICombatant): Boolean {
        if (!FactionRules.isTruceBetween(attacker, target.getCivInfo())) return false
        return when (target) {
            is CityCombatant -> true
            is MapUnitCombatant -> target.unit.isCivilian()
            else -> false
        }
    }

    /** Top bar text, e.g. "Truce: [12] turns left"; null when there is no truce */
    @Readonly
    fun getTopBarText(civ: Civilization): String? {
        if (!civ.isMajorCiv() || !FactionRules.isTruceActive(civ.gameInfo)) return null
        return "Truce: [${FactionRules.truceTurnsLeft(civ.gameInfo)}] turns left"
    }

    /** At the start of the last truce turn every major civilization is warned */
    fun onTurnStart(civ: Civilization) {
        val gameInfo = civ.gameInfo
        if (!FactionRules.isFactionGame(gameInfo) || !civ.isMajorCiv()) return
        if (civ.factionState.truceEndAnnounced) return
        if (FactionRules.truceTurnsLeft(gameInfo) != 1) return
        civ.factionState.truceEndAnnounced = true
        civ.addNotification("The truce is ending. Time to get out the calculators and the staffs",
            NotificationCategory.War, NotificationIcon.War)
    }
}
