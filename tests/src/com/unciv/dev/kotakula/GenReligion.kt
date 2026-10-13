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

    // Vanilla beliefs stay common; Technocrats see them as LLCs and directives (9.3)
    translations.section("Beliefs (section 9.3, 9.5)")
    val beliefs = vanilla("Beliefs.json").map { adaptVanillaUniques(it) }.toMutableList()
    for (belief in beliefs) {
        val name = belief["name"] as String
        val russian = vanillaRussian[name] ?: continue
        val technocratName = FactionWording.forTechnocrat(russian)
        val template = if (belief["type"] == "Pantheon") "ООО «$technocratName»" else "Директива «$technocratName»"
        translations.add("$name#Technocrat", template)
    }

    val rows = spec.table("### 9.5.")
    val descriptions = spec.sectionText("### 9.5.").joinToString(" ").substringAfter("Описания:")
    fun descriptionOf(russian: String) = Regex("$russian — «([^»]+)»").find(descriptions)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("No description for belief $russian")
    for (row in rows) {
        val type = beliefTypes[row["Тип"]]!!
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
