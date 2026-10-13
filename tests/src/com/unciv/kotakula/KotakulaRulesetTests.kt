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

    /** Section 13: every SVG icon of art/kotakula-icons is packed into the mod atlas under the name the engine looks up */
    @Test
    fun svgIconsArePackedIntoTheAtlas() {
        val sources = java.io.File(com.unciv.dev.kotakula.KotakulaSvgIcons.SOURCE_DIR)
        val svgNames = sources.walkTopDown().filter { it.extension == "svg" }
            .map { it.relativeTo(sources).invariantSeparatorsPath.removeSuffix(".svg") }.toList()
        Assert.assertTrue("No SVG icons found", svgNames.size > 300)
        val atlas = java.io.File(KotakulaTestSupport.MOD_FOLDER, "game.atlas").readLines(Charsets.UTF_8).toSet()
        val missing = svgNames.filterNot { it in atlas }
        Assert.assertTrue("Not in game.atlas: $missing", missing.isEmpty())
        // ...and every unique object of the mod has its own icon or a vanilla one kept on purpose
        val withoutIcon = com.unciv.dev.kotakula.KotakulaSvgIcons.targets(java.io.File(KotakulaTestSupport.MOD_FOLDER))
            .map { "${it.folder}/${it.name}" }
            .filterNot { it in svgNames }
            .filterNot { it in java.io.File(sources, "keep-vanilla.txt").readLines() }
        Assert.assertTrue("Unique objects without an icon: $withoutIcon", withoutIcon.isEmpty())
    }

    /** Bug report: units gained XP but could never be promoted (G&K promotions named G&K unit types only) */
    @Test
    fun everyMilitaryUnitCanBePromoted() {
        val ruleset = RulesetCache.getComplexRuleset(linkedSetOf(), KotakulaTestSupport.MOD.also { KotakulaTestSupport.ensureLoaded() })
        val withoutPromotions = ruleset.units.values
            .filter { it.isMilitary && it.unitType != "Missile" && !it.hasUnique(com.unciv.models.ruleset.unique.UniqueType.NuclearWeapon) }
            .filter { unit -> ruleset.unitPromotions.values.none { unit.unitType in it.unitTypes && it.prerequisites.isEmpty() } }
            .map { "${it.name} (${it.unitType})" }
        Assert.assertTrue("No promotion for: $withoutPromotions", withoutPromotions.isEmpty())
        // The submarines keep what makes them submarines
        Assert.assertTrue(ruleset.unitTypes["WaterSubmarine"]!!.uniques.any { it.startsWith("Invisible to non-adjacent units") })
    }

    @Test
    fun rulesetHasFactions() {
        val ruleset = RulesetCache.getComplexRuleset(linkedSetOf(), KotakulaTestSupport.MOD.also { KotakulaTestSupport.ensureLoaded() })
        Assert.assertTrue(ruleset.hasFactions)
        Assert.assertEquals(50, ruleset.technologies.values.count { it.faction == null || it.faction!!.name == "Technocrat" })
        Assert.assertEquals(50, ruleset.technologies.values.count { it.faction == null || it.faction!!.name == "Humanist" })
    }
}
