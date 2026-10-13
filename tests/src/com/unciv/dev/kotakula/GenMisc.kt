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
    writeJson("Events.json", listOf(choose) + godsAndStartupsEvents() + vanilla("Events.json"))
}

/** GlobalUniques.json (section 9.1 holy city bonus), ModOptions.json, Personalities.json and plain vanilla copies */
@Suppress("UNCHECKED_CAST")
fun GenContext.generateMisc() {
    val global = JsonText.read(File(vanillaDir, "GlobalUniques.json")) as JObj
    // gods-and-startups.md 3: the pivot of the startups
    global["uniques"] = (global["uniques"] as List<String>) + "KM: Holy city bonus" + "KM: Pivot <for [Technocrat] faction>"
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
            "CityStateTypes.json", "Ruins.json", "UnitNameGroups.json"))
        writeJson(file, vanilla(file).map { adaptVanillaUniques(it) })

    // Section 6.1 names the unit types of the original Civ V ruleset (Melee, Ranged, Armor, WaterMelee...). In
    // Gods & Kings they are bare deprecated entries: give them the uniques of their G&K counterparts
    val unitTypes = vanilla("UnitTypes.json").map { adaptVanillaUniques(it) }
    val typesByName = unitTypes.associateBy { it["name"] as String }
    for ((legacy, current) in specUnitTypeOf) {
        val target = typesByName[current] ?: continue
        val source = typesByName[legacy] ?: continue
        if (target["uniques"] == null && source["uniques"] != null)
            target["uniques"] = (source["uniques"] as List<*>).map { renameUnitTypesInUnique(it as String) }
        if (target["civilopediaText"] == null && source["civilopediaText"] != null) target["civilopediaText"] = source["civilopediaText"]
    }
    writeJson("UnitTypes.json", unitTypes)

    // Section 6.6: statuses of summoned and expiring units
    translations.section("Summoned units (section 6.6)")
    translations.add("Summoned", "Призванный")
    translations.add("Expiring", "Временный")
    // Promotions of G&K are given to G&K unit types: add the section 6.1 types, or no unit of the mod could be promoted
    @Suppress("UNCHECKED_CAST")
    val vanillaPromotions = vanilla("UnitPromotions.json").map { adaptVanillaUniques(it) }.onEach { promotion ->
        val types = promotion["unitTypes"] as List<String>? ?: return@onEach
        promotion["unitTypes"] = (types + types.mapNotNull { specUnitTypeOf[it] }).distinct()
    }
    val promotions = vanillaPromotions + listOf(
        jobj("name" to "Summoned", "uniques" to listOf("KM: Summoned")),
        jobj("name" to "Expiring"),
    )
    writeJson("UnitPromotions.json", promotions)
}

/** Gods & Kings unit type -> the unit type of section 6.1 that stands for it in the mod */
val specUnitTypeOf = mapOf(
    "Sword" to "Melee", "Gunpowder" to "Melee", "Archery" to "Ranged", "Ranged Gunpowder" to "Ranged",
    "Armored" to "Armor", "Civilian Water" to "WaterCivilian", "Melee Water" to "WaterMelee", "Ranged Water" to "WaterRanged",
    "Submarine" to "WaterSubmarine", "Aircraft Carrier" to "WaterAircraftCarrier", "Atomic Bomber" to "AtomicBomber",
)

private val unitTypeFilter = Regex("""\[(${specUnitTypeOf.keys.joinToString("|") { Regex.escape(it) }})] units""")

/** "[Armored] units" -> "[Armor] units" and so on, in any unique or conditional */
fun renameUnitTypesInUnique(unique: String): String =
    unitTypeFilter.replace(unique) { "[${specUnitTypeOf[it.groupValues[1]]}] units" }

/** Last pass over the generated jsons: unit filters naming G&K unit types now name the section 6.1 ones */
fun renameUnitTypeFilters(jsonDir: java.io.File) {
    for (file in jsonDir.listFiles { f -> f.extension == "json" }.orEmpty()) {
        val text = file.readText(Charsets.UTF_8)
        val renamed = renameUnitTypesInUnique(text)
        if (renamed != text) file.writeText(renamed, Charsets.UTF_8)
    }
}

/** Gods and startups, section 3: the God's Whim (Humanists) and the Investor Round (Technocrats), every 25 turns */
fun GenContext.godsAndStartupsEvents(): List<JObj> {
    val gods = SpecDoc(File(KotakulaModGenerator.GODS_PATH))
    val mechanics = gods.table("## 3.").associateBy { it["Механика"] }
    fun quoted(text: String, prefix: String) = Regex("""$prefix «([^»]+)»""").find(text)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("No «» text after $prefix in: $text")
    val whim = mechanics["Каприз бога"]!!["Как работает"]
    val round = mechanics["Раунд инвестиций"]!!["Как работает"]
    translations.section("Gods and startups: events (gods-and-startups.md, 3)")

    // The God's Whim text names the god: "[Бог]" of the addendum is filled in by FactionPantheons.eventText
    val whimText = "[pantheon] looks at you from a cloud. The expression on its face is like a cat's in front of a vase"
    translations.add(whimText, quoted(whim, "Текст:").replace("""\[Бог\]""", "[pantheon]").replace("[Бог]", "[pantheon]"))
    translations.add("Bring a gift", quoted(whim, "Выбор 1"))
    translations.add("Pretend you did not hear", quoted(whim, "Выбор 2"))
    translations.add("Gift: 30 x (era number + 1) Faith. +20% Faith in all cities for 10 turns", "Дар: 30 × (номер эпохи + 1) веры. +20% веры во всех городах на 10 ходов")
    translations.add("-2 Happiness for 10 turns", "−2 к счастью на 10 ходов")
    val roundText = "Investors are ready to invest. The terms are 300 pages long, no time to read"
    translations.add(roundText, quoted(round, "Текст:"))
    translations.add("Take the money", quoted(round, "Выбор 1"))
    translations.add("We are doing fine anyway", quoted(round, "Выбор 2"))
    translations.add("Gold equal to 50% of the Faith, and 25% of the Faith is lost", "Золото в размере 50% веры, 25% веры теряется")

    return listOf(
        jobj(
            "name" to "GodsWhim",
            "text" to whimText,
            "choices" to listOf(
                jobj("text" to "Bring a gift",
                    "civilopediaText" to listOf(jobj("text" to "Gift: 30 x (era number + 1) Faith. +20% Faith in all cities for 10 turns")),
                    "uniques" to listOf("KM: Bring a gift to the god <hidden from users>", "Only available <KM: when the gift to the god is affordable>")),
                jobj("text" to "Pretend you did not hear",
                    "civilopediaText" to listOf(jobj("text" to "-2 Happiness for 10 turns")),
                    "uniques" to listOf("KM: Pretend not to hear the god <hidden from users>")),
            ),
        ),
        jobj(
            "name" to "InvestorRound",
            "text" to roundText,
            "choices" to listOf(
                jobj("text" to "Take the money",
                    "civilopediaText" to listOf(jobj("text" to "Gold equal to 50% of the Faith, and 25% of the Faith is lost")),
                    "uniques" to listOf("KM: Take the investors' money <hidden from users>")),
                jobj("text" to "We are doing fine anyway"),
            ),
        ),
    )
}
