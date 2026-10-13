package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.translations.tr
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine

/**
 * Kotakula Multiverse (KC-12): a faith action from `FactionActions.json` (section 9.4).
 *
 * Technocrats call them "Corporate initiatives", Humanists "Prayers and rituals".
 * - [duration] > 0: a long action - its uniques are global effects for that many turns and it takes a slot.
 * - [duration] == 0: an instant action - its uniques are triggers that fire once, no slot needed.
 * After the action ends it is on [cooldown] for that many turns.
 */
class FactionAction : RulesetObject() {
    /** Faith cost before era, speed and discounts */
    var baseCost = 0
    /** Turns the effect lasts, 0 for instant actions */
    var duration = 0
    /** Turns before it can be used again, counted from its end */
    var cooldown = 10
    var quote = ""

    val isInstant get() = duration <= 0

    override fun getUniqueTarget() = UniqueTarget.Global
    override fun makeLink() = "FactionAction/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lines = ArrayList<FormattedLine>()
        if (quote.isNotEmpty()) {
            lines += FormattedLine(quote, color = "#C8C8C8", centered = true)
            lines += FormattedLine()
        }
        lines += FormattedLine("Base cost: [$baseCost]")
        lines += if (isInstant) FormattedLine("Instant action")
            else FormattedLine("Duration: [$duration] turns")
        lines += FormattedLine("Cooldown: [$cooldown] turns")
        uniquesToCivilopediaTextLines(lines)
        return lines
    }
}
