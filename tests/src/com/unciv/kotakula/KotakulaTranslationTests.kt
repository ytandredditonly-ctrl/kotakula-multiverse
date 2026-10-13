package com.unciv.kotakula

import com.unciv.dev.kotakula.FactionWording
import com.unciv.dev.kotakula.readTranslationFile
import org.junit.Assert
import org.junit.Test
import java.io.File

/**
 * Spec section 14.1: the same check as tools/check_translations.py — Humanists must not see the science words,
 * Technocrats the faith words, in the text each faction actually gets (`Key#Faction`, else `Key` of the mod, else vanilla).
 */
class KotakulaTranslationTests {
    private val placeholder = Regex("""\[[^\]]*]|\{[^}]*}""")

    @Test
    fun factionTextsHaveNoForbiddenWords() {
        val mod = readTranslationFile(File(KotakulaTestSupport.MOD_FOLDER, "jsons/translations/Russian.properties"))
        val vanilla = readTranslationFile(File("jsons/translations/Russian.properties"))
        val keys = (mod.keys + vanilla.keys).filterNot { it.contains('#') }.toSortedSet()
        val report = ArrayList<String>()
        for ((faction, pattern) in listOf(
            "Humanist" to FactionWording.forbiddenForHumanists,
            "Technocrat" to FactionWording.forbiddenForTechnocrats,
        )) for (key in keys) {
            val text = mod["$key#$faction"] ?: mod[key] ?: vanilla[key] ?: continue
            val found = pattern.find(placeholder.replace(text, "")) ?: continue
            report += "$faction: «${found.value}» in [$key] = $text"
        }
        report.forEach(::println)
        Assert.assertTrue("check_translations report has ${report.size} lines", report.isEmpty())
    }
}
