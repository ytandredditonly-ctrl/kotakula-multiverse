package com.unciv.dev.kotakula

import java.io.File

/**
 * Shared state of the Kotakula Multiverse mod generator: the spec, the vanilla ruleset files,
 * name lookups (Russian name in the spec -> English id) and the collected Russian translations.
 */
class GenContext(
    val spec: SpecDoc,
    /** android/assets/jsons/Civ V - Gods & Kings */
    val vanillaDir: File,
    /** The mod folder: .../Kotakula Multiverse */
    val outDir: File,
) {
    val translations = ModTranslations()

    // Russian names of the spec -> ids
    val techIds = LinkedHashMap<String, String>()
    val unitIds = LinkedHashMap<String, String>()
    val buildingIds = LinkedHashMap<String, String>()
    val resourceIds = LinkedHashMap<String, String>()
    val improvementIds = LinkedHashMap<String, String>()
    val policyIds = LinkedHashMap<String, String>()

    /** Technocrat -> Humanist resource mirror (section 6.5) */
    val resourceMirror = linkedMapOf(
        "Horses" to "Horses",
        "Coal" to "Mana Crystals",
        "Oil" to "Moonsilver",
        "Aluminum" to "Dragon Bone",
        "Uranium" to "Stardust",
        "Silicon" to "Titan Blood",
        "Coffee" to "Mandrake",
        "Rare Earths" to "Moonlily",
    )

    /** Technocrat tech id -> mirrored Humanist tech id (same column and row, section 5.5) */
    val techMirror = LinkedHashMap<String, String>()

    fun vanilla(fileName: String) = JsonText.readArray(File(vanillaDir, fileName))

    fun writeJson(fileName: String, value: Any?) {
        val file = File(File(outDir, "jsons"), fileName)
        file.parentFile.mkdirs()
        file.writeText(JsonText.write(value), Charsets.UTF_8)
    }

    fun techId(russianName: String): String {
        val name = SpecDoc.unquote(russianName)
        return techIds[name] ?: throw IllegalArgumentException("Unknown tech '$name'")
    }

    fun unitId(russianName: String): String =
        unitIds[russianName.trim()] ?: throw IllegalArgumentException("Unknown unit '$russianName'")

    fun buildingId(russianName: String): String =
        buildingIds[russianName.trim()] ?: throw IllegalArgumentException("Unknown building '$russianName'")

    fun resourceId(russianName: String): String =
        resourceIds[russianName.trim()] ?: throw IllegalArgumentException("Unknown resource '$russianName'")

    /** "1 Лошади" / "1 Уран, 1 Кремний" -> list of (amount, resource id) */
    fun resources(cell: String): List<Pair<Int, String>> =
        SpecDoc.list(cell).map { part ->
            val amount = part.substringBefore(' ').toInt()
            amount to resourceId(part.substringAfter(' '))
        }

    /** Spec unique texts adjusted to the exact Unciv 4.22.9 wording (section 1.4: same meaning and numbers) */
    fun fixUnique(unique: String): String {
        var u = unique.trim()
        val fixes = listOf<Pair<Regex, String>>(
            Regex("""^May withdraw before melee \(\[(\d+)]%\)$""") to "Withdraws before melee combat <with [$1]% chance>",
            Regex("""^\[(-?\+?\d+)]% maintenance cost for buildings \[(.+)]$""") to "[$1]% maintenance cost for [Buildings] buildings [$2]",
            Regex("""^\[(-?\+?\d+)]% maintenance costs for \[(.+)] units$""") to "[$1]% maintenance costs <for [$2] units>",
            Regex("""^\[(-?\+?\d+)]% tile improvement construction time$""") to "[$1]% construction time for [All] improvements",
            Regex("""^New \[(.+)] units start with \[(\d+)] Experience \[(.+)]$""") to "New [$1] units start with [$2] XP [$3]",
            Regex("""^\[(-?\+?\d+)]% Gold cost of purchasing items in cities \[.+]$""") to "[Gold] cost of purchasing items in cities [$1]%",
            Regex("""^\[1] free \[Technology]$""") to "Free Technology",
            Regex("""^\[(-?\+?\d+)]% City Strength \[in all cities]$""") to "[$1]% Strength for cities",
            Regex("""^\[(.+)] for every \[(\d+)] global followers$""") to "[$1] from every [$2] global followers [in all cities]",
            Regex("""^Free \[(.+)] appears ×2$""") to "[2] free [$1] units appear",
        )
        for ((regex, replacement) in fixes) u = regex.replace(u, replacement)
        return u
    }
}
