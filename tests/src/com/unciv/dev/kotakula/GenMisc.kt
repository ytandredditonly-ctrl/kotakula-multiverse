package com.unciv.dev.kotakula

import java.io.File

/** Common buildings that later-era starts may hand out (faction buildings would differ per side) */
private val commonSettlerBuildings = setOf("Monument", "Granary", "Shrine", "Lighthouse", "Barracks", "Library", "Walls")
private val commonWonders = listOf("Stonehenge", "Temple of Artemis", "Great Library", "Pyramids", "Great Lighthouse", "Colossus")

/** Section 5.1 / 4.3: Eras.json - the 8 vanilla era ids, shown under faction-specific names */
@Suppress("UNCHECKED_CAST")
fun GenContext.generateEras() {
    translations.section("Eras (section 4.3)")
    val eras = vanilla("Eras.json").filter { it["name"] != "Future era" }
    for ((index, era) in eras.withIndex()) {
        era["startingMilitaryUnit"] = when (index) { 0 -> "Warrior"; 1, 2 -> "Spearman"; else -> "Swordsman" }
        era["settlerBuildings"] = (era["settlerBuildings"] as List<String>?)?.map { vanillaBuildingRenames[it] ?: it }
            ?.filter { it in commonSettlerBuildings }?.ifEmpty { null }
        if (era["startingObsoleteWonders"] != null)
            era["startingObsoleteWonders"] = if (index >= 2) commonWonders else null
        // No Unciv spies in the mod (section 10.1, England)
        val uniques = (era["uniques"] as List<String>?).orEmpty().filterNot { it.contains("spy") }.toMutableList()
        if (era["name"] == "Industrial era") uniques += "KM: Gain a free [Follower] belief" // section 9.1
        era["uniques"] = uniques.ifEmpty { null }
    }
    writeJson("Eras.json", eras)

    for (row in spec.table("### 4.3.")) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Без фракции"])
        translations.add("$id#Technocrat", row["Технари"])
        translations.add("$id#Humanist", row["Гумы"])
    }
}

/** Section 11: VictoryTypes.json */
fun GenContext.generateVictories() {
    translations.section("Victories (section 11)")
    val vanilla = vanilla("VictoryTypes.json").associateBy { it["name"] as String }
    val texts = spec.table("### 11.4.").associate { it["Событие"] to it["Текст"] }
    for (row in spec.table("## 11. ")) translations.add(SpecDoc.id(row["ID"]), row["Название"])

    fun key(id: String, kind: String) = "$id: $kind"
    // Victory texts are shown to the winner (victoryString) or the loser (defeatString); per-faction wording via #Faction keys
    translations.add(key("Interuniverse Flight", "victory"), texts["Межвселенский полёт (победа)"]!!)
    translations.add(key("Interuniverse Flight", "defeat"), texts["Межвселенский полёт (поражение гума)"]!!)
    translations.add(key("Gates of Paradise", "victory"), texts["Врата в рай (победа)"]!!)
    translations.add(key("Gates of Paradise", "defeat"), texts["Врата в рай (поражение технаря)"]!!)
    translations.add(key("Cultural", "victory"), texts["Культурная (технари)"]!!)
    translations.add(key("Cultural", "victory") + "#Technocrat", texts["Культурная (технари)"]!!)
    translations.add(key("Cultural", "victory") + "#Humanist", texts["Культурная (гумы)"]!!)
    translations.add(key("Faction Victory", "victory"), texts["Фракционная (победа технарей)"]!!)
    translations.add(key("Faction Victory", "victory") + "#Technocrat", texts["Фракционная (победа технарей)"]!!)
    translations.add(key("Faction Victory", "victory") + "#Humanist", texts["Фракционная (победа гумов)"]!!)
    translations.add(key("Faction Victory", "defeat"), texts["Фракционная (поражение)"]!!)
    translations.add("Build the culture project of your faction after 5 policy branches to win!",
        "Завершите 5 веток политик своей фракции и постройте её проект культуры!")
    translations.add("Build the Interuniverse Shipyard and deliver all parts to the capital!",
        "Постройте Межвселенскую верфь и доставьте в столицу все части корабля!")
    translations.add("Build the Celestial Chancery and deliver all relics to the capital!",
        "Постройте Небесную канцелярию и доставьте в столицу все реликвии!")
    translations.add("Leave no civilization of the other faction alive!",
        "Не оставьте в живых ни одной цивилизации другой фракции!")

    val cultural = JObj(vanilla["Cultural"]!!)
    cultural["victoryScreenHeader"] = "Build the culture project of your faction after 5 policy branches to win!"
    cultural["milestones"] = listOf("Complete [5] Policy branches", "Build [Eternal Teambuilding]")
    cultural["victoryString"] = key("Cultural", "victory")

    val victories = listOf(
        vanilla["Domination"]!!,
        cultural,
        jobj(
            "name" to "Interuniverse Flight",
            "faction" to "Technocrat",
            "victoryScreenHeader" to "Build the Interuniverse Shipyard and deliver all parts to the capital!",
            "milestones" to listOf("Build [Interuniverse Shipyard]", "Add all [spaceship parts] in capital"),
            "requiredSpaceshipParts" to listOf("Reality Accelerator", "Reality Accelerator", "Reality Accelerator",
                "Crew Capsule", "Kotakula Engine", "Cat Cryochamber"),
            "victoryString" to key("Interuniverse Flight", "victory"),
            "defeatString" to key("Interuniverse Flight", "defeat"),
        ),
        jobj(
            "name" to "Gates of Paradise",
            "faction" to "Humanist",
            "victoryScreenHeader" to "Build the Celestial Chancery and deliver all relics to the capital!",
            "milestones" to listOf("Build [Celestial Chancery]", "Add all [spaceship parts] in capital"),
            "requiredSpaceshipParts" to listOf("Celestial Stair Step", "Celestial Stair Step", "Celestial Stair Step",
                "Halo Generator", "Key of the Holy Watchman", "Ark of the Righteous"),
            "victoryString" to key("Gates of Paradise", "victory"),
            "defeatString" to key("Gates of Paradise", "defeat"),
        ),
        vanilla["Time"]!!,
        jobj(
            "name" to "Faction Victory",
            "victoryScreenHeader" to "Leave no civilization of the other faction alive!",
            "milestones" to listOf("KM: Destroy all civilizations of the opposing faction"),
            "victoryString" to key("Faction Victory", "victory"),
            "defeatString" to key("Faction Victory", "defeat"),
        ),
    )
    writeJson("VictoryTypes.json", victories)
}

/** Section 2.1: Events.json with the faction choice window, plus the vanilla tutorial tasks */
fun GenContext.generateEvents() {
    translations.section("Faction choice (section 2.1)")
    val texts = spec.table("### 2.1.").associate { it["Элемент"] to it["Текст"] }
    translations.add("The Great Choice of Kotakula", texts["Заголовок"]!!)
    translations.add("Choose your side: description", texts["Описание"]!!)
    translations.add("I AM A TECHNOCRAT", texts["Кнопка 1"]!!)
    translations.add("Technocrat: hint", texts["Подсказка 1"]!!)
    translations.add("I AM A HUMANIST", texts["Кнопка 2"]!!)
    translations.add("Humanist: hint", texts["Подсказка 2"]!!)
    translations.add("You chose the path of the Technocrat. The Humanists already know and are offended.",
        texts["Уведомление после выбора (технарь)"]!!)
    translations.add("You chose the path of the Humanist. The Technocrats already know and have drawn up a table of complaints.",
        texts["Уведомление после выбора (гум)"]!!)

    val choose = jobj(
        "name" to "ChooseFaction",
        "text" to "The Great Choice of Kotakula",
        "civilopediaText" to listOf(jobj("text" to "Choose your side: description")),
        "choices" to listOf(
            jobj(
                "text" to "I AM A TECHNOCRAT",
                "civilopediaText" to listOf(
                    jobj("extraImage" to "Illustrations/ChooseTechnocrat", "imageSize" to 160),
                    jobj("text" to "Technocrat: hint"),
                ),
                "uniques" to listOf("KM: Set faction [Technocrat] <hidden from users>"),
                "keyShortcut" to "T",
            ),
            jobj(
                "text" to "I AM A HUMANIST",
                "civilopediaText" to listOf(
                    jobj("extraImage" to "Illustrations/ChooseHumanist", "imageSize" to 160),
                    jobj("text" to "Humanist: hint"),
                ),
                "uniques" to listOf("KM: Set faction [Humanist] <hidden from users>"),
                "keyShortcut" to "H",
            ),
        ),
    )
    writeJson("Events.json", listOf(choose) + vanilla("Events.json"))
}

/** GlobalUniques.json (section 9.1 holy city bonus), ModOptions.json, Personalities.json and plain vanilla copies */
@Suppress("UNCHECKED_CAST")
fun GenContext.generateMisc() {
    val global = JsonText.read(File(vanillaDir, "GlobalUniques.json")) as JObj
    global["uniques"] = (global["uniques"] as List<String>) + "KM: Holy city bonus"
    writeJson("GlobalUniques.json", global)

    writeJson("ModOptions.json", jobj(
        "isBaseRuleset" to true,
        "author" to "Kotakula",
        "modUrl" to "",
        "topics" to listOf("Rulesets"),
    ))

    // Section 12.2: branch priorities from the leaders' focuses
    val personalities = vanilla("Personalities.json")
    for (personality in personalities) {
        fun focus(name: String) = (personality[name] as Number?)?.toFloat() ?: 5f
        val priorities = JObj()
        for ((branch, archetype) in branchArchetypes) {
            priorities[branch] = when (archetype) {
                BranchArchetype.Production -> focus("production")
                BranchArchetype.Growth -> (focus("expansion") + focus("food")) / 2
                BranchArchetype.Military -> (focus("military") + focus("declareWar")) / 2
                BranchArchetype.FaithGold -> (focus("faith") + focus("gold")) / 2
                BranchArchetype.Science -> (focus("science") + focus("culture")) / 2
                BranchArchetype.Final -> 20f // section 12.2: taken with the highest priority as soon as available
            }.toInt()
        }
        personality["priorities"] = priorities
        if (personality["preferredVictoryType"] == "Diplomatic") personality["preferredVictoryType"] = "Cultural"
    }
    writeJson("Personalities.json", personalities)

    for (file in listOf("Speeds.json", "Difficulties.json", "Terrains.json", "Quests.json", "Specialists.json",
            "UnitTypes.json", "CityStateTypes.json", "Ruins.json", "UnitNameGroups.json"))
        writeJson(file, vanilla(file).map { adaptVanillaUniques(it) })

    // Section 6.6: statuses of summoned and expiring units
    translations.section("Summoned units (section 6.6)")
    translations.add("Summoned", "Призванный")
    translations.add("Expiring", "Временный")
    val promotions = vanilla("UnitPromotions.json").map { adaptVanillaUniques(it) } + listOf(
        jobj("name" to "Summoned", "uniques" to listOf("KM: Summoned")),
        jobj("name" to "Expiring"),
    )
    writeJson("UnitPromotions.json", promotions)
}
