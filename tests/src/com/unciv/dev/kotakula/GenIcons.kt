package com.unciv.dev.kotakula

import java.io.File

/** Section 13.2: vanilla images standing in for Kotakula icons until they are drawn */
private val technocratTechIcons = mapOf(
    "Mechanics" to "Machinery", "Accounting" to "Currency", "Drill" to "Construction", "Shipbuilding" to "Optics",
    "Clockwork" to "Physics", "Corporate Law" to "Civil Service", "Industrial Espionage" to "Education",
    "Cavalry Tactics" to "Military Science", "Geological Survey" to "Archaeology", "Steam Engine" to "Steam Power",
    "Scientific Management" to "Scientific Theory", "Petrochemistry" to "Biology", "Armored Warfare" to "Combustion",
    "Aeronautics" to "Flight", "Mechanized Warfare" to "Mobile Tactics", "Microelectronics" to "Computers",
    "Satellite Recon" to "Satellites", "Quantum Computing" to "Particle Physics", "Multiverse Theory" to "Nuclear Fusion",
)

private val technocratBuildingIcons = mapOf(
    "Arena" to "Colosseum", "Bastion" to "Castle", "Standup Theater" to "Amphitheater", "Business Center" to "Temple",
    "Intelligence Department" to "National Intelligence Agency", "Power Plant" to "Hydro Plant", "Cinema" to "Stadium",
    "Research Institute" to "Research Lab", "Data Center" to "Medical Lab", "Multiverse Component Plant" to "Spaceship Factory",
    "Interuniverse Shipyard" to "Apollo Program",
    // Wonders of section 7.3
    "First Calculator" to "Porcelain Tower", "Crystal Sphere" to "The Oracle", "Wall of Thorns" to "Great Wall",
    "Giant Gear" to "Big Ben", "Eternal Cauldron" to "Hanging Gardens", "Turing Machine" to "Oxford University",
    "Library of Dreams" to "The Louvre", "Kotakula Skyscraper" to "Leaning Tower of Pisa", "Spire of the High Priest" to "Hagia Sophia",
    "Crystal Palace" to "Eiffel Tower", "Aether Gardens" to "Hanging Gardens", "Aether Broadcast Tower" to "CN Tower",
    "Bell of Echoes" to "Notre Dame", "Large Hadron Collider" to "Hubble Space Telescope", "Eye of the Abyss" to "Angkor Wat",
    "World Wide Web" to "Sydney Opera House", "World Tree" to "Cristo Redentor", "Kotakula Office" to "Forbidden Palace",
    "Kotakula Hall" to "Neuschwanstein", "Eternal Teambuilding" to "Utopia Project", "Endless Ball of Muses" to "Utopia Project",
    // Common ones renamed from vanilla
    "Great Lighthouse" to "The Great Lighthouse", "Great Library" to "The Great Library", "Pyramids" to "The Pyramids",
    "Apprentice Tower" to "Library",
)

private val victoryPartIcons = mapOf(
    "Reality Accelerator" to "SS Booster", "Crew Capsule" to "SS Cockpit", "Kotakula Engine" to "SS Engine",
    "Cat Cryochamber" to "SS Stasis Chamber", "Celestial Stair Step" to "SS Booster", "Halo Generator" to "SS Cockpit",
    "Key of the Holy Watchman" to "SS Engine", "Ark of the Righteous" to "SS Stasis Chamber",
)

/** Vanilla policy branch standing in for each archetype of section 8.1 */
private val branchIconSource = mapOf(
    BranchArchetype.Production to "Tradition", BranchArchetype.Growth to "Liberty", BranchArchetype.Military to "Honor",
    BranchArchetype.FaithGold to "Piety", BranchArchetype.Science to "Rationalism", BranchArchetype.Final to "Order",
)

private val actionIcons = mapOf(
    "Quarterly Bonus" to "StatIcons/Production", "Craft Blessing" to "StatIcons/Production",
    "Ad Campaign" to "StatIcons/Culture", "Festival of Muses" to "StatIcons/Culture",
    "Hackathon" to "StatIcons/Science", "Starfall Night" to "StatIcons/Science",
    "Corporate Party" to "StatIcons/Happiness", "Holy Feast" to "StatIcons/Happiness",
    "Mergers and Acquisitions" to "OtherIcons/CityState", "Miracle of Healing" to "StatIcons/Faith",
    "Shareholder Mobilization" to "StatIcons/Strength", "Crusade" to "StatIcons/Strength",
    "IPO Bubble" to "StatIcons/Gold", "Prophet Vision" to "StatIcons/Science",
)

/** Writes jsons/IconPlaceholders.json and PLACEHOLDERS.md (section 13.2) */
@Suppress("UNCHECKED_CAST")
fun GenContext.generateIconPlaceholders(forkRoot: File) {
    val placeholders = LinkedHashMap<String, String>()
    val reasons = LinkedHashMap<String, String>()
    // Icons drawn as SVG (art/kotakula-icons, see KotakulaSvgIcons) need no placeholder
    val drawn = File(forkRoot, "art/kotakula-icons")
    val keepVanilla = File(drawn, "keep-vanilla.txt").takeIf { it.exists() }?.readLines()?.map { it.trim() }?.toSet().orEmpty()
    fun add(name: String, fallback: String, why: String) {
        if (name == fallback) return
        if (File(drawn, "$name.svg").exists() || name in keepVanilla) return
        placeholders[name] = fallback
        reasons[name] = why
    }

    // Units: the prototype of the unit (Humanists: of the mirror)
    val units = JsonText.readArray(File(outDir, "jsons/Units.json")).associateBy { it["name"] as String }
    val technocratRows = spec.table("### 6.4.").associateBy { SpecDoc.id(it["ID"]) }
    for (row in technocratRows.values) {
        val prototype = row["Прототип"].let { if (it == "Bazooka") "Anti-Tank Gun" else it }
        add("UnitIcons/${SpecDoc.id(row["ID"])}", "UnitIcons/$prototype", "прототип $prototype")
    }
    for (row in spec.table("### 6.5.")) {
        val mirror = technocratRows[row["Зеркало"]]!!
        val prototype = mirror["Прототип"].let { if (it == "Bazooka") "Anti-Tank Gun" else it }
        add("UnitIcons/${SpecDoc.id(row["ID"])}", "UnitIcons/$prototype", "прототип зеркала ${row["Зеркало"]}: $prototype")
    }
    add("UnitIcons/Work Boat", "UnitIcons/Work Boats", "ванильная Work Boats")
    for ((part, ss) in victoryPartIcons) add("UnitIcons/$part", "UnitIcons/$ss", "ванильная часть корабля $ss")
    require(units.isNotEmpty())

    // Buildings and wonders: a vanilla building with the same role; Humanist buildings use their Technocrat pair's
    for ((building, vanilla) in technocratBuildingIcons) add("BuildingIcons/$building", "BuildingIcons/$vanilla", "ванильное $vanilla")
    for (row in spec.table("### 7.2.", 0)) {
        val technocrat = SpecDoc.id(row["ID технарей"])
        val target = technocratBuildingIcons[technocrat] ?: technocrat
        add("BuildingIcons/${SpecDoc.id(row["ID гумов"])}", "BuildingIcons/$target", "иконка пары $technocrat")
    }

    // Techs: a vanilla tech of the same era and a similar meaning; Humanist mysteries use their mirror's
    for ((tech, vanilla) in technocratTechIcons) add("TechIcons/$tech", "TechIcons/$vanilla", "ванильная $vanilla")
    for ((technocrat, humanist) in techMirror) {
        val target = technocratTechIcons[technocrat] ?: technocrat
        add("TechIcons/$humanist", "TechIcons/$target", "иконка зеркала $technocrat")
    }

    // Policies: the vanilla policy with the same number in the vanilla branch of the same archetype
    val vanillaBranches = vanilla("Policies.json").associateBy { it["name"] as String }
    for (branch in JsonText.readArray(File(outDir, "jsons/Policies.json"))) {
        val name = branch["name"] as String
        val source = vanillaBranches[branchIconSource[branchArchetypes[name]!!]!!]!!
        add("PolicyBranchIcons/$name", "PolicyBranchIcons/${source["name"]}", "ветка ${source["name"]}")
        val vanillaPolicies = (source["policies"] as List<JObj>).map { it["name"] as String }
        val ours = (branch["policies"] as List<JObj>).map { it["name"] as String }
        for ((index, policy) in ours.withIndex()) {
            if (policy.endsWith(" Complete")) continue
            add("PolicyIcons/$policy", "PolicyIcons/${vanillaPolicies[index]}", "политика ${vanillaPolicies[index]} того же номера")
        }
    }

    // Corporations: vanilla religion symbols in turn
    val vanillaReligions = JsonText.read(File(vanillaDir, "Religions.json")) as List<String>
    val corporations = (JsonText.read(File(outDir, "jsons/Religions.json")) as List<String>).filter { it !in vanillaReligions }
    for ((index, corporation) in corporations.withIndex())
        add("ReligionIcons/$corporation", "ReligionIcons/${vanillaReligions[index % vanillaReligions.size]}", "символ ${vanillaReligions[index % vanillaReligions.size]}")

    for ((action, icon) in actionIcons) add("FactionActionIcons/$action", icon, "иконка стата")

    writeJson("IconPlaceholders.json", placeholders)

    val md = StringBuilder()
    md.append("# Заглушки иконок Kotakula Multiverse\n\n")
    md.append("Раздел 13.2 ТЗ: пока своей иконки нет, движок показывает иконку прототипа. ")
    md.append("Таблица соответствий лежит в `jsons/IconPlaceholders.json` мода и генерируется командой `./gradlew :tests:kotakulaMod`. ")
    md.append("Заглушка действует, только пока в папке `Images` мода нет файла с таким именем: нарисованная иконка заменяет её автоматически.\n\n")
    md.append("Уже нарисованы (P1): иконки маны и корпоративности, 2 иконки фракций, 2 иллюстрации окна выбора, 10 новых ресурсов, 9 новых улучшений, глаз шпионажа.\n\n")
    val svgCount = drawn.walkTopDown().count { it.extension == "svg" }
    md.append("Векторные иконки (P2, P3): $svgCount файлов SVG в `art/kotakula-icons` — все технологии, юниты, здания, чудеса, ветки и политики, корпорации, верования и действия веры, уникальные для мода. ")
    md.append("Они рендерятся в PNG командой `./gradlew :tests:kotakulaSvgIcons`. ")
    md.append("Объекты с ванильным ID, чья ванильная иконка уже совпадает с описанием в разделе 13, перечислены в `art/kotakula-icons/keep-vanilla.txt`.\n\n")
    if (placeholders.isEmpty()) md.append("Заглушек не осталось.\n")
    else {
        md.append("Ниже — только псевдонимы: общие объекты, которые в моде названы иначе, чем в ванили, и показывают ту же самую ванильную иконку.\n\n")
        md.append("| Иконка | Берётся из | Почему |\n| --- | --- | --- |\n")
    }
    for ((name, fallback) in placeholders) md.append("| `$name` | `$fallback` | ${reasons[name]} |\n")
    File(forkRoot, "PLACEHOLDERS.md").writeText(md.toString(), Charsets.UTF_8)
}
