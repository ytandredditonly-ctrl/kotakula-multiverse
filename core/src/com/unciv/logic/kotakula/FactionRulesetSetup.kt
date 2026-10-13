package com.unciv.logic.kotakula

import com.unciv.models.ruleset.Ruleset

/**
 * Load-time completion of faction content (section 6): upgrade chains are described once, on the
 * Technocrat side. A faction unit that `replaces` a mirror unit and has no `upgradesTo` of its own
 * inherits the mirror's upgrade, translated to its own faction's equivalent when one exists.
 *
 * Example: `Initiate Guard` replaces `Phalanx` (upgrades to `Pikeman`) -> `Initiate Guard` upgrades to `Rune Guard`,
 * the Humanist unit replacing `Pikeman`.
 */
object FactionRulesetSetup {

    fun completeFactionContent(ruleset: Ruleset) {
        if (!ruleset.hasFactions) return
        val units = ruleset.units
        // Same-faction replacement lookup: (faction, replaced unit) -> replacing unit
        val replacements = units.values
            .filter { it.faction != null && it.replaces != null }
            .associateBy { it.faction!! to it.replaces!! }

        for (unit in units.values) {
            val faction = unit.faction ?: continue
            val mirrorName = unit.replaces ?: continue
            if (unit.upgradesTo != null) continue
            val mirror = units[mirrorName] ?: continue
            val mirrorUpgrade = mirror.upgradesTo ?: continue
            unit.upgradesTo = replacements[faction to mirrorUpgrade]?.name ?: mirrorUpgrade
        }

        // A faction unit becomes obsolete once the tech of its upgrade is researched, as in Civ V.
        // Common units stay available: their upgrade differs per faction.
        for (unit in units.values) {
            if (unit.faction == null || unit.obsoleteTech != null) continue
            val upgrade = units[unit.upgradesTo ?: continue] ?: continue
            if (upgrade.faction != unit.faction) continue
            unit.obsoleteTech = upgrade.requiredTech
        }
    }
}
