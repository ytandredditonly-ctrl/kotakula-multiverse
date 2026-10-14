package com.unciv.dev.kotakula

import java.io.File

/** The spec of the three-layer map (docs/kotakula/layers.md) */
const val LAYERS_PATH = "../../docs/kotakula/layers.md"

/** Russian names of the spec -> ids of the Surface terrains used by the layer rules */
private val surfaceTerrainIds = linkedMapOf(
    "Пустыня" to "Desert", "Тундра" to "Tundra", "Снег" to "Snow", "Лес" to "Forest", "Джунгли" to "Jungle",
    "Болото" to "Marsh", "Холмы" to "Hill", "Горы" to "Mountain", "Побережье" to "Coast", "Океан" to "Ocean",
    "Луга" to "Grassland", "Равнины" to "Plains", "Озеро" to "Lakes",
)

/** Layers by their Russian names */
private val layerIds = mapOf("Подземье" to "Underground", "Небо" to "Sky", "Поверхность" to "Surface")

/** 7.4: palette element -> terrain id (the minimap color of the terrain, `RGB`) */
private val paletteTerrains = mapOf(
    "Скальная толща" to "Solid Rock", "Пол пещеры" to "Cave", "Светящиеся грибы" to "Fungal Grotto", "Магма" to "Magma Chamber",
    "Вода" to "Underground Lake", "Кристаллы" to "Crystal Vein", "Открытое небо" to "Open Sky", "Облака" to "Clouds",
    "Трава острова" to "Floating Island", "Грозовой фронт" to "Storm Front",
)

private fun rgbOf(hex: String): List<Int> {
    val h = hex.trim().removePrefix("#")
    return listOf(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
}

/** "+1 еда, −1 производство" -> "[+1 Food, -1 Production]" (the Unicode minus of the spec included) */
private fun statsUnique(cell: String): String? {
    val stats = parseStats(cell.replace('−', '-'))
    if (stats.isEmpty()) return null
    val names = mapOf("food" to "Food", "production" to "Production", "gold" to "Gold", "science" to "Science",
        "culture" to "Culture", "faith" to "Faith", "happiness" to "Happiness")
    return stats.entries.joinToString(", ", "[", "]") { (stat, amount) ->
        val value = amount as Int
        "${if (value >= 0) "+" else ""}$value ${names[stat]}"
    }
}

/**
 * Three-layer map (docs/kotakula/layers.md, sections 3, 4, 7): terrains, resources, improvements, units, buildings,
 * techs and biomes of the Underground and the Sky. Runs after [generateMisc], it extends the JSON files written before.
 */
fun GenContext.generateLayers() {
    val doc = SpecDoc(File(LAYERS_PATH))
    translations.section("Three-layer map (layers.md)")
    fun modArray(name: String) = JsonText.readArray(File(outDir, "jsons/$name"))
    fun quote(text: String) = Regex("«([^»]+)»").find(text)?.groupValues?.get(1) ?: SpecDoc.unquote(text)

    // Russian names of all layer terrains -> ids, for the "Где" cells
    val terrainIds = LinkedHashMap<String, String>()
    terrainIds.putAll(surfaceTerrainIds)

    // ---------- 3.1, 3.2, 3.3, 3.7: Terrains.json ----------
    val palette = doc.table("### 7.4.", 1).associate { row ->
        row["Элемент"] to SpecDoc.backticked(row["Цвет"]).firstOrNull()
    }
    fun colorOf(id: String): List<Int>? {
        val element = paletteTerrains.entries.firstOrNull { it.value == id }?.key ?: return null
        return palette[element]?.let { rgbOf(it) }
    }

    val terrains = modArray("Terrains.json").toMutableList()
    val magmaBonus = ArrayList<String>()
    for ((section, layer) in listOf("### 3.1." to "Underground", "### 3.2." to "Sky")) {
        for (row in doc.table(section)) {
            val id = SpecDoc.id(row["ID"])
            terrainIds[row["Название"]] = id
            translations.add(id, row["Название"])
            val descriptionKey = "Description: $id"
            translations.add(descriptionKey, row["Описание"])
            val passability = row["Проходимость"]
            val move = row["Ход"]
            val defence = row["Оборона"].replace('−', '-').removeSuffix("%")
            val uniques = arrayListOf("Doesn't generate naturally")
            if (move.contains("KM: Burrowing")) uniques += "KM: Passable for burrowing units"
            if (move.contains("KM: Flying")) uniques += "KM: Passable for flying units"
            val damage = Regex("""получает (\d+) урона""").find(passability)?.groupValues?.get(1)
            if (damage != null) uniques += "Units ending their turn on this terrain take [$damage] damage"
            magmaBonus += SpecDoc.backticked(passability).filter { it.startsWith("[") }
            val terrain = jobj(
                "name" to id,
                "type" to "Land",
                "layer" to layer,
                "impassable" to (passability.startsWith("Непроходим")).takeIf { it },
                // 7.3: the Underground Lake becomes passable with move 2 under the tundra
                "movementCost" to (Regex("""\d+""").find(move)?.value?.toInt() ?: if (id == "Underground Lake") 2 else 1),
                "defenceBonus" to (defence.toFloatOrNull()?.let { it / 100f }?.takeIf { it != 0f }),
                "RGB" to colorOf(id),
                "uniques" to uniques,
                "civilopediaText" to listOf(jobj("text" to descriptionKey)),
            )
            terrain.putAll(parseStats(row["Доход"]))
            terrains += terrain
        }
    }
    // Magma: "the adjacent passable tiles: [+1 Production]" - put on the passable Underground terrains
    for (terrain in terrains.filter { it["layer"] == "Underground" && it["impassable"] == null })
        for (bonus in magmaBonus) @Suppress("UNCHECKED_CAST") (terrain["uniques"] as MutableList<String>) += "$bonus <in tiles adjacent to [Magma Chamber] tiles>"

    // Features of the layers: Crystal Vein (3.1), Jet Stream (3.2) - paragraphs after the tables
    for ((section, occursOn) in listOf("### 3.1." to listOf("Cave"), "### 3.2." to listOf("Clouds", "Open Sky"))) {
        val line = doc.sectionText(section).firstOr("L105") { it.startsWith("Особенност") }
        val codes = SpecDoc.backticked(line)
        val id = codes[0]
        val russian = line.substringAfter("`$id`").substringBefore("(").trim()
        translations.add(id, russian)
        val descriptionKey = "Description: $id"
        translations.add(descriptionKey, quote(line))
        val uniques = arrayListOf("Doesn't generate naturally", "KM: Overlay feature")
        val feature = jobj("name" to id, "type" to "TerrainFeature", "layer" to (if (section == "### 3.1.") "Underground" else "Sky"),
            "occursOn" to occursOn, "RGB" to colorOf(id))
        for (code in codes.drop(1)) if (code.startsWith("[")) uniques += code
        if (line.contains("1/3")) uniques += "KM: Flying units spend [33]% movement here"
        feature["uniques"] = uniques
        feature["civilopediaText"] = listOf(jobj("text" to descriptionKey))
        terrains += feature
    }

    // 3.3: natural transitions - overlay features of the Surface
    for (row in doc.table("### 3.3.")) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val descriptionKey = "Description: $id"
        translations.add(descriptionKey, row["Описание"])
        val occursOn = if (id == "Cave Entrance") listOf("Plains", "Grassland", "Tundra", "Desert")
            else listOf("Grassland", "Plains", "Desert", "Tundra", "Snow")
        terrains += jobj(
            "name" to id, "type" to "TerrainFeature", "occursOn" to occursOn,
            "uniques" to listOf("Doesn't generate naturally", "KM: Overlay feature") + SpecDoc.backticked(row["Эффект"]),
            "civilopediaText" to listOf(jobj("text" to descriptionKey)),
        )
    }

    // 3.7: natural wonders of the layers
    for (row in doc.table("### 3.7.")) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val descriptionKey = "Description: $id"
        translations.add(descriptionKey, row["Описание"])
        val effect = row["Эффект"]
        val wonder = jobj(
            "name" to id, "type" to "NaturalWonder", "layer" to layerIds[row["Слой"]],
            "impassable" to effect.contains("непроходимо").takeIf { it },
            "occursOn" to listOf(if (row["Слой"] == "Подземье") "Cave" else "Floating Island"),
            "weight" to 0,
            "uniques" to listOf("Doesn't generate naturally"),
            "civilopediaText" to listOf(jobj("text" to descriptionKey)),
        )
        wonder.putAll(parseStats(effect.substringBefore(";")))
        terrains += wonder
    }
    writeJson("Terrains.json", terrains)

    // ---------- 3.5 + 7.1 + 7.2: improvements (names first, the resources need them) ----------
    val builderRows = doc.table("### 4.3.")
    val builderIds = builderRows.associate { it["Название"] to SpecDoc.id(it["ID"]) }
    val layerImprovementIds = LinkedHashMap<String, String>()
    for (row in doc.table("### 3.5.")) layerImprovementIds[row["Название"]] = SpecDoc.id(row["ID"])
    // "То же" (ditto) cells repeat the cell of the row above
    val routeRows = doc.table("### 7.1.").fold(ArrayList<SpecDoc.Row>()) { rows, row ->
        val previous = rows.lastOrNull()
        rows += if (previous == null) row else SpecDoc.Row(
            row.headers.associateWith { header -> if (row[header] == "То же") previous[header] else row[header] },
            row.raw.mapIndexed { i, cell -> if (cell == "То же") previous.raw[i] else cell })
        rows
    }
    val terraformIds = mapOf("Выработка" to "Excavation", "Выкопать грот" to "Dig Grotto",
        "Искусственный остров" to "Artificial Island", "Насыпать остров" to "Raise Island")
    for (row in routeRows) {
        val name = row["Действие"].substringBefore("(").trim()
        val id = SpecDoc.backticked(row["Действие"]).firstOrNull() ?: layerImprovementIds[name] ?: continue
        layerImprovementIds[name] = id
    }

    // ---------- 3.4: TileResources.json ----------
    val resources = modArray("TileResources.json").toMutableList()
    val resourcesByName = resources.associateBy { it["name"] as String }
    for (row in doc.table("### 3.4.")) {
        val idCell = row["ID"]
        val ids = SpecDoc.backticked(idCell)
        val layer = layerIds[row["Слой"]]!!
        val terrain = terrainIds[row["Где"]] ?: throw IllegalArgumentException("Unknown terrain '${row["Где"]}'")
        val improvement = layerImprovementIds[row["Улучшение"]] ?: throw IllegalArgumentException("Unknown improvement '${row["Улучшение"]}'")
        if (idCell.trim().startsWith("`")) {
            // A new luxury of the layer
            val id = ids.single()
            translations.add(id, row["Название"])
            val descriptionKey = "Description: $id"
            translations.add(descriptionKey, row["Описание"])
            val resource = jobj(
                "name" to id, "resourceType" to "Luxury", "terrainsCanBeFoundOn" to listOf(terrain),
                "improvement" to improvement,
                "uniques" to listOf("Doesn't generate naturally"),
                "civilopediaText" to listOf(jobj("text" to descriptionKey)),
            )
            resource.putAll(parseStats(row["Бонус клетки"]))
            resources += resource
        } else {
            // Deep and sky deposits of existing strategic resources
            val share = Regex("""(\d+)%""").find(row["Описание"])!!.groupValues[1]
            val extra = Regex("""(\d+) единиц""").find(row["Описание"])?.groupValues?.get(1)
                ?: Regex("""\+(\d+) единиц""").find(row["Описание"])?.groupValues?.get(1) ?: "0"
            for (id in ids) {
                val resource = resourcesByName[id] ?: throw IllegalArgumentException("No resource '$id'")
                @Suppress("UNCHECKED_CAST")
                resource["terrainsCanBeFoundOn"] = ((resource["terrainsCanBeFoundOn"] as List<String>?).orEmpty() + terrain).distinct()
                @Suppress("UNCHECKED_CAST")
                val improvedBy = (resource["improvedBy"] as List<String>?).orEmpty() + listOfNotNull(resource["improvement"] as String?)
                resource.remove("improvement")
                resource["improvedBy"] = (improvedBy + improvement).distinct()
                @Suppress("UNCHECKED_CAST")
                resource["uniques"] = (resource["uniques"] as List<String>?).orEmpty() +
                    "KM: [$share]% of deposits lie in the [$layer] layer on [$terrain] tiles with [$extra] extra"
            }
            // A quote in the description goes to the Civilopedia of the resource ("Лошади на облаках. Не спрашивайте")
            val quoted = Regex("«([^»]+)»").find(row["Описание"])?.groupValues?.get(1)
            if (quoted != null) for (id in ids) {
                val key = "Description: $id in the layer"
                translations.add(key, quoted)
                @Suppress("UNCHECKED_CAST")
                resourcesByName[id]!!["civilopediaText"] = (resourcesByName[id]!!["civilopediaText"] as List<Any?>?).orEmpty() + jobj("text" to key)
            }
        }
    }
    writeJson("TileResources.json", resources)

    // ---------- Improvements ----------
    val improvements = modArray("TileImprovements.json").toMutableList()
    val landSurface = listOf("Grassland", "Plains", "Desert", "Tundra", "Snow", "Hill")
    fun factionOfBuilder(cell: String): String? = when {
        cell.contains("технарей") -> "Technocrat"
        cell.contains("гумов") -> "Humanist"
        builderIds[cell] != null -> builderRows.firstOr("L229") { it["Название"] == cell }["Фракция"].let { if (it == "Технари") "Technocrat" else "Humanist" }
        else -> null
    }
    val routeByName = routeRows.associateBy { it["Действие"].substringBefore("(").trim() }
    val routeIds = routeByName.keys.mapNotNull { layerImprovementIds[it] }.filter { it !in terraformIds.values }.toSet()
    for (row in doc.table("### 3.5.")) {
        val id = SpecDoc.id(row["ID"])
        translations.add(id, row["Название"])
        val builder = row["Кто строит"]
        val where = row["Где"]
        val effect = row["Эффект"]
        val uniques = ArrayList<String>()
        uniques += SpecDoc.backticked(effect)
        if (effect.contains("больше не наносит урон")) uniques += "KM: Units here take no terrain damage"
        val builderUnit = builderIds[builder]
        if (builderUnit != null) uniques += "Only available <for [$builderUnit] units>"
        val route = routeByName[row["Название"]]
        val terrainsOn: List<String> = when {
            route != null && route["Слой"] == "Подземье" -> listOf("Solid Rock", "Cave", "Fungal Grotto")
            route != null && route["Слой"] == "Небо" -> listOf("Open Sky", "Clouds", "Storm Front")
            where.startsWith("Суша Поверхности") -> landSurface
            where.contains("без ресурса") -> { uniques += "Cannot be built on [resource] tiles"; listOf(terrainIds[where.substringBefore(" без")]!!) }
            where.contains("с ресурсом") -> { uniques += "Can only be built to improve a resource"; emptyList() }
            terrainIds[where] != null -> listOf(terrainIds[where]!!)
            else -> { uniques += "Can only be built to improve a resource"; emptyList() } // resource improvements (Cloud Loom)
        }
        val improvement = jobj(
            "name" to id,
            "faction" to factionOfBuilder(builder),
            "terrainsCanBeBuiltOn" to terrainsOn.ifEmpty { null },
            "turnsToBuild" to row["Ходов"].toInt(),
            "techRequired" to if (SpecDoc.isEmpty(row["Технология"])) null else techId(row["Технология"]),
            "uniques" to uniques.ifEmpty { null },
            "civilopediaText" to listOf(jobj("text" to "Description: $id")),
        )
        improvement.putAll(parseStats(effect.substringBefore(";")))
        translations.add("Description: $id", "${row["Где"]}. ${row["Эффект"].replace("`", "")}")
        improvements += improvement
    }
    // 7.1: terraforming
    for (row in routeRows) {
        val name = row["Действие"].substringBefore("(").trim()
        val id = terraformIds[name] ?: continue
        translations.add(id, name)
        val builderUnit = builderIds[row["Кто"]]!!
        val result = row["Результат"]
        // "Местность навсегда становится Пещерой": match the word stems, the spec declines the name
        fun stems(name: String) = name.lowercase().split(" ").map { if (it.length > 4) it.dropLast(2) else it }
        val newTerrain = terrainIds.entries.firstOr("L277") { entry -> stems(entry.key).all { result.lowercase().contains(it) } }.value
        val from = row["Где"]
        val terrainsOn = terrainIds.filterKeys { from.contains(it) }.values.toList()
        val builderTech = builderRows.firstOr("L280") { it["Название"] == row["Кто"] }["Технология"]
        improvements += jobj(
            "name" to id,
            "terrainsCanBeBuiltOn" to terrainsOn,
            "turnsToBuild" to row["Ходов"].toInt(),
            "techRequired" to techId(builderTech),
            "uniques" to listOf("KM: Turns the tile into [$newTerrain]", "Only available <for [$builderUnit] units>"),
            "civilopediaText" to listOf(jobj("text" to "Description: $id")),
        )
        translations.add("Description: $id", "${row["Где"]}: ${row["Результат"]}. ${row["Условие"]}")
    }
    // 7.2: transit nodes outside of cities
    val nodesLine = doc.sectionText("### 7.2.").firstOr("L292") { it.startsWith("**Узлы вне городов.**") }
    val access = doc.table("### 4.1.")
    // The table has two columns "Технология (колонка)": the early tech is cell 2, the late one cell 4
    fun lateTech(faction: String) = techId(access.firstOr("L295") { it["Фракция"] == faction }.raw[4].substringBefore("(").trim())
    for ((russian, faction, id) in listOf(Triple("Транзитный узел", "Technocrat", "Transit Node"), Triple("Ось стихий", "Humanist", "Elemental Axis"))) {
        translations.add(id, russian)
        improvements += jobj(
            "name" to id, "faction" to faction,
            "terrainsCanBeBuiltOn" to landSurface,
            "turnsToBuild" to Regex("""(\d+) ходов""").find(nodesLine)!!.groupValues[1].toInt(),
            "techRequired" to lateTech(if (faction == "Technocrat") "Технари" else "Гумы"),
            "uniques" to SpecDoc.backticked(nodesLine).filter { it.startsWith("KM:") } +
                "Only available <when [Underground] layer is unlocked> <when [Sky] layer is unlocked>",
            "civilopediaText" to listOf(jobj("text" to "Description: $id")),
        )
        translations.add("Description: $id", nodesLine.substringAfter(":** ").replace("`", ""))
    }
    writeJson("TileImprovements.json", improvements)

    // ---------- 4.1 techs ----------
    val techs = modArray("Techs.json")
    fun techById(id: String): JObj = techs.flatMap { @Suppress("UNCHECKED_CAST") (it["techs"] as List<JObj>) }.firstOr("L313") { it["name"] == id }
    for (row in access) {
        for ((layerColumn, techColumn) in listOf("Ранний слой" to 0, "Поздний слой" to 1)) {
            val tech = techById(techId(row.raw[if (techColumn == 0) 2 else 4].substringBefore("(").trim()))
            @Suppress("UNCHECKED_CAST")
            tech["uniques"] = (tech["uniques"] as List<String>?).orEmpty() + "KM: Unlocks [${layerIds[row[layerColumn]]}] layer"
        }
    }
    for (tech in listOf("Electricity", "Dragonology"))
        @Suppress("UNCHECKED_CAST")
        techById(tech).let { it["uniques"] = (it["uniques"] as List<String>?).orEmpty() + "KM: Layer routes work as railroads" }
    writeJson("Techs.json", techs)
    // 4.1: unlock notifications
    val unlockLine = doc.sectionText("### 4.1.").firstOr("L326") { it.startsWith("Уведомления при открытии") }
    val unlockKeys = mapOf(
        "технари, Подземье" to "We started digging. We do not know when we will stop",
        "технари, Небо" to "We took off. The Humanists have long been up there and already took offence",
        "гумы, Небо" to "We learned to soar. The Technocrats look up from below and calculate the lift",
        "гумы, Подземье" to "The earth parted. The Technocrats are already there, and they have helmets",
    )
    for ((who, key) in unlockKeys)
        translations.add(key, Regex("$who — «([^»]+)»").find(unlockLine)!!.groupValues[1])

    // ---------- 4.3 units ----------
    val units = modArray("Units.json").toMutableList()
    for (row in builderRows) {
        val id = SpecDoc.id(row["ID"])
        unitIds[row["Название"]] = id
        translations.add(id, row["Название"])
        translations.add("Description: $id", row["Описание"])
        val builds = SpecDoc.list(row["Строит"]).map { layerImprovementIds[it] ?: throw IllegalArgumentException("Unknown improvement '$it'") }
        val terraform = routeRows.filter { it["Кто"] == row["Название"] && terraformIds.containsKey(it["Действие"].substringBefore("(").trim()) }
            .map { terraformIds[it["Действие"].substringBefore("(").trim()]!! }
        val routeId = builds.firstOr("L346") { it in routeIds }
        val uniques = SpecDoc.backticked(row["Способность"]) +
            (builds + terraform).map { "Can build [$it] improvements on tiles" } +
            // 7.1: a route takes 8 turns on Solid Rock / Open Sky and 4 on passable tiles
            "[-50]% construction time for [$routeId] improvements <in [non-[Impassable]] tiles>"
        units += jobj(
            "name" to id, "faction" to (if (row["Фракция"] == "Технари") "Technocrat" else "Humanist"),
            "unitType" to "Civilian", "movement" to 2, "strength" to 0,
            "cost" to row["Цена"].toInt(), "requiredTech" to techId(row["Технология"]),
            "uniques" to uniques,
            "civilopediaText" to listOf(jobj("text" to "Description: $id")),
        )
    }
    for (row in doc.table("### 4.3.", 2)) {
        for ((unitColumn, abilityIndex) in listOf(1 to 2, 3 to 4)) {
            val unitName = row.raw[unitColumn].substringBefore("(").trim()
            val unit = units.firstOr("L362") { it["name"] == unitId(unitName) }
            @Suppress("UNCHECKED_CAST")
            unit["uniques"] = ((unit["uniques"] as List<String>?).orEmpty() + SpecDoc.backticked(row.raw[abilityIndex])).distinct()
        }
    }
    writeJson("Units.json", units)

    // ---------- 3.6 + 7.2 buildings ----------
    val buildings = modArray("Buildings.json").toMutableList()
    fun layerCondition(cell: String): List<String> {
        val coded = SpecDoc.backticked(cell).filter { it.startsWith("Only available") }
        if (coded.isNotEmpty()) return coded
        val layers = if (cell.contains("Оба слоя")) listOf("Underground", "Sky")
            else layerIds.filterKeys { cell.contains(it) }.values.toList()
        return listOf("Only available " + layers.joinToString(" ") { "<when [$it] layer is unlocked>" })
    }
    for (row in doc.table("### 3.6.")) {
        val id = SpecDoc.id(row["ID"])
        buildingIds[row["Название"]] = id
        translations.add(id, row["Название"])
        translations.add("Description: $id", row["Описание"])
        val effect = row["Эффект"]
        val uniques = layerCondition(row["Условие"]).toMutableList()
        val strength = Regex("""Сила города `([+-]\d+)%` против атак с (\S+)""").find(effect)
        if (strength != null) {
            val layer = layerIds.entries.firstOr("L387") { strength.groupValues[2].startsWith(it.key.dropLast(1)) }.value
            uniques += "KM: [${strength.groupValues[1]}]% City Strength against attacks from the [$layer] layer"
        } else uniques += SpecDoc.backticked(effect)
        buildings += jobj(
            "name" to id, "cost" to row["Цена"].toInt(), "maintenance" to row["Содерж."].toInt(),
            "uniques" to uniques, "civilopediaText" to listOf(jobj("text" to "Description: $id")),
        )
    }
    for (row in doc.table("### 7.2.")) {
        val id = SpecDoc.id(row["ID"])
        buildingIds[row["Название"]] = id
        translations.add(id, row["Название"])
        translations.add("Description: $id", row["Описание"])
        val connects = row["Соединяет"]
        val direction = when {
            connects.startsWith("Все три слоя") -> "All"
            connects.contains("Подземье") -> "Down"
            else -> "Up"
        }
        val requires = row["Требует"]
        val uniques = layerCondition(requires.substringBefore(";")).toMutableList()
        val requiredBuildings = requires.substringAfter("в городе ", "").split(" и ").map { it.trim() }.filter { it.isNotEmpty() }
        for (building in requiredBuildings) uniques += "Only available <in cities with a [${buildingId(building)}]>"
        uniques += "KM: Transition [$direction]"
        val extra = row["Доп. эффект"]
        if (extra.contains("+1 ОП")) uniques += "KM: Units changing the layer here gain [1] Movement"
        uniques += SpecDoc.backticked(extra)
        buildings += jobj(
            "name" to id, "faction" to (if (row["Фракция"] == "Технари") "Technocrat" else "Humanist"),
            "cost" to row["Цена"].toInt(), "maintenance" to row["Содерж."].toInt(),
            "uniques" to uniques, "civilopediaText" to listOf(jobj("text" to "Description: $id")),
        )
    }
    writeJson("Buildings.json", buildings)

    // ---------- 7.3 LayerBiomes.json ----------
    val biomes = ArrayList<JObj>()
    val terrainEffectRegex = Regex("""^(.+?):\s*(.+)$""")
    for ((nth, layer) in listOf(0 to "Underground", 1 to "Sky")) {
        for (row in doc.table("### 7.3.", nth)) {
            val id = SpecDoc.id(row["ID"])
            val names = row["Название"].split("/").map { it.trim() }
            translations.add(id, names[0])
            if (names.size > 1) translations.add("$id#Humanist", names[1])
            val whenCell = row.raw[2]
            val surfaceFilter = when {
                whenCell.contains("Центр города") -> "City center"
                whenCell.contains("Fallout") -> "Fallout"
                whenCell.contains("Фабрика") -> "Near city with [${buildingId("Фабрика")}] or [${buildingId("Голем-мастерская")}]"
                else -> whenCell.replace(",", " или").split(" или ").map { surfaceTerrainIds[it.trim()] ?: throw IllegalArgumentException("Unknown '$it'") }
                    .joinToString(" or ")
            }
            val uniques = ArrayList<String>()
            val effect = row["Эффект"].replace('−', '-')
            for (part in effect.split(Regex("""[.;]\s*""")).map { it.trim() }.filter { it.isNotEmpty() }) {
                val match = terrainEffectRegex.find(part)
                val subject = match?.groupValues?.get(1)
                val subjectTerrains = subject?.split(" и ")?.mapNotNull { terrainIds[it.trim()] }.orEmpty()
                val body = if (subjectTerrains.isEmpty()) part else match!!.groupValues[2]
                val stats = if (body.contains("урона")) null
                    else statsUnique(body.replace(Regex("""ход \d+(/\d+)?,?"""), ""))
                fun withTerrains(unique: String) =
                    if (subjectTerrains.isEmpty()) listOf(unique) else subjectTerrains.map { "$unique <in [$it] tiles>" }
                if (stats != null) uniques += withTerrains(stats)
                Regex("""ход (\d+)\b(?!/)""").find(body)?.let { m ->
                    if (!body.contains("проходимым")) uniques += withTerrains("KM: Movement cost [${m.groupValues[1]}]")
                }
                if (body.contains("становится проходимым")) {
                    val target = terrainIds.entries.sortedByDescending { it.key.length }.firstOr("L454") { body.contains(it.key) }.value
                    uniques += "KM: Makes tile passable <in [$target] tiles>"
                }
                if (body.contains("ход 1/2")) uniques += "KM: Flying units spend [50]% movement here"
                Regex("""получает (\d+) урона""").find(body)?.let { uniques += "Units ending their turn on this terrain take [${it.groupValues[1]}] damage" }
            }
            biomes += jobj(
                "name" to id, "layer" to layer, "surfaceFilter" to surfaceFilter,
                "priority" to row["Приоритет"].toInt(),
                "uniques" to uniques.distinct().ifEmpty { null },
            )
        }
    }
    writeJson("LayerBiomes.json", biomes)
    generateLayerArticles(doc)
    generateLayerTileset(doc)
}

/** Russian texts of the engine parts of the three-layer map (KL-xx, 6.1) */
fun GenContext.addLayerEngineTranslations() {
    val t = translations
    t.section("Engine: three-layer map (layers.md)")
    for ((id, russian) in layerIds.entries.map { it.value to it.key }) t.add(id, russian)
    t.add("Underground (tag)", "[Подземье]")
    t.add("Sky (tag)", "[Небо]")
    t.add("Layer: [layer]", "Слой: [layer]")
    t.add("Layer biome", "Биом слоя")
    t.add("Surface: [filter]", "Поверхность: [filter]")
    t.add("Layers", "Слои")
    t.add("Layer", "Слой")
    t.add("Opens with [tech]", "Откроется с [tech]")
    t.add("Ascend", "Подняться")
    t.add("Descend", "Спуститься")
    t.add("Lay route", "Проложить маршрут до…")
    t.add("The Multiverse built the missing floors", "Мультивселенная достроила этажи")
    t.add("The [layer] layer is open to us", "Нам открыт слой: [layer]")
    // 7.3: the notification text of the spec, word for word
    t.add("[layer] changed under [terrain]: now there is [biome]", "[layer] изменился под [terrain]: теперь здесь [biome]")
    t.add("The mine of [cityName] broke through into a cave", "Шахта города [cityName] пробилась в пещеру")
    // 5.3.5: barbarian camps of the layers
    t.add("Goblin Lair", "Логово гоблинов")
    t.add("Harpy Nest", "Гнездо гарпий")
    // 7.1: "Lay route to..."
    t.add("Lay route cancelled!", "Прокладка маршрута отменена!")
    t.add("Lay route completed", "Маршрут проложен")
    // 6.1: keys of the layer switch
    t.add("Show the layer above", "Слой выше")
    t.add("Show the layer below", "Слой ниже")
    // 5.5: map check of the editor
    t.add("Terrain [terrain] belongs to the [layer] layer, not to the [otherLayer] layer!", "Местность [terrain] относится к слою [layer], а не к слою [otherLayer]!")
}

/** [first] with a readable error for the spec tables */
private inline fun <T> Iterable<T>.firstOr(label: String, predicate: (T) -> Boolean): T =
    firstOrNull(predicate) ?: throw IllegalArgumentException("GenLayers: nothing found at $label")

/** 6.1: the articles of the "Layers" section of the Civilopedia (Tutorials.json, category Layers), from the text of the spec */
private fun GenContext.generateLayerArticles(doc: SpecDoc) {
    fun clean(text: String) = text.replace("**", "").replace("`", "").let { SpecDoc.unescape(it) }.trim()
    fun article(name: String, russianName: String, lines: List<String>): JObj {
        translations.add(name, russianName)
        val text = lines.map(::clean).filter { it.isNotEmpty() && !it.startsWith("&#91;") }.mapIndexed { i, line ->
            val key = "Layers article: $name ${i + 1}"
            translations.add(key, line)
            jobj("text" to key)
        }
        return jobj("name" to name, "category" to "Layers", "civilopediaText" to text)
    }
    fun tableLines(heading: String, nth: Int = 0) = doc.table(heading, nth).map { row -> row.raw.filter { it.isNotBlank() }.joinToString(": ") }
    val articles = listOf(
        article("Three-layer map", "Трёхслойная карта", doc.sectionText("## 1. Концепция") + tableLines("### 1.1.")),
        article("Layer transitions", "Переходы между слоями", doc.sectionText("### 4.2.").map { it.substringAfter(". ") } +
            doc.table("### 7.2.").map { "${it["Название"]}: ${it["Соединяет"]} (${it["Требует"]})" }),
        article("Cities on the layers", "Города на слоях", tableLines("### 4.4.")),
        article("Combat between the layers", "Бой между слоями", tableLines("### 4.5.")),
        article("Biomes", "Биомы", doc.sectionText("### 7.3.").filter { !it.startsWith("**Биомы") && !it.startsWith("**Сквозные") }),
    )
    translations.add("Layers", "Слои")
    writeJson("Tutorials.json", articles)
}

/** 7.4: jsons/TileSets/Kotakula.json - fallback to FantasyHex, fog of war per layer, biomes that replace the terrain image */
private fun GenContext.generateLayerTileset(doc: SpecDoc) {
    val fogLine = doc.sectionText("### 7.4.").first { it.contains("fogOfWarColorByLayer") }
    fun color(layerRussian: String): JObj {
        val json = Regex("""$layerRussian `(\{[^`]+})`""").find(fogLine)!!.groupValues[1]
        return JObj((JsonText.parse(json) as Map<*, *>).entries.associate { it.key as String to it.value })
    }
    val variantsLine = doc.sectionText("### 7.4.").first { it.contains("ruleVariants") && it.contains("Cave+Ice Caves") }
    val example = Regex("""`"([^"]+)": \["([^"]+)"]`""").find(variantsLine)!!
    // The spec names one example and the list of replacement images (Cave-Ice, Cave-Sand, Clouds-Mirage, Clouds-Snow)
    val replacements = linkedMapOf(example.groupValues[1] to listOf(example.groupValues[2]),
        "Cave+Sand Caves" to listOf("Cave-Sand"), "Clouds+Mirage Clouds" to listOf("Clouds-Mirage"), "Clouds+Snow Clouds" to listOf("Clouds-Snow"))
    writeJson("TileSets/Kotakula.json", jobj(
        "fallbackTileSet" to "FantasyHex",
        "fogOfWarColorByLayer" to jobj("Underground" to color("Подземье"), "Sky" to color("Небо")),
        "ruleVariants" to JObj(replacements),
    ))
}

/** Icons the three-layer map needs (section 6.4): builders, buildings, improvements, layer luxuries, action buttons, layer icons */
fun layerIconTargets(russian: (String) -> String): List<KotakulaSvgIcons.Target> {
    val doc = SpecDoc(File(LAYERS_PATH))
    val result = ArrayList<KotakulaSvgIcons.Target>()
    fun add(folder: String, id: String) { result += KotakulaSvgIcons.Target(folder, id, russian(id)) }
    for (row in doc.table("### 4.3.")) add("UnitIcons", SpecDoc.id(row["ID"]))
    for (row in doc.table("### 3.6.") + doc.table("### 7.2.")) add("BuildingIcons", SpecDoc.id(row["ID"]))
    for (row in doc.table("### 3.5.")) add("ImprovementIcons", SpecDoc.id(row["ID"]))
    for (row in doc.table("### 7.1.")) SpecDoc.backticked(row["Действие"]).firstOrNull()?.let { add("ImprovementIcons", it) }
    for (id in listOf("Transit Node", "Elemental Axis")) add("ImprovementIcons", id)
    for (row in doc.table("### 3.4.")) if (row["ID"].trim().startsWith("`")) add("ResourceIcons", SpecDoc.id(row["ID"]))
    for (id in listOf("LayerUp", "LayerDown", "LayRoute")) add("UnitActionIcons", id)
    for (id in listOf("Underground", "Surface", "Sky")) add("LayerIcons", id)
    return result
}
