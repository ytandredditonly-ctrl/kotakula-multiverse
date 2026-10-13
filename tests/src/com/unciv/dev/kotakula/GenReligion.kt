package com.unciv.dev.kotakula

/** Section 9.3: corporation ids (must match FactionReligion.corporations in the engine) and their names */
private val corporations = listOf(
    "Kotakula Holdings" to "Kotakula Holdings",
    "Horns and Hooves LLC" to "ООО Рога и Копыта",
    "Megacorp" to "Мегакорп",
    "Evil Corporation (Rebranded)" to "Корпорация Зла (ребрендинг)",
    "Startup Inc" to "СтартапЪ",
    "Pixel and Sons" to "Пиксель и Сыновья",
    "Global Deadline" to "Глобал Дедлайн",
    "Infinite Coffee Inc" to "Бесконечный Кофе Инк.",
    "Synergy of Synergies" to "Синергия Синергий",
    "Cloud Solutions of Clouds" to "Облачные Решения Облаков",
    "Quarterly Report Group" to "Квартальный Отчёт Групп",
    "Effective Managers" to "Эффективные Менеджеры",
)

/** Section 9.5: ids of the faction beliefs, by Russian name */
private val beliefIds = mapOf(
    "Культ кофе" to "Coffee Cult", "Культ дедлайна" to "Deadline Cult",
    "Корпоративная столовая" to "Corporate Canteen", "Удалёнка" to "Remote Work",
    "Дивиденды" to "Dividends", "Агрессивный маркетинг" to "Aggressive Marketing",
    "Дух мандрагоры" to "Spirit of Mandrake", "Хранители очага" to "Hearth Keepers",
    "Монастырская трапеза" to "Monastery Meal", "Медитация" to "Meditation",
    "Десятина" to "Holy Tithe", "Святое воинство" to "Holy Host",
)

private val beliefTypes = mapOf("Пантеон" to "Pantheon", "Последователь" to "Follower", "Основатель" to "Founder", "Усилитель" to "Enhancer")

/** Section 9: Religions.json, Beliefs.json, FactionActions.json */
fun GenContext.generateReligion(vanillaRussian: Map<String, String>) {
    translations.section("Religions and corporations (section 9)")
    val vanillaReligions = JsonText.read(java.io.File(vanillaDir, "Religions.json")) as List<*>
    writeJson("Religions.json", vanillaReligions + corporations.map { it.first })
    for ((id, name) in corporations) translations.add(id, name)

    // Vanilla beliefs stay common; Technocrats see them as directives (9.3). The vanilla pantheons are gone:
    // the factions have their gods and startups instead (gods-and-startups.md, 1.1)
    translations.section("Beliefs (section 9.3, 9.5)")
    val beliefs = vanilla("Beliefs.json").filter { it["type"] != "Pantheon" }.map { adaptVanillaUniques(it) }.toMutableList()
    for (belief in beliefs) {
        val name = belief["name"] as String
        val russian = vanillaRussian[name] ?: continue
        translations.add("$name#Technocrat", "Директива «${FactionWording.forTechnocrat(russian)}»")
    }

    val rows = spec.table("### 9.5.")
    val descriptions = spec.sectionText("### 9.5.").joinToString(" ").substringAfter("Описания:")
    fun descriptionOf(russian: String) = Regex("$russian — «([^»]+)»").find(descriptions)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("No description for belief $russian")
    for (row in rows) {
        val type = beliefTypes[row["Тип"]]!!
        if (type == "Pantheon") continue // moved to gods-and-startups.md
        for ((faction, nameColumn, effectIndex) in listOf(Triple("Technocrat", "Технари", 2), Triple("Humanist", "Гумы", 4))) {
            val russian = row[nameColumn]
            val id = beliefIds[russian]!!
            translations.add(id, russian)
            val descriptionKey = "Description: $id"
            translations.add(descriptionKey, descriptionOf(russian))
            // "То же" = the Technocrat effect; filters naming Technocrat buildings also match their Humanist replacement
            val effect = if (row.raw[effectIndex].startsWith("То же")) row.raw[2] else row.raw[effectIndex]
            var uniques = SpecDoc.backticked(effect).map { fixUnique(it) }
            if (faction == "Humanist") uniques = uniques.map { it.replace("[Coffee]", "[Mandrake]") }
            // Follower beliefs apply in the cities following the religion
            if (type == "Follower") uniques = uniques.map { if (Regex("""^\[[^\]]+]$""").matches(it)) "$it [in this city]" else it }
            beliefs += jobj(
                "name" to id,
                "type" to type,
                "faction" to faction,
                "uniques" to uniques,
                "civilopediaText" to listOf(jobj("text" to descriptionKey)),
            )
        }
    }
    beliefs += generatePantheons()
    writeJson("Beliefs.json", beliefs)

    // 9.4 faith actions
    translations.section("Faith actions (section 9.4)")
    translations.add("Faith actions", "Молитвы и ритуалы")
    translations.add("Faith actions#Technocrat", "Корпоративные инициативы")
    val actions = ArrayList<JObj>()
    for ((tableIndex, faction) in listOf(0 to "Technocrat", 1 to "Humanist")) {
        for (row in spec.table("### 9.4.", tableIndex)) {
            val id = SpecDoc.id(row["ID"])
            translations.add(id, row["Название"])
            val quoteKey = "Quote: $id"
            translations.add(quoteKey, row["Описание"])
            val effect = row["Эффект"]
            val uniques = when {
                effect.contains("выбранный город-государство") -> listOf("KM: [+40] Influence with chosen city-state")
                effect.contains("полностью лечатся") -> listOf("KM: All units fully heal", "[10] HP when healing <for [5] turns>")
                else -> SpecDoc.backticked(effect).map { fixUnique(it) }
            }
            actions += jobj(
                "name" to id,
                "faction" to faction,
                "baseCost" to row["База"].toInt(),
                "duration" to row["Длит."].toInt(),
                "cooldown" to 10,
                "uniques" to uniques,
                "quote" to quoteKey,
            )
        }
    }
    writeJson("FactionActions.json", actions)
}

/** Gods and startups (docs/kotakula/gods-and-startups.md): 13 gods of the Humanists and 13 startups of the Technocrats */
fun GenContext.generatePantheons(): List<JObj> {
    val gods = SpecDoc(java.io.File(KotakulaModGenerator.GODS_PATH))
    translations.section("Gods and startups (gods-and-startups.md)")
    val result = ArrayList<JObj>()
    for ((heading, faction) in listOf("### 2.1." to "Humanist", "### 2.2." to "Technocrat")) {
        val rows = gods.table(heading)
        require(rows.size == 13) { "$heading must list 13 pantheons" }
        for (row in rows) {
            val id = SpecDoc.id(row["ID"])
            translations.add(id, row["Название"])
            val descriptionKey = "Description: $id"
            translations.add(descriptionKey, row["Описание"])
            result += jobj(
                "name" to id,
                "type" to "Pantheon",
                "faction" to faction,
                "uniques" to pantheonUniques(row.raw[2]),
                "civilopediaText" to listOf(jobj("text" to descriptionKey)),
            )
        }
    }
    return result
}

/** The effect column: `uniques`, "то же для `[X]` и `[Y]`" (the previous tile unique for other tiles) and notes */
fun GenContext.pantheonUniques(effect: String): List<String> {
    val parts = effect.split("то же для")
    val uniques = SpecDoc.backticked(parts[0]).toMutableList()
    for (part in parts.drop(1)) {
        val base = uniques.last()
        for (filter in SpecDoc.backticked(part))
            uniques += Regex("""\[[^\]]+] tiles""").replaceFirst(base, "$filter tiles")
    }
    val lifetime = Regex("""живёт (\d+) ход""").find(effect)?.groupValues?.get(1)
    return uniques.map { unique ->
        var u = fixUnique(unique)
        // The notation of the addendum, written as the matching Unciv uniques
        if (u == "Receive [+100]% Gold from Barbarian encampments") u += " and pillaging Cities"
        u = Regex("""^(\[[^\]]+]) from (\[[^\]]+]) tiles (\[[^\]]+]) <when not improved>$""").replace(u, "$1 from $2 tiles without [improved] $3")
        u = Regex("""^\[([+-]\d+)]% tile improvement construction time$""").replace(u, "[$1]% construction time for [All] improvements")
        if (effect.contains("масштабируется скоростью") && u.startsWith("Gain [")) u += " <(modified by game speed)>"
        if (lifetime != null && u.startsWith("KM: Summons")) u += " <KM: Expires after [$lifetime] turns>"
        u
    }
}
