package com.unciv.logic.kotakula

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Ruleset
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

/**
 * Central place for the Kotakula Multiverse faction rules that many engine parts ask about.
 *
 * Everything here is inert for rulesets without factions: all civilizations stay [Faction.None],
 * so no civilization is ever "opposing" another and every object is available to everyone.
 */
object FactionRules {

    /** Standard-speed length of the newbie truce (section 2.4) */
    const val STANDARD_TRUCE_TURNS = 25

    /** Turns of the newbie truce per speed name (section 2.4); other speeds scale [STANDARD_TRUCE_TURNS] */
    private val truceTurnsBySpeed = mapOf("Quick" to 17, "Standard" to 25, "Epic" to 38, "Marathon" to 75)

    /** Caps for espionage reveal shares (section 2.7) */
    const val MAX_REVEAL_PERCENT = 75

    /** Does this ruleset use the faction mechanics at all? */
    @Readonly
    fun isFactionRuleset(ruleset: Ruleset): Boolean = ruleset.hasFactions

    @Readonly
    fun isFactionGame(gameInfo: GameInfo): Boolean = gameInfo.ruleset.hasFactions

    /** The faction whose content [civ] may use; `null` means "all content" (barbarians, section 2.6) */
    @Readonly
    fun contentFaction(civ: Civilization): Faction? = when {
        civ.isBarbarian -> null
        civ.isCityState -> civ.cultureFaction
        else -> civ.faction
    }

    /** Implements the availability rule of section 1.2 for an object with [objectFaction] */
    @Readonly
    fun canUse(civ: Civilization, objectFaction: Faction?): Boolean {
        if (objectFaction == null || objectFaction == Faction.None) return true
        val contentFaction = contentFaction(civ) ?: return true
        return contentFaction == objectFaction
    }

    @Readonly
    fun canUse(civ: Civilization, rulesetObject: IRulesetObject): Boolean =
        canUse(civ, getFaction(rulesetObject))

    /** Faction of a ruleset object; policies inherit the faction of their branch (section 8) */
    @Readonly
    fun getFaction(rulesetObject: IRulesetObject): Faction? {
        if (rulesetObject is Policy && rulesetObject.faction == null) {
            val branch = try { rulesetObject.branch } catch (_: UninitializedPropertyAccessException) { return null }
            if (branch !== rulesetObject) return branch.faction
        }
        return rulesetObject.faction
    }

    /** True when [civA] and [civB] are on different real sides - eternal war applies (section 2.3) */
    @Readonly
    fun areOpposing(civA: Civilization, civB: Civilization): Boolean =
        civA.faction.isReal() && civB.faction.isReal() && civA.faction != civB.faction

    /** True when both are on the same real side */
    @Readonly
    fun areSameFaction(civA: Civilization, civB: Civilization): Boolean =
        civA.faction.isReal() && civA.faction == civB.faction

    //region Newbie truce (KC-06)

    @Readonly
    fun truceLength(gameInfo: GameInfo): Int =
        truceTurnsBySpeed[gameInfo.speed.name]
            ?: (STANDARD_TRUCE_TURNS * gameInfo.speed.modifier).roundToInt()

    /** The truce lasts for the first [truceLength] turns: `GameInfo.turns` 0 until [truceLength] - 1 */
    @Readonly
    fun isTruceActive(gameInfo: GameInfo): Boolean =
        isFactionGame(gameInfo) && gameInfo.turns < truceLength(gameInfo)

    /** Turns of truce left including the current one, 0 when it is over */
    @Readonly
    fun truceTurnsLeft(gameInfo: GameInfo): Int =
        if (!isFactionGame(gameInfo)) 0
        else (truceLength(gameInfo) - gameInfo.turns).coerceAtLeast(0)

    /** Is the truce protecting [civA] and [civB] from each other right now? */
    @Readonly
    fun isTruceBetween(civA: Civilization, civB: Civilization): Boolean =
        areOpposing(civA, civB) && isTruceActive(civA.gameInfo)

    //endregion

    /** Section 2.2 / 11.3: is the faction victory possible in this game? */
    @Readonly
    fun isFactionVictoryEnabled(gameInfo: GameInfo): Boolean =
        isFactionGame(gameInfo) && !gameInfo.factionGameState.factionVictoryDisabled

    /** Major civilizations of [faction] that are still alive */
    @Readonly
    fun aliveMajorCivsOf(gameInfo: GameInfo, faction: Faction): List<Civilization> =
        gameInfo.civilizations.filter { it.isMajorCiv() && it.isAlive() && it.faction == faction }
}
