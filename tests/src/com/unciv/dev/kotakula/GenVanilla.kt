package com.unciv.dev.kotakula

/**
 * Adapting vanilla Gods & Kings content that the spec keeps ("ванильный"): references to vanilla techs,
 * buildings and units that do not exist in the Kotakula tree are rewritten to their Kotakula equivalent.
 */

/** Vanilla buildings renamed or replaced in the mod (section 7) */
val vanillaBuildingRenames = mapOf(
    "Temple" to "Business Center", // section 9.1: the temple role is played by Business Center / Temple of Light
    "Amphitheater" to "Standup Theater",
    "Colosseum" to "Arena",
    "Garden" to "Aqueduct",
    "Castle" to "Bastion",
    "Hermitage" to "Standup Theater",
    "The Pyramids" to "Pyramids",
    "The Great Library" to "Great Library",
    "The Great Lighthouse" to "Great Lighthouse",
    "University" to "University",
)

private var vanillaTechEraCache: Map<String, String>? = null

/** Vanilla tech name -> its era, for rewriting tech conditionals of vanilla content */
fun GenContext.vanillaTechEra(): Map<String, String> {
    vanillaTechEraCache?.let { return it }
    val map = HashMap<String, String>()
    for (column in vanilla("Techs.json")) {
        @Suppress("UNCHECKED_CAST")
        for (tech in column["techs"] as List<JObj>) map[tech["name"] as String] = column["era"] as String
    }
    vanillaTechEraCache = map
    return map
}

/** Rewrites one vanilla unique; null means "drop it" */
fun GenContext.adaptVanillaUnique(unique: String): String? {
    val modTechs = techIds.values.toSet()
    // Tech conditionals -> era conditionals of the same era (keeps the timing equal for both factions)
    fun replaceTechConditional(text: String, pattern: String, eraConditional: (String) -> String): String? {
        var result = text
        for (match in Regex(pattern).findAll(text)) {
            val tech = match.groupValues[1]
            if (tech in modTechs) continue
            val era = vanillaTechEra()[tech] ?: return null
            result = result.replace(match.value, eraConditional(era))
        }
        return result
    }
    var u: String = unique
    u = replaceTechConditional(u, """<after discovering \[([^\]]+)]>""") { "<starting from the [$it]>" } ?: return null
    u = replaceTechConditional(u, """<before discovering \[([^\]]+)]>""") { "<before the [$it]>" } ?: return null
    u = replaceTechConditional(u, """<upon discovering \[([^\]]+)] technology>""") { "<upon entering the [$it]>" } ?: return null
    // Buildings
    for ((old, new) in vanillaBuildingRenames)
        u = u.replace("[$old]", "[$new]")
    // Things that do not exist at all in the mod
    if (u.contains("[Manhattan Project]") || u.contains("[Apollo Program]")) return null
    return u
}

@Suppress("UNCHECKED_CAST")
fun GenContext.adaptVanillaUniques(obj: JObj): JObj {
    val uniques = obj["uniques"] as List<String>? ?: return obj
    obj["uniques"] = uniques.mapNotNull { adaptVanillaUnique(it) }.ifEmpty { null }
    return obj
}
