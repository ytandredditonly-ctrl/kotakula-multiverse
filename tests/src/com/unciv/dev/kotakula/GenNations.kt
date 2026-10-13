package com.unciv.dev.kotakula

/** Russian nation names of section 10.2 -> vanilla ids */
private val nationIds = mapOf(
    "Вавилон" to "Babylon", "Греция" to "Greece", "Китай" to "China", "Египет" to "Egypt", "Англия" to "England",
    "Франция" to "France", "Россия" to "Russia", "Рим" to "Rome", "Аравия" to "Arabia", "Америка" to "America",
    "Япония" to "Japan", "Индия" to "India", "Германия" to "Germany", "Османы" to "The Ottomans", "Корея" to "Korea",
    "Ирокезы" to "Iroquois", "Персия" to "Persia", "Полинезия" to "Polynesia", "Сиам" to "Siam", "Испания" to "Spain",
    "Сонгай" to "Songhai", "Монголия" to "Mongolia", "Ацтеки" to "Aztecs", "Инки" to "Inca", "Дания" to "Denmark",
    "Гунны" to "The Huns", "Нидерланды" to "The Netherlands", "Швеция" to "Sweden", "Австрия" to "Austria",
    "Карфаген" to "Carthage", "Византия" to "Byzantium", "Кельты" to "Celts", "Эфиопия" to "Ethiopia", "Майя" to "The Maya",
)

/** Section 10: Nations.json */
@Suppress("UNCHECKED_CAST")
fun GenContext.generateNations() {
    translations.section("Nations: Heritage (section 10)")
    val nations = vanilla("Nations.json")
    val heritage = spec.table("### 10.2.").associateBy { nationIds[it["Нация"]] ?: throw IllegalArgumentException("Unknown nation ${it["Нация"]}") }
    require(heritage.size == 34) { "Section 10.2 must list 34 nations" }

    for (nation in nations) {
        val name = nation["name"] as String
        if (nation["cityStateType"] != null || name == "Spectator" || name == "Barbarians") {
            if (name == "Barbarians") {
                // Section 2.6: the Civilopedia text of the barbarians
                val text = Regex("Текст в Цивилопедии: «([^»]+)»").find(spec.sectionText("### 2.6.").joinToString(" "))!!.groupValues[1]
                val key = "Barbarians do not choose a side. They plunder both. They are the only honest people in the multiverse"
                nation["civilopediaText"] = listOf(mapOf("text" to key))
                translations.add(key, text)
            }
            adaptVanillaUniques(nation)
            continue
        }
        val row = heritage[name] ?: throw IllegalArgumentException("Major nation $name has no Heritage in 10.2")
        var uniques = (nation["uniques"] as List<String>?).orEmpty().mapNotNull { adaptVanillaUnique(it) }.toMutableList()

        // 10.1 adaptations
        when (name) {
            "Russia" -> uniques += "[+100]% [Stardust] resource production"
            "Arabia" -> uniques += "[+100]% [Moonsilver] resource production"
            "England" -> {
                uniques.removeAll { it.startsWith("Gain an extra spy") }
                uniques += "KM: Reveals [+5]% opposing faction military units"
            }
            "Polynesia" -> uniques.removeAll { it.contains("[Moai]") }
            "The Maya" -> uniques = uniques.map { it.replace("after researching [Theology]", "after researching [Medieval era]") }.toMutableList()
        }
        if (nation["preferredVictoryType"] == "Diplomatic") nation["preferredVictoryType"] = "Cultural"

        // 10.2 Heritage: shown as a comment line, then its two uniques
        val heritageKey = "Heritage of $name"
        translations.add(heritageKey, "Наследие «${row["Наследие"]}»: ${row["Описание"]}")
        uniques += "Comment [$heritageKey]"
        uniques += SpecDoc.backticked(row["Unique"]).map { fixUnique(it) }
        nation["uniques"] = uniques
    }
    writeJson("Nations.json", nations)
}
