package com.unciv.dev.kotakula

/** Section 6.2: line of a unit type -> index of the row in the traits table */
private val lineOfUnitType = mapOf(
    "Melee" to 0, "Ranged" to 1, "Siege" to 2, "Mounted" to 3, "Armor" to 4,
    "WaterMelee" to 5, "WaterAircraftCarrier" to 5,
    "WaterRanged" to 6, "WaterSubmarine" to 6,
    "Fighter" to 7, "Bomber" to 7,
)

/** Vanilla units kept as they are (section 6.3): great people and religious units */
private val keptVanillaUnits = listOf(
    "Great Artist", "Great Scientist", "Great Merchant", "Great Engineer", "Great Prophet",
    "Great General", "Great Admiral", "Missionary", "Inquisitor",
)

/** Section 6: Units.json */
fun GenContext.generateUnits() {
    translations.section("Units (section 6)")
    val vanilla = vanilla("Units.json").associateBy { it["name"] as String }
    val traits = spec.table("### 6.2.")
    val common = spec.table("### 6.3.").filter { it["ID"].startsWith("`") }
    val technocrat = spec.table("### 6.4.")
    val humanist = spec.table("### 6.5.")
    for (row in common + technocrat + humanist) unitIds[row["Название"]] = SpecDoc.id(row["ID"])

    fun traitUniques(unitType: String, faction: String): List<String> {
        val line = lineOfUnitType[unitType] ?: return emptyList()
        val cell = traits[line][if (faction == "Technocrat") "Технари" else "Гумы"]
        return SpecDoc.backticked(cell).map { fixUnique(it) }
    }

    /** Technical uniques of the vanilla prototype (section 6.2): abilities, not combat bonuses */
    @Suppress("UNCHECKED_CAST")
    fun prototypeUniques(prototype: String, faction: String, unitType: String, allOfThem: Boolean): List<String> {
        val uniques = (vanilla[prototype]?.get("uniques") as List<String>?) ?: return emptyList()
        return uniques.mapNotNull { adaptVanillaUnique(it) }.filter { u ->
            when {
                allOfThem -> true
                u.contains("% Strength") -> false // combat bonuses come from the line traits
                u.startsWith("Can carry [") -> false // the spec gives the carrier capacity
                u == "Must set up to ranged attack" -> faction == "Technocrat" && unitType == "Siege"
                else -> true
            }
        }
    }

    fun description(id: String, cell: String): Pair<List<String>, String> {
        val uniques = SpecDoc.backticked(cell).map { fixUnique(it) }
        val text = SpecDoc.withoutBackticks(cell)
        val key = "Description: $id"
        translations.add(key, text)
        return uniques to key
    }

    fun stats(unit: JObj, row: SpecDoc.Row, prototype: JObj?) {
        unit["unitType"] = row["Тип"]
        unit["movement"] = if (SpecDoc.isEmpty(row["Ход"])) prototype?.get("movement") ?: 1 else row["Ход"].toInt()
        unit["strength"] = row["Сила"].toInt()
        // Unciv needs a strength for aircraft (it defends against interception with it): use the ranged strength (open question 1)
        if (unit["strength"] == 0 && row["Тип"] in setOf("Fighter", "Bomber") && !SpecDoc.isEmpty(row["Дальн. сила"]))
            unit["strength"] = row["Дальн. сила"].toInt()
        if (!SpecDoc.isEmpty(row["Дальн. сила"])) unit["rangedStrength"] = row["Дальн. сила"].toInt()
        if (!SpecDoc.isEmpty(row["Дальн."])) unit["range"] = row["Дальн."].toInt()
        if (prototype?.get("interceptRange") != null) unit["interceptRange"] = prototype["interceptRange"]
        unit["cost"] = row["Цена"].toInt()
        if (!SpecDoc.isEmpty(row["Технология"])) unit["requiredTech"] = techId(row["Технология"])
    }

    fun resourcesOf(cell: String, mirror: Boolean): Pair<String?, List<String>> {
        if (SpecDoc.isEmpty(cell)) return null to emptyList()
        val list = resources(cell).map { (amount, id) -> amount to (if (mirror) resourceMirror[id]!! else id) }
        val first = list.first()
        val extra = list.drop(1).map { (amount, id) -> "Consumes [$amount] [$id]" }
        return (if (first.first == 1) first.second else null) to
            (if (first.first == 1) extra else listOf("Consumes [${first.first}] [${first.second}]") + extra)
    }

    val result = ArrayList<JObj>()

    // Common units (6.3), based on their vanilla namesakes
    for (row in common) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val base = vanilla[if (id == "Work Boat") "Work Boats" else id]!!
        val unit = JObj()
        unit["name"] = id
        stats(unit, row, base)
        val (resource, resourceUniques) = resourcesOf(row["Ресурс"], false)
        unit["requiredResource"] = resource
        if (!SpecDoc.isEmpty(row["Улучшается в"])) unit["upgradesTo"] = unitId(row["Улучшается в"])
        val (specUniques, descriptionKey) = description(id, row["Описание"])
        @Suppress("UNCHECKED_CAST")
        val vanillaUniques = ((base["uniques"] as List<String>?) ?: emptyList()).mapNotNull { adaptVanillaUnique(it) }
        unit["uniques"] = (vanillaUniques + specUniques + resourceUniques).distinct().ifEmpty { null }
        unit["promotions"] = base["promotions"]
        unit["hurryCostModifier"] = base["hurryCostModifier"]
        unit["attackSound"] = base["attackSound"]
        unit["civilopediaText"] = listOf(jobj("text" to descriptionKey))
        result += unit
    }

    // Technocrat units (6.4)
    val technocratRows = technocrat.associateBy { SpecDoc.id(it["ID"]) }
    fun factionUnit(id: String, faction: String, row: SpecDoc.Row, mirrorOf: String?, descriptionCell: String, tech: String): JObj {
        val prototypeName = row["Прототип"]
        val prototype = vanilla[prototypeName] ?: vanilla["Anti-Tank Gun"]!! // "Bazooka" is not in Gods & Kings (open question 2)
        val unit = JObj()
        unit["name"] = id
        unit["faction"] = faction
        if (mirrorOf != null) unit["replaces"] = mirrorOf
        stats(unit, row, prototype)
        unit["requiredTech"] = tech
        val (resource, resourceUniques) = resourcesOf(row["Ресурс"], mirror = faction == "Humanist")
        unit["requiredResource"] = resource
        if (faction == "Technocrat" && !SpecDoc.isEmpty(row["Улучшается в"])) unit["upgradesTo"] = unitId(row["Улучшается в"])
        val special = row["Особое"]
        val allPrototypeUniques = special.contains("все unique ванильной")
        val (_, descriptionKey) = description(id, descriptionCell)
        val uniques = traitUniques(row["Тип"], faction) +
            prototypeUniques(prototypeName, faction, row["Тип"], allPrototypeUniques) +
            SpecDoc.backticked(special).filter { it != prototypeName }.map { fixUnique(it) } +
            resourceUniques
        unit["uniques"] = uniques.distinct().ifEmpty { null }
        unit["promotions"] = prototype["promotions"]
        unit["hurryCostModifier"] = prototype["hurryCostModifier"]
        unit["attackSound"] = prototype["attackSound"]
        unit["civilopediaText"] = listOf(jobj("text" to descriptionKey))
        return unit
    }

    for (row in technocrat) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        result += factionUnit(id, "Technocrat", row, null, row["Описание"], techId(row["Технология"]))
    }
    // Humanist units (6.5): stats of the mirror, own tech, mirrored resources, own line traits
    for (row in humanist) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val mirror = row["Зеркало"]
        val mirrorRow = technocratRows[mirror] ?: throw IllegalArgumentException("No mirror '$mirror'")
        result += factionUnit(id, "Humanist", mirrorRow, mirror, row["Описание"], techId(row["Технология"]))
    }

    // Victory parts (section 11.2)
    result += generateVictoryParts()

    // Great people and religious units (section 6.3): vanilla
    for (name in keptVanillaUnits) {
        val unit = JObj(vanilla[name]!!)
        adaptVanillaUniques(unit)
        result += unit
    }
    writeJson("Units.json", result)
}

/** Section 11.2: parts of the Interuniverse Flight / Gates of Paradise, modelled on the vanilla spaceship parts */
private fun GenContext.generateVictoryParts(): List<JObj> {
    translations.section("Victory parts (section 11.2)")
    val rows = spec.table("### 11.2.")
    val descriptions = spec.sectionText("### 11.2.").joinToString(" ").substringAfter("Описания:")
    fun descriptionOf(russianName: String): String =
        Regex("$russianName — «([^»]+)»").find(descriptions)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("No description for $russianName")

    val result = ArrayList<JObj>()
    for (row in rows) {
        for (faction in listOf("Technocrat", "Humanist")) {
            val cell = if (faction == "Technocrat") row["Технари"] else row["Гумы (зеркало)"]
            val id = SpecDoc.id(cell)
            val russian = SpecDoc.withoutBackticks(cell)
            translations.add(id, russian)
            val descriptionKey = "Description: $id"
            translations.add(descriptionKey, descriptionOf(russian))
            val resourceCell = if (faction == "Technocrat") row.raw[3] else row.raw[6] // two columns are called "Ресурс"
            val techCell = if (faction == "Technocrat") row["Технология"] else row["Таинство"]
            val project = if (faction == "Technocrat") "Interuniverse Shipyard" else "Celestial Chancery"
            val victory = if (faction == "Technocrat") "Interuniverse Flight" else "Gates of Paradise"
            val count = row["Кол-во"].toInt()
            result += jobj(
                "name" to id,
                "faction" to faction,
                "unitType" to "Civilian",
                "movement" to 2,
                "cost" to row["Цена"].toInt(),
                "requiredTech" to techId(techCell),
                "requiredResource" to resources(resourceCell).first().second,
                "uniques" to listOf("Spaceship part", "Cannot be purchased", "Only available <if [$project] is constructed>",
                    "Uncapturable", "Can be added to [The Spaceship] in the Capital", "Limited to [$count] per Civilization",
                    "Only available <when [$victory] Victory is enabled>"),
                "civilopediaText" to listOf(jobj("text" to descriptionKey)),
            )
        }
    }
    return result
}
