package com.unciv.dev.kotakula

import java.io.File

/**
 * Generates the Kotakula Multiverse base ruleset from the spec (docs/kotakula/spec.md) and the vanilla
 * Gods & Kings ruleset. Run from android/assets:
 *
 *     ./gradlew :tests:kotakulaMod
 *
 * Output: android/assets/KotakulaMod/Kotakula Multiverse/jsons (+ translations).
 * Hand-made parts of the mod (Images, README.md, game.atlas) are not touched.
 */
object KotakulaModGenerator {
    const val MOD_PATH = "KotakulaMod/Kotakula Multiverse"
    /** The addendum "gods and startups" (pantheons of the factions) */
    const val GODS_PATH = "../../docs/kotakula/gods-and-startups.md"

    @JvmStatic
    fun main(args: Array<String>) {
        val spec = File(args.getOrElse(0) { "../../docs/kotakula/spec.md" })
        val out = File(args.getOrElse(1) { MOD_PATH })
        generate(spec, File("jsons/Civ V - Gods & Kings"), File("jsons/translations/Russian.properties"), out)
        println("Kotakula Multiverse generated in ${out.absolutePath}")
    }

    fun generate(specFile: File, vanillaDir: File, vanillaRussianFile: File, outDir: File) {
        val context = GenContext(SpecDoc(specFile), vanillaDir, outDir)
        val vanillaRussian = readTranslationFile(vanillaRussianFile)
        File(outDir, "jsons").deleteRecursively()
        with(context) {
            generateTechs()
            generateResources()
            generateImprovements()
            generateUnits()
            generateBuildings()
            generatePolicies()
            generateReligion(vanillaRussian)
            generateNations()
            generateEras()
            generateVictories()
            generateEvents()
            generateMisc()
            renameUnitTypeFilters(File(outDir, "jsons"))
            generateIconPlaceholders(File("../.."))
            addEngineTranslations()
            addFactionVariants(vanillaRussian)
            translations.write(File(outDir, "jsons/translations/Russian.properties"))
        }
    }
}
