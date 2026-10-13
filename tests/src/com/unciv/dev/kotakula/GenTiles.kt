package com.unciv.dev.kotakula

/** Russian stat words of the spec -> Unciv stat field names */
private val statWords = linkedMapOf(
    "производств" to "production", "наук" to "science", "ман" to "science", "золот" to "gold",
    "ед" to "food", "культур" to "culture", "вер" to "faith", "счасть" to "happiness",
)

/** "+1 производство, +1 наука" -> {production: 1, science: 1} */
fun parseStats(cell: String): JObj {
    val result = JObj()
    for (part in SpecDoc.list(cell)) {
        val match = Regex("""([+-]?\d+)\s+(\S+)""").find(part) ?: continue
        val amount = match.groupValues[1].toInt()
        val word = match.groupValues[2].lowercase()
        val stat = statWords.entries.firstOrNull { word.startsWith(it.key) }?.value
            ?: throw IllegalArgumentException("Unknown stat word in '$cell'")
        result[stat] = ((result[stat] as Int?) ?: 0) + amount
    }
    return result
}

/** Section 3: TileResources.json */
fun GenContext.generateResources() {
    translations.section("Resources (section 3)")
    resourceIds["Лошади"] = "Horses"
    resourceIds["Железо"] = "Iron"
    improvementIds["Шахта"] = "Mine"
    improvementIds["Плантация"] = "Plantation"
    for (row in spec.table("### 3.5.")) improvementIds[row["Название"]] = SpecDoc.id(row["ID"])

    val vanilla = vanilla("TileResources.json")
    val vanillaByName = vanilla.associateBy { it["name"] as String }
    val technocrat = spec.table("### 3.3.")
    val magic = spec.table("### 3.4.")
    for (row in technocrat + magic) resourceIds[row["Название"]] = SpecDoc.id(row["ID"])

    fun prototype(row: SpecDoc.Row, generated: Map<String, JObj>): JObj {
        val cell = row["Прототип размещения"]
        val protoName = SpecDoc.backticked(cell).firstOrNull()
            ?: cell.removePrefix("ванильный ").substringBefore(',').trim()
        val base = (vanillaByName[protoName] ?: generated[protoName]
            ?: throw IllegalArgumentException("No prototype '$protoName'"))
        @Suppress("UNCHECKED_CAST")
        val copy = JObj(base.mapValues { (_, v) -> if (v is List<*>) ArrayList(v) else v })
        if (cell.contains("только Холмы, Пустыня, Тундра")) {
            copy["terrainsCanBeFoundOn"] = listOf("Hill", "Desert", "Tundra")
            @Suppress("UNCHECKED_CAST")
            copy["uniques"] = (copy["uniques"] as List<String>).filter { u ->
                listOf("Hill", "Desert", "Tundra").any { u.contains(it) } && listOf("Snow", "Jungle").none { u.contains(it) }
            }
        }
        return copy
    }

    val generated = LinkedHashMap<String, JObj>()
    for ((rows, faction) in listOf(technocrat to "Technocrat", magic to "Humanist")) {
        for (row in rows) {
            val id = SpecDoc.id(row["ID"])
            val proto = prototype(row, generated)
            val improvements = row["Улучшение"].split('/').map { improvementIds[it.trim()]
                ?: throw IllegalArgumentException("Unknown improvement '${it.trim()}'") }
            val resource = jobj(
                "name" to id,
                "faction" to faction,
                "resourceType" to (if (row["Тип"] == "Стратегический") "Strategic" else "Luxury"),
                "revealedBy" to techId(row["Открывает"]),
                "terrainsCanBeFoundOn" to proto["terrainsCanBeFoundOn"],
            )
            resource.putAll(parseStats(row["Бонус клетки"]))
            if (improvements.size == 1) resource["improvement"] = improvements[0]
            else resource["improvedBy"] = improvements
            resource["improvementStats"] = proto["improvementStats"]
            resource["uniques"] = proto["uniques"]
            resource["majorDepositAmount"] = proto["majorDepositAmount"]
            resource["minorDepositAmount"] = proto["minorDepositAmount"]
            val descriptionKey = "Description: $id"
            resource["civilopediaText"] = listOf(jobj("text" to descriptionKey))
            translations.add(id, row["Название"])
            translations.add(descriptionKey, row["Описание в Цивилопедии"])
            generated[id] = resource
        }
    }

    val result = ArrayList<JObj>()
    for (resource in vanilla) {
        val name = resource["name"] as String
        result += generated.remove(name) ?: resource // Coal, Oil, Aluminum, Uranium are replaced in place
    }
    result += generated.values
    writeJson("TileResources.json", result)
}

/** Section 3.5: TileImprovements.json */
fun GenContext.generateImprovements() {
    translations.section("Improvements (section 3.5)")
    val vanilla = vanilla("TileImprovements.json").filter { it["uniqueTo"] == null }
    val vanillaByName = vanilla.associateBy { it["name"] as String }.toMutableMap()

    // Common improvements whose vanilla tech is gone get the common tech of the spec (5.3) or a comparable one
    val techOverrides = mapOf(
        "Plantation" to "Pottery", "Camp" to "Animal Husbandry", "Lumber mill" to "Bronze Working",
        "Trading post" to "Animal Husbandry", "Fort" to "Iron Working",
    )
    for ((name, tech) in techOverrides) vanillaByName[name]!!["techRequired"] = tech

    val rows = spec.table("### 3.5.")
    val generated = LinkedHashMap<String, JObj>()
    for (row in rows) {
        val id = SpecDoc.id(row["ID"])
        val faction = if (row["Фракция"] == "Технари") "Technocrat" else "Humanist"
        translations.add(id, row["Название"])
        val tech = techId(row["Технология"])
        val where = row["Где"]
        val time = row["Время"]
        val effect = row["Эффект"]

        // The vanilla improvement this one copies ("как ...")
        val baseName = when (id) {
            "Railroad", "Ley Line" -> "Railroad"
            "Oil well", "Moon Well" -> "Oil well"
            "Offshore Platform", "Moon Well (Water)" -> "Offshore Platform"
            "Alchemist Garden" -> "Plantation"
            else -> "Mine"
        }
        val base = vanillaByName[baseName]!!
        val improvement = JObj()
        improvement["name"] = id
        improvement["faction"] = faction
        if (id == "Ley Line") improvement["replaces"] = "Railroad"
        // Resource improvements need no terrains: Unciv allows them wherever their resource lies
        if (id == "Railroad" || id == "Ley Line") improvement["terrainsCanBeBuiltOn"] = base["terrainsCanBeBuiltOn"]
        improvement.putAll(if (id == "Railroad" || id == "Ley Line") JObj() else parseStats(effect))
        improvement["turnsToBuild"] = if (time.endsWith("ходов")) time.substringBefore(' ').toInt()
            else when {
                time.contains("Железная дорога") -> vanillaByName["Railroad"]!!["turnsToBuild"]
                time.contains("Шахта") -> vanillaByName["Mine"]!!["turnsToBuild"]
                time.contains("Плантация") -> vanillaByName["Plantation"]!!["turnsToBuild"]
                time.contains("Нефтяная скважина") -> vanillaByName["Oil well"]!!["turnsToBuild"]
                time.contains("Морская платформа") -> vanillaByName["Offshore Platform"]!!["turnsToBuild"]
                else -> base["turnsToBuild"]
            }
        improvement["techRequired"] = tech
        improvement["uniques"] = when (id) {
            "Railroad", "Ley Line" -> base["uniques"]
            "Oil well", "Moon Well" -> vanillaByName["Oil well"]!!["uniques"]
            "Offshore Platform", "Moon Well (Water)" -> vanillaByName["Offshore Platform"]!!["uniques"]
            else -> listOf("Can only be built to improve a resource", "Pillaging this improvement yields approximately [+20 Gold]")
        }
        improvement["shortcutKey"] = base["shortcutKey"]
        improvement["civilopediaText"] = if (id == "Ley Line") listOf(jobj("text" to "Description: Ley Line"))
            else base["civilopediaText"]
        if (id == "Ley Line") translations.add("Description: Ley Line", "Аналог Железной дороги: движение 1/10, связь городов")
        generated[id] = improvement
    }

    val result = ArrayList<JObj>()
    for (improvement in vanilla) {
        val name = improvement["name"] as String
        result += generated.remove(name) ?: adaptVanillaUniques(improvement)
    }
    result += generated.values
    writeJson("TileImprovements.json", result)
}

