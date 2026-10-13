package com.unciv.dev.kotakula

/** Section 5: Techs.json - common Stick-era techs, Technocrat techs and Humanist mysteries on one shared grid */
fun GenContext.generateTechs() {
    translations.section("Technologies (section 5)")
    val columns = spec.table("### 5.1.")
    val common = spec.table("### 5.3.")
    val technocrat = spec.table("### 5.4.")
    val humanist = spec.table("### 5.5.")

    // Pass 1: names, so prerequisites can be resolved in any order
    for (row in common + technocrat + humanist) {
        val id = SpecDoc.id(row["ID"])
        techIds[row["Название"]] = id
    }
    for (t in technocrat) {
        val h = humanist.first { it["Кол."] == t["Кол."] && it["Ряд"] == t["Ряд"] }
        techMirror[SpecDoc.id(t["ID"])] = SpecDoc.id(h["ID"])
    }

    // Direct prerequisites by id, to drop the ones already implied by others (same tree, no Unciv warning)
    val directPrereqs = (common + technocrat + humanist).associate { row ->
        SpecDoc.id(row["ID"]) to SpecDoc.list(row["Требует"]).map { techId(it) }
    }
    fun allPrereqs(tech: String): Set<String> =
        directPrereqs[tech].orEmpty().flatMap { allPrereqs(it) + it }.toSet()
    fun minimalPrereqs(tech: String): List<String> {
        val direct = directPrereqs[tech].orEmpty()
        return direct.filter { candidate -> direct.none { other -> other != candidate && candidate in allPrereqs(other) } }
    }

    fun techJson(row: SpecDoc.Row, faction: String?): JObj {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val quoteKey = "Quote: $id"
        translations.add(quoteKey, row["Цитата"])
        val uniques = ArrayList<String>()
        if (id == "Agriculture") uniques += "Starting tech"
        val opens = row["Открывает"]
        for (code in SpecDoc.backticked(opens)) uniques += fixUnique(code)
        if (opens.contains("то же для Каменоломни"))
            uniques += "[+1 Production] from [Quarry] tiles [in all cities]"
        val technicalSource = if (faction == "Humanist") techMirror.entries.first { it.value == id }.key else id
        uniques += technicalUniques[technicalSource].orEmpty()
        // Section 5.4/5.5: the repeatable last tech gives +10 score per research - Unciv counts repeatable techs that way
        return jobj(
            "name" to id,
            "faction" to faction,
            "row" to row["Ряд"].toInt(),
            "prerequisites" to minimalPrereqs(id).ifEmpty { null },
            "uniques" to uniques.ifEmpty { null },
            "quote" to quoteKey,
        )
    }

    val result = ArrayList<JObj>()
    for (column in columns) {
        val number = column["Колонка"].toInt()
        val techs = ArrayList<JObj>()
        for (row in common) if (row["Кол."].toInt() == number) techs += techJson(row, null)
        for (row in technocrat) if (row["Кол."].toInt() == number) techs += techJson(row, "Technocrat")
        for (row in humanist) if (row["Кол."].toInt() == number) techs += techJson(row, "Humanist")
        result += jobj(
            "columnNumber" to number,
            "era" to column["Эпоха (ID)"],
            "techCost" to column["techCost"].toInt(),
            "buildingCost" to column["buildingCost"].toInt(),
            "wonderCost" to column["wonderCost"].toInt(),
            "techs" to techs,
        )
    }
    writeJson("Techs.json", result)
}

/**
 * Vanilla techs carry engine features the spec tables do not list (open question 4): they go to the Technocrat tech
 * of the same meaning one column later (the mod has one more Ancient column) and to its Humanist mirror.
 */
private val technicalUniques = mapOf(
    "Writing" to listOf("Enables establishment of embassies <Civilopedia link [Tutorials/Embassies]>"),
    "Shipbuilding" to listOf("Enables embarkation for land units"),
    "Engineering" to listOf("Roads connect tiles across rivers"),
    "Banking" to listOf("Enables Open Borders agreements <Civilopedia link [Tutorials/Open Borders]>",
        "Enables conversion of city production to [Gold]"),
    "Clockwork" to listOf("Improves movement speed on roads"),
    "Printing Press" to listOf("Enables conversion of city production to [Science]",
        "Enables Research agreements <Civilopedia link [Tutorials/Research Agreements]>"),
    "Corporate Law" to listOf("Enables Defensive Pacts <Civilopedia link [Tutorials/Defensive Pacts]>"),
    "Astronomy" to listOf("[+1] Movement <for [Embarked] units>", "Enables [Embarked] units to enter ocean tiles"),
    "Steam Engine" to listOf("[+1] Movement <for [Embarked] units>"),
)
