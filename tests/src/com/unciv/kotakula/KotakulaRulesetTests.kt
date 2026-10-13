package com.unciv.kotakula

import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/** Spec section 14.2, test T13: the mod loads without errors or warnings of the Unciv ruleset check */
@RunWith(GdxTestRunner::class)
class KotakulaRulesetTests {

    @Test
    fun t13_rulesetLoadsWithoutErrorsOrWarnings() {
        KotakulaTestSupport.ensureLoaded()
        val (ruleset, errors) = RulesetCache.checkCombinedModLinks(linkedSetOf(), KotakulaTestSupport.MOD)
        Assert.assertNotNull(ruleset)
        // "OK" entries are informational notes of the checker, not errors or warnings
        // Without a GL context the game atlases are not loaded: an extraImage counts as found when its png exists
        // in the image sources of the game (android/Images.*) or of the mod (Images)
        val imageSources = (java.io.File("..").listFiles { f -> f.name.startsWith("Images.") }.orEmpty().toList()
            + java.io.File(KotakulaTestSupport.MOD_FOLDER, "Images"))
        val combined = ruleset!!
        fun imageExistsAsFile(text: String): Boolean {
            val match = Regex("""^\((\w+)\) (.*?)'?s? ?civilopediaText line (\d+):""").find(text) ?: return false
            val (className, name, line) = match.destructured
            val sources = combined.allICivilopediaText().filter { it::class.java.simpleName == className }
                .filter { (it as? com.unciv.models.stats.INamed)?.name.orEmpty() == name.removeSuffix("'s").trim() }
            val images = sources.mapNotNull { it.civilopediaText.getOrNull(line.toInt() - 1)?.extraImage }.toList()
            return images.isNotEmpty() && images.all { image -> imageSources.any { java.io.File(it, "$image.png").exists() } }
        }
        val problems = errors.filter { it.errorSeverityToReport > RulesetErrorSeverity.OK }
            .filterNot { it.text.contains("extraImage not found") && imageExistsAsFile(it.text) }
        for (error in problems) println("T13: ${error.errorSeverityToReport} ${error.text}")
        Assert.assertTrue("Ruleset errors/warnings: ${problems.size}", problems.isEmpty())
    }

    @Test
    fun rulesetHasFactions() {
        val ruleset = RulesetCache.getComplexRuleset(linkedSetOf(), KotakulaTestSupport.MOD.also { KotakulaTestSupport.ensureLoaded() })
        Assert.assertTrue(ruleset.hasFactions)
        Assert.assertEquals(50, ruleset.technologies.values.count { it.faction == null || it.faction!!.name == "Technocrat" })
        Assert.assertEquals(50, ruleset.technologies.values.count { it.faction == null || it.faction!!.name == "Humanist" })
    }
}
