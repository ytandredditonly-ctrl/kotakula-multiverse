package com.unciv.dev.kotakula

/** Vanilla ids of the common wonders of section 7.1 */
private val vanillaWonderNames = mapOf(
    "Great Lighthouse" to "The Great Lighthouse",
    "Great Library" to "The Great Library",
    "Pyramids" to "The Pyramids",
)

/** Vanilla buildings that only come from beliefs, kept for the vanilla beliefs (section 9.3) */
private val vanillaBeliefBuildings = listOf("Cathedral", "Mosque", "Pagoda", "Monastery")

/** Section 7.3: wonder effects, technocrat / humanist (written out from the spec table) */
private val wonderEffects: Map<String, List<String>> = mapOf(
    "First Calculator" to listOf("Free [Great Scientist] appears", "[+25]% [Science] [in this city]"),
    "Crystal Sphere" to listOf("Free [Great Scientist] appears", "[+1 Science] from every [Library]"),
    "Great Firewall" to listOf("Enemy [Land] units must spend [1] extra movement points when inside your territory",
        "Obsolete with [Steam Engine]", "[+2 Culture]"),
    "Wall of Thorns" to listOf("Enemy [Land] units must spend [1] extra movement points when inside your territory",
        "Obsolete with [Crystallomancy]", "[+2 Faith]"),
    "Giant Gear" to listOf("[+2 Production] from every [Workshop]"),
    "Eternal Cauldron" to listOf("[+1 Production, +1 Food] from every [Workshop]"),
    "Turing Machine" to listOf("Free Technology", "[+3 Science]"),
    "Library of Dreams" to listOf("Free Technology", "[+3 Culture]"),
    "Kotakula Skyscraper" to listOf("Free [Great Prophet] appears", "[+2 Faith] from every [Business Center]"),
    "Spire of the High Priest" to listOf("Free [Great Prophet] appears", "[+2 Faith] from every [Business Center]"),
    "Crystal Palace" to listOf("[+15]% Production when constructing [All] buildings [in all cities]"),
    "Aether Gardens" to listOf("[+15]% growth [in all cities]"),
    "Aether Broadcast Tower" to listOf("[+5 Culture]", "[+1 Happiness] from every [Broadcast Tower]"),
    "Bell of Echoes" to listOf("[+5 Culture]", "[+1 Happiness] from every [Broadcast Tower]"),
    "Large Hadron Collider" to listOf("[2] free [Great Scientist] units appear"),
    "Eye of the Abyss" to listOf("Free [Great Scientist] appears", "[+50]% [Science] [in this city]"),
    "World Wide Web" to listOf("[+25]% [Culture] [in all cities]", "KM: Reveals [+10]% opposing faction military units"),
    "World Tree" to listOf("[+25]% [Culture] [in all cities]", "KM: Reveals [+10]% opposing faction military units"),
    "Kotakula Office" to listOf("[+5 Gold, +5 Culture, +5 Science, +5 Faith] [in capital]", "KM: [+10]% Strength vs opposing faction"),
    "Kotakula Hall" to listOf("[+5 Gold, +5 Culture, +5 Science, +5 Faith] [in capital]", "KM: [+10]% Strength vs opposing faction"),
)

/** Section 7: Buildings.json, plus the culture projects of section 11.1 */
fun GenContext.generateBuildings() {
    translations.section("Buildings and wonders (section 7)")
    val vanilla = vanilla("Buildings.json").associateBy { it["name"] as String }
    val result = ArrayList<JObj>()

    // 7.1 common buildings and wonders
    val common = spec.table("### 7.1.")
    for (row in common) buildingIds[row["Название"]] = SpecDoc.id(row["ID"])
    for (row in common) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val building = JObj(vanilla[vanillaWonderNames[id] ?: id]!!)
        building["name"] = id
        building["requiredTech"] = if (SpecDoc.isEmpty(row["Технология"])) null else techId(row["Технология"])
        if (!SpecDoc.isEmpty(row["Цена"])) building["cost"] = row["Цена"].toInt()
        if (!SpecDoc.isEmpty(row["Содержание"])) building["maintenance"] = row["Содержание"].toInt().takeIf { it > 0 }
        when (id) {
            "Shrine" -> { building["faith"] = 2; translations.add("Shrine#Technocrat", "Гараж стартапа") }
            "Stonehenge" -> building["faith"] = 6
            "Monument" -> building["culture"] = 2
        }
        adaptVanillaUniques(building)
        result += building
        if (id == "Library") {
            // 7.1: Humanists get the Apprentice Tower instead, with the same effects
            val tower = JObj(building)
            tower["name"] = "Apprentice Tower"
            tower["faction"] = "Humanist"
            tower["replaces"] = "Library"
            translations.add("Apprentice Tower", "Башня ученика")
            buildingIds["Башня ученика"] = "Apprentice Tower"
            result += tower
        }
    }

    // 7.2 faction buildings
    val factionRows = spec.table("### 7.2.", 0)
    val descriptionRows = spec.table("### 7.2.", 1)
    for (row in factionRows) {
        buildingIds[row["Технари"]] = SpecDoc.id(row["ID технарей"])
        buildingIds[row["Гумы"]] = SpecDoc.id(row["ID гумов"])
    }
    for (row in factionRows) {
        val techs = row["Технология"].split('/').map { techId(it) }
        val description = descriptionRows.first { it["Пара"].startsWith(row["Технари"] + " /") }
        for ((index, faction) in listOf("Technocrat", "Humanist").withIndex()) {
            val id = SpecDoc.id(row[if (index == 0) "ID технарей" else "ID гумов"])
            val russian = row[if (index == 0) "Технари" else "Гумы"]
            translations.add(id, russian)
            val descriptionKey = "Description: $id"
            translations.add(descriptionKey, description[if (index == 0) "Технари" else "Гумы"])
            val building = JObj()
            building["name"] = id
            building["faction"] = faction
            if (faction == "Humanist") building["replaces"] = SpecDoc.id(row["ID технарей"])
            building["cost"] = row["Цена"].toInt()
            building["maintenance"] = row["Содерж."].toInt().takeIf { it > 0 }
            building["requiredTech"] = techs[index]
            val uniques = ArrayList<String>()
            when (val requirement = row["Требует"]) {
                "—" -> {}
                "у побережья" -> uniques += "Must be next to [Coast]"
                "рядом Гора" -> uniques += "Must be next to [Mountain]"
                else -> building["requiredBuilding"] = buildingId(requirement)
            }
            for (raw in SpecDoc.backticked(row["Эффект (uniques)"])) {
                if (raw.contains('…')) continue // "after level 2 gives 60%" - covered by the +10 points below
                val bracketContent = raw.removePrefix("[").removeSuffix("]")
                if (raw.startsWith("[") && raw.endsWith("]") && bracketContent in resourceIds.values) continue // "(у гумов `[Mana Crystals]`)"
                var unique = fixUnique(raw)
                Regex("""^\[\+(\d+)] City Strength$""").find(unique)?.let { building["cityStrength"] = it.groupValues[1].toInt(); unique = "" }
                Regex("""^\[\+(\d+)] City HP$""").find(unique)?.let { building["cityHealth"] = it.groupValues[1].toInt(); unique = "" }
                // Intelligence Department / Crystal Ball: +10 points to the espionage share (section 2.7)
                if (unique.startsWith("KM: Reveals [35]%")) unique = "KM: Reveals [+10]% opposing faction military units"
                if (faction == "Humanist") for ((t, h) in resourceMirror) unique = unique.replace("[$t]", "[$h]")
                if (unique.isNotEmpty()) uniques += unique
            }
            if (row["Эффект (uniques)"].contains("ванильная связь")) uniques += "Connects trade routes over water"
            if (row["Эффект (uniques)"].contains("Нельзя строить, если в городе есть Электростанция"))
                uniques += "Only available <in cities without a [Power Plant]>"
            if (id == "Interuniverse Shipyard" || id == "Celestial Chancery") {
                building["isNationalWonder"] = true
                uniques += listOf("Triggers a global alert upon completion", "Cannot be hurried",
                    "Only available <when [${if (index == 0) "Interuniverse Flight" else "Gates of Paradise"}] Victory is enabled>")
            }
            building["uniques"] = uniques.distinct().ifEmpty { null }
            building["civilopediaText"] = listOf(jobj("text" to descriptionKey))
            result += building
        }
    }

    // 7.3 faction wonders
    val wonderRows = spec.table("### 7.3.", 0)
    val quoteRows = spec.table("### 7.3.", 1)
    for (row in wonderRows) {
        val techs = row["Технология"].split('/').map { techId(it) }
        for ((index, faction) in listOf("Technocrat", "Humanist").withIndex()) {
            val cell = row[if (index == 0) "Технари" else "Гумы"]
            val id = SpecDoc.id(cell)
            val russian = SpecDoc.withoutBackticks(cell)
            translations.add(id, russian)
            buildingIds[russian] = id
            val quoteKey = "Quote: $id"
            translations.add(quoteKey, quoteRows.first { it["Чудо"] == russian }["Цитата"])
            result += jobj(
                "name" to id,
                "faction" to faction,
                "isWonder" to true,
                "cost" to row["Цена"].toInt(),
                "requiredTech" to techs[index],
                "uniques" to wonderEffects[id] ?: throw IllegalArgumentException("No effects for wonder $id"),
                "quote" to quoteKey,
            )
        }
    }

    // 11.1 culture projects
    translations.section("Culture projects (section 11.1)")
    for ((index, row) in spec.table("### 11.1.").withIndex()) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val descriptionKey = "Description: $id"
        translations.add(descriptionKey, row["Описание"])
        result += jobj(
            "name" to id,
            "faction" to if (index == 0) "Technocrat" else "Humanist",
            "replaces" to if (index == 0) null else "Eternal Teambuilding",
            "isNationalWonder" to true,
            "cost" to row["Цена"].toInt(),
            "uniques" to listOf("Unavailable <when number of [Completed Policy branches] is less than [5]>",
                "Triggers a global alert upon build start", "Triggers a Cultural Victory upon completion",
                "Only available <when [Cultural] Victory is enabled>", "Cannot be hurried"),
            "civilopediaText" to listOf(jobj("text" to descriptionKey)),
        )
    }

    // Buildings granted by vanilla beliefs; names for Technocrats (section 9.3)
    for (name in vanillaBeliefBuildings) {
        val building = vanilla[name] ?: continue
        result += adaptVanillaUniques(JObj(building))
    }
    translations.add("Cathedral#Technocrat", "Опенспейс")
    translations.add("Mosque#Technocrat", "Коворкинг")
    translations.add("Pagoda#Technocrat", "Переговорка")

    writeJson("Buildings.json", result)
}
