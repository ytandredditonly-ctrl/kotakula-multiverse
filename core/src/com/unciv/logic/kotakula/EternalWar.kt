package com.unciv.logic.kotakula

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DeclareWarReason
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.WarType
import yairm210.purity.annotations.Readonly

/**
 * KC-05: eternal war between the factions (section 2.3).
 *
 * - War is declared automatically when two civilizations of different factions meet (or when a civilization
 *   that already knows someone of the other side picks its faction).
 * - Peace, trades, embassies, open borders, denouncements, demands and friendship are impossible between them.
 * - No warmonger penalty for anything done to the other faction.
 * - Conquering a city of the other faction makes the conqueror a "Hero of the faction" for its own side.
 * - Attacking a civilization of one's own faction marks a "Traitor of the faction" for the whole side.
 */
object EternalWar {

    /** "Hero of the faction": +10 per conquered city of the other side, decays by 1 every 5 turns */
    const val HERO_OF_FACTION_PER_CITY = 10f
    const val HERO_OF_FACTION_DECAY_TURNS = 5

    /** "Traitor of the faction": -20, decays by 1 every 4 turns */
    const val TRAITOR_OF_FACTION = -20f
    const val TRAITOR_OF_FACTION_DECAY_TURNS = 4

    /** Opinion of a civilization of the other faction when the game is not irreconcilable */
    const val OTHER_FACTION_OPINION = -10f
    /** Opinion of a civilization of the same faction when the game is not irreconcilable */
    const val SAME_FACTION_OPINION = 10f

    /**
     * Called after [civA] and [civB] met: if they are on different sides, the eternal war starts at once.
     * Without irreconcilability (game setting) there is no war, only the faction opinions.
     */
    fun onCivilizationsMet(civA: Civilization, civB: Civilization) {
        if (!FactionRules.isIrreconcilable(civA.gameInfo)) return setFactionOpinions(civA, civB)
        if (!FactionRules.areOpposing(civA, civB)) return
        declare(civA, civB)
    }

    /** Called when [civ] gets its faction: start the eternal war with everyone of the other side it already knows */
    fun onFactionChosen(civ: Civilization) {
        val irreconcilable = FactionRules.isIrreconcilable(civ.gameInfo)
        for (otherCiv in civ.getKnownCivs().toList())
            if (!irreconcilable) setFactionOpinions(civ, otherCiv)
            else if (FactionRules.areOpposing(civ, otherCiv)) declare(civ, otherCiv)
    }

    /** Game without irreconcilability: major civilizations like their own faction (+10) and dislike the other one (-10) */
    private fun setFactionOpinions(civA: Civilization, civB: Civilization) {
        if (!civA.isMajorCiv() || !civB.isMajorCiv()) return
        val opinion = when {
            FactionRules.areOpposing(civA, civB) -> DiplomaticModifiers.OtherFaction to OTHER_FACTION_OPINION
            FactionRules.areSameFaction(civA, civB) -> DiplomaticModifiers.SameFaction to SAME_FACTION_OPINION
            else -> return
        }
        for ((civ, otherCiv) in listOf(civA to civB, civB to civA))
            civ.getDiplomacyManager(otherCiv)?.setModifier(opinion.first, opinion.second)
    }

    private fun declare(civA: Civilization, civB: Civilization) {
        val diplomacy = civA.getDiplomacyManager(civB) ?: return
        if (civA.isAtWarWith(civB)) return
        diplomacy.declareWar(DeclareWarReason(WarType.EternalWar))
    }

    /** Notifications for [WarType.EternalWar] - replaces the usual "X has declared war on us!" */
    fun notifyOfWar(diplomacyManager: DiplomacyManager) {
        val civ = diplomacyManager.civInfo
        val otherCiv = diplomacyManager.otherCiv
        civ.addNotification("[${otherCiv.civName}] belongs to the other faction. The eternal war has begun!",
            NotificationCategory.Diplomacy, otherCiv.civName, NotificationIcon.War, civ.civName)
        otherCiv.addNotification("[${civ.civName}] belongs to the other faction. The eternal war has begun!",
            NotificationCategory.Diplomacy, civ.civName, NotificationIcon.War, otherCiv.civName)
    }

    //region Diplomatic restrictions

    /** Is any diplomacy apart from war forbidden between these two? Only in irreconcilable games (game setting) */
    @Readonly
    fun isDiplomacyForbidden(civA: Civilization, civB: Civilization): Boolean =
        FactionRules.areOpposing(civA, civB) && FactionRules.isIrreconcilable(civA.gameInfo)

    /** Can [civ] make peace (or a truce/pact/alliance) with [otherCiv]? */
    @Readonly
    fun canMakePeace(civ: Civilization, otherCiv: Civilization): Boolean = !isDiplomacyForbidden(civ, otherCiv)

    //endregion

    //region Opinions

    /** [conqueror] took a city from the other faction: everyone of its own side likes it more */
    fun onOpposingCityConquered(conqueror: Civilization) {
        for (ally in conqueror.gameInfo.civilizations) {
            if (ally == conqueror || !ally.isMajorCiv() || ally.isDefeated()) continue
            if (!FactionRules.areSameFaction(ally, conqueror)) continue
            val diplomacy = ally.getDiplomacyManager(conqueror) ?: continue
            diplomacy.addModifier(DiplomaticModifiers.HeroOfFaction, HERO_OF_FACTION_PER_CITY)
        }
    }

    /** [aggressor] declared war on [victim] of its own faction: the whole side remembers the betrayal */
    fun onWarDeclaredWithinFaction(aggressor: Civilization, victim: Civilization) {
        if (!FactionRules.areSameFaction(aggressor, victim)) return
        for (witness in aggressor.gameInfo.civilizations) {
            if (witness == aggressor || !witness.isMajorCiv() || witness.isDefeated()) continue
            if (!FactionRules.areSameFaction(witness, aggressor)) continue
            val diplomacy = witness.getDiplomacyManager(aggressor) ?: continue
            diplomacy.addModifier(DiplomaticModifiers.TraitorOfFaction, TRAITOR_OF_FACTION)
        }
    }

    //endregion
}
