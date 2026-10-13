package com.unciv.dev.kotakula

/** Branch archetypes of section 8.1, in table order */
enum class BranchArchetype { Production, Growth, Military, FaithGold, Science, Final }

/** AI priorities of a branch per victory type (section 12.2); the final branches get the maximum */
private fun victoryPriorities(archetype: BranchArchetype): JObj = when (archetype) {
    BranchArchetype.Production -> jobj("Domination" to 2, "Cultural" to 3, "Interuniverse Flight" to 3, "Gates of Paradise" to 3, "Faction Victory" to 2, "Time" to 2)
    BranchArchetype.Growth -> jobj("Domination" to 2, "Cultural" to 3, "Interuniverse Flight" to 2, "Gates of Paradise" to 2, "Faction Victory" to 2, "Time" to 3)
    BranchArchetype.Military -> jobj("Domination" to 5, "Cultural" to 0, "Interuniverse Flight" to 1, "Gates of Paradise" to 1, "Faction Victory" to 5, "Time" to 1)
    BranchArchetype.FaithGold -> jobj("Domination" to 1, "Cultural" to 3, "Interuniverse Flight" to 2, "Gates of Paradise" to 2, "Faction Victory" to 1, "Time" to 2)
    BranchArchetype.Science -> jobj("Domination" to 1, "Cultural" to 2, "Interuniverse Flight" to 5, "Gates of Paradise" to 5, "Faction Victory" to 1, "Time" to 2)
    BranchArchetype.Final -> jobj("Domination" to 10, "Cultural" to 10, "Interuniverse Flight" to 15, "Gates of Paradise" to 15, "Faction Victory" to 10, "Time" to 10)
}

/** Branch id -> archetype, filled while generating (used for Personalities.json) */
val branchArchetypes = LinkedHashMap<String, BranchArchetype>()

/** Section 8: Policies.json */
fun GenContext.generatePolicies() {
    translations.section("Policies (section 8)")
    val overview = spec.table("### 8.1.")
    // Section headings 8.2 .. 8.13 in the order: Steam, Eco, Diesel, Digital, Atom, Quantum, Runic ... Forbidden
    val technocratSections = listOf("### 8.2.", "### 8.3.", "### 8.4.", "### 8.5.", "### 8.6.", "### 8.7.")
    val humanistSections = listOf("### 8.8.", "### 8.9.", "### 8.10.", "### 8.11.", "### 8.12.", "### 8.13.")
    // 8.1: incompatible branches
    val incompatible = mapOf("Eco" to "Diesel", "Diesel" to "Eco", "Holy Magic" to "Forbidden Magic", "Forbidden Magic" to "Holy Magic")

    val result = ArrayList<JObj>()
    for ((index, overviewRow) in overview.withIndex()) {
        val archetype = BranchArchetype.entries[index]
        for ((side, faction) in listOf(0 to "Technocrat", 1 to "Humanist")) {
            val section = if (side == 0) technocratSections[index] else humanistSections[index]
            val era = overviewRow.raw[if (side == 0) 2 else 4] + " era"
            result += generateBranch(spec.table(section), faction, era, archetype, incompatible)
        }
    }
    writeJson("Policies.json", result)
}

private fun GenContext.policyUniques(cell: String): List<String> {
    val lifetime = Regex("""призванный живёт (\d+) ходов""").find(cell)?.groupValues?.get(1)
    return SpecDoc.backticked(cell).map { raw ->
        val unique = fixUnique(raw)
        if (lifetime != null && unique.startsWith("KM: Summons best")) "$unique <KM: Expires after [$lifetime] turns>"
        else unique
    }
}

private fun GenContext.generateBranch(
    rows: List<SpecDoc.Row>, faction: String, era: String, archetype: BranchArchetype, incompatible: Map<String, String>
): JObj {
    val opening = rows.first { it["№"] == "Откр." }
    val branchId = SpecDoc.id(opening["ID"])
    branchArchetypes[branchId] = archetype
    translations.add(branchId, opening["Название"])
    val branchDescriptionKey = "Description: $branchId"
    translations.add(branchDescriptionKey, opening["Описание"])
    for (row in rows) policyIds[row["№"] + "|" + branchId] = SpecDoc.id(row["ID"])

    // Section 8.1: positions of the five policies
    val positions = mapOf("1" to (1 to 1), "2" to (1 to 3), "3" to (2 to 1), "4" to (2 to 3), "5" to (3 to 2))
    val idByNumber = rows.associate { it["№"] to SpecDoc.id(it["ID"]) }
    val policies = ArrayList<JObj>()
    for (number in listOf("1", "2", "3", "4", "5")) {
        val row = rows.first { it["№"] == number }
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val descriptionKey = "Description: $id"
        translations.add(descriptionKey, row["Описание"])
        val (rowNumber, column) = positions[number]!!
        val requires = when (number) {
            "3" -> listOf(idByNumber["1"]!!)
            "4" -> listOf(idByNumber["2"]!!)
            "5" -> listOf(idByNumber["3"]!!, idByNumber["4"]!!)
            else -> null
        }
        policies += jobj(
            "name" to id,
            "row" to rowNumber,
            "column" to column,
            "requires" to requires,
            "uniques" to policyUniques(row["Эффект"]),
            "civilopediaText" to listOf(jobj("text" to descriptionKey)),
        )
    }
    // The completion policy: Unciv names it "<branch> Complete" whatever the json says (open question 3)
    val completion = rows.first { it["№"] == "Заверш." }
    val completionId = "$branchId Complete"
    translations.add(completionId, completion["Название"])
    val completionDescriptionKey = "Description: $completionId"
    translations.add(completionDescriptionKey, completion["Описание"])
    policies += jobj(
        "name" to completionId,
        "uniques" to policyUniques(completion["Эффект"]),
        "civilopediaText" to listOf(jobj("text" to completionDescriptionKey)),
    )

    val branchUniques = policyUniques(opening["Эффект"]).toMutableList()
    incompatible[branchId]?.let { branchUniques += "Only available <before adopting [$it]>" }
    return jobj(
        "name" to branchId,
        "faction" to faction,
        "era" to era,
        "priorities" to victoryPriorities(archetype),
        "uniques" to branchUniques,
        "civilopediaText" to listOf(jobj("text" to branchDescriptionKey)),
        "policies" to policies,
    )
}
