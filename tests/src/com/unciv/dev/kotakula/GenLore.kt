package com.unciv.dev.kotakula

import java.io.File

/**
 * The three nations of the Kotakula lore mod (lore/Kotakula lore mod, from github.com/ytandredditonly-ctrl/Kotakula-lore-mod),
 * merged into the base ruleset. Units, buildings, improvement, promotion and unit type are kept as they are; only
 * what points to the Gods & Kings ruleset is moved onto the Kotakula one:
 * - techs missing in the Kotakula tree take the common tech of the same age ([GenLoreMaps.techOf]): Construction
 *   (Classical in G&K) has no common counterpart, Iron Working is the last common tech, so both factions get the unit;
 * - a unit replacing a common unit takes its upgrade line (upgradesTo, obsoleteTech) from the unit it replaces;
 * - G&K unit types become the section 6.1 types ([specUnitTypeOf]), the Castle is the Bastion (and the Rune Wall replacing it);
 * - texts written in Russian in the jsons move to Russian.properties under English keys (spec 0.4), so the faction
 *   wording of section 4 applies to them too.
 */
object GenLore {
    const val LORE_PATH = "../../lore/Kotakula lore mod"
}

@Suppress("UNCHECKED_CAST")
fun GenContext.mergeLoreMod() {
    val lore = File(GenLore.LORE_PATH)
    require(lore.isDirectory) { "No lore mod at ${lore.absolutePath}" }
    val loreJsons = File(lore, "jsons")
    fun loreArray(name: String) = JsonText.readArray(File(loreJsons, name))
    fun modArray(name: String) = JsonText.readArray(File(outDir, "jsons/$name"))
    translations.section("Kotakula lore mod: Vedor, Hun-Pyr-na-Chun-Chunke, Hulimansish")
    for ((key, value) in readTranslationFile(File(loreJsons, "translations/Russian.properties")))
        translations.add(key, value)

    val techs = modArray("Techs.json").flatMap { (it["techs"] as List<JObj>).map { tech -> tech["name"] as String } }.toSet()
    fun tech(name: Any?): String? {
        if (name == null) return null
        val mapped = GenLoreMaps.techOf(name as String)
        require(mapped in techs) { "Lore tech $name has no Kotakula counterpart" }
        return mapped
    }
    fun renameBuildings(unique: String) = GenLoreMaps.buildingOf.entries.fold(unique) { acc, (from, to) -> acc.replace("[$from]", "[$to]") }
    /** Russian civilopedia lines -> "Description: X" keys */
    fun civilopedia(id: String, obj: JObj) {
        val lines = obj["civilopediaText"] as List<JObj>? ?: return
        obj["civilopediaText"] = lines.mapIndexed { index, line ->
            val key = if (index == 0) "Description: $id" else "Description ${index + 1}: $id"
            translations.add(key, line["text"] as String)
            jobj("text" to key)
        }
    }

    // Units
    val units = modArray("Units.json")
    val unitsByName = units.associateBy { it["name"] as String }
    for (unit in loreArray("Units.json")) {
        val name = unit["name"] as String
        unit["unitType"] = specUnitTypeOf[unit["unitType"]] ?: unit["unitType"]
        unit["requiredTech"] = tech(unit["requiredTech"])
        val replaced = unit["replaces"]?.let { unitsByName[it as String] ?: error("Lore $name replaces unknown ${unit["replaces"]}") }
        if (replaced != null && unit["upgradesTo"] != null) {
            // The upgrade line of the Kotakula tree, not the G&K one
            unit["upgradesTo"] = replaced["upgradesTo"]
            unit["obsoleteTech"] = replaced["obsoleteTech"]
        } else unit["obsoleteTech"] = tech(unit["obsoleteTech"])
        unit["uniques"] = (unit["uniques"] as List<String>?)?.map { renameUnitTypesInUnique(renameBuildings(it)) }
        civilopedia(name, unit)
        units += unit
    }
    writeJson("Units.json", units)

    // Buildings and the improvement
    val buildings = modArray("Buildings.json")
    for (building in loreArray("Buildings.json")) {
        building["requiredTech"] = tech(building["requiredTech"])
        building["requiredBuilding"] = (building["requiredBuilding"] as String?)?.let { GenLoreMaps.buildingOf[it] ?: it }
        building["uniques"] = (building["uniques"] as List<String>?)?.map { renameBuildings(it) }
        buildings += building
    }
    writeJson("Buildings.json", buildings)
    writeJson("TileImprovements.json", modArray("TileImprovements.json") + loreArray("TileImprovements.json"))

    // Promotion and unit type. The lore mod left its Undead (Kiwi) without promotions; after the promotion bug
    // report of 1.0.2 it gets the melee ones like every other unit of the mod
    val promotions = modArray("UnitPromotions.json").onEach { promotion ->
        val types = promotion["unitTypes"] as List<String>? ?: return@onEach
        if ("Melee" in types) promotion["unitTypes"] = types + "Undead"
    }
    writeJson("UnitPromotions.json", promotions + loreArray("UnitPromotions.json"))
    writeJson("UnitTypes.json", modArray("UnitTypes.json") + loreArray("UnitTypes.json"))

    // Nations: Russian texts of the json -> translation keys
    val nations = modArray("Nations.json")
    for (nation in loreArray("Nations.json")) {
        val name = nation["name"] as String
        val leader = nation["leaderName"] as String
        val leaderId = GenLoreMaps.leaderIds[leader] ?: leader
        if (leaderId != leader) translations.add(leaderId, leader)
        nation["leaderName"] = leaderId
        for (field in GenLoreMaps.nationTextFields) {
            val text = nation[field] as String? ?: continue
            val key = "$name: $field"
            translations.add(key, text)
            nation[field] = key
        }
        nation["uniques"] = (nation["uniques"] as List<String>).map { renameBuildings(it) }
        nations += nation
    }
    writeJson("Nations.json", nations)

    // Images: the lore icons go to the mod as they are (they are packed into the mod atlas with the others)
    val images = File(lore, "Images")
    // ...unless the object has a vector icon in art/kotakula-icons (rendered by KotakulaSvgIcons instead)
    images.walkTopDown().filter { it.isFile && it.extension == "png" }
        .filterNot { File(KotakulaSvgIcons.SOURCE_DIR, it.relativeTo(images).invariantSeparatorsPath.removeSuffix(".png") + ".svg").exists() }
        .forEach { png ->
        png.copyTo(File(outDir, "Images/${png.relativeTo(images).invariantSeparatorsPath}"), overwrite = true)
    }
}

/** The maps of [GenLore], reachable from the extension function */
internal object GenLoreMaps {
    fun techOf(name: String) = mapOf("Construction" to "Iron Working")[name] ?: name
    val buildingOf = mapOf("Castle" to "Bastion")
    val nationTextFields = listOf("startIntroPart1", "startIntroPart2", "declaringWar", "attacked", "defeated",
        "introduction", "neutralHello", "hateHello", "tradeRequest")
    val leaderIds = mapOf("Мистер Никитошка" to "Mister Nikitoshka", "Метермин" to "Metermin")
}
