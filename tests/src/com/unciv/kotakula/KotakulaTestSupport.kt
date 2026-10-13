package com.unciv.kotakula

import com.badlogic.gdx.files.FileHandle
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache

/** Loads the generated Kotakula Multiverse mod (android/assets/KotakulaMod) into [RulesetCache] for tests */
object KotakulaTestSupport {
    const val MOD = "Kotakula Multiverse"
    const val MOD_FOLDER = "KotakulaMod/$MOD"

    fun ensureLoaded(): Ruleset {
        if (RulesetCache.isEmpty()) RulesetCache.loadRulesets(noMods = true)
        RulesetCache[MOD]?.let { return it }
        val folder = FileHandle(MOD_FOLDER)
        check(folder.exists()) { "Generate the mod first: ./gradlew :tests:kotakulaMod" }
        val ruleset = Ruleset()
        ruleset.name = MOD
        ruleset.load(folder.child("jsons"))
        ruleset.folderLocation = folder
        RulesetCache[MOD] = ruleset
        return ruleset
    }
}
