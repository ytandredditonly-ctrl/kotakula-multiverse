package com.unciv.dev.kotakula

import com.github.weisj.jsvg.parser.SVGLoader
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Section 13: the vector icons of Kotakula Multiverse.
 *
 * Sources: `art/kotakula-icons/<Folder>/<ID>.svg` in the fork root, one per unique object of the mod (section 13.6-13.9).
 * Authoring convention: black shapes are the silhouette, white shapes painted over them cut details out
 * (grey gives partial transparency). The renderer turns that into the Unciv style of the folder:
 * a white silhouette, or a black one for BuildingIcons, as the vanilla icons are.
 *
 *     ./gradlew :tests:kotakulaSvgIcons      (then :tests:kotakulaAtlas)
 *
 * Writes the PNGs into the mod Images folder and contact sheets into tests/build/kotakula-icons-*.png;
 * lists the objects that still have no SVG.
 */
object KotakulaSvgIcons {
    const val SOURCE_DIR = "../../art/kotakula-icons"

    /** Pixel size of the rendered PNG; vanilla icons are 100 px (policies 50 px) */
    private fun pixelSize(folder: String) = if (folder == "PolicyIcons") 64 else 128

    /** Vanilla construction icons are black and tinted by nothing, everything else is white and tinted by the game */
    private fun silhouetteColor(folder: String) = if (folder == "BuildingIcons" || folder == "ImprovementIcons") Color.BLACK else Color.WHITE

    data class Target(val folder: String, val name: String, val russian: String)

    @JvmStatic
    fun main(args: Array<String>) {
        val modDir = File(KotakulaModGenerator.MOD_PATH)
        val sourceDir = File(SOURCE_DIR)
        val keepVanilla = File(sourceDir, "keep-vanilla.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val targets = targets(modDir).filterNot { "${it.folder}/${it.name}" in keepVanilla }
        val missing = ArrayList<Target>()
        val rendered = ArrayList<Pair<Target, BufferedImage>>()
        for (target in targets) {
            val svg = File(sourceDir, "${target.folder}/${target.name}.svg")
            if (!svg.exists()) { missing += target; continue }
            val image = render(svg, pixelSize(target.folder), silhouetteColor(target.folder))
            val out = File(modDir, "Images/${target.folder}/${target.name}.png")
            out.parentFile.mkdirs()
            ImageIO.write(image, "png", out)
            rendered += target to image
        }
        // These folders only hold rendered SVGs: a PNG without a target any more (renamed or removed object) goes
        // (ImprovementIcons and NationIcons also hold the P1 icons of KotakulaIconPainter: not cleaned)
        for (folder in targets.map { it.folder }.toSet() - setOf("ImprovementIcons", "NationIcons")) {
            val keep = rendered.filter { it.first.folder == folder }.map { "${it.first.name}.png" }.toSet() +
                // the icons of the lore mod are copied by the generator, not rendered
                File(GenLore.LORE_PATH, "Images/$folder").list().orEmpty().toSet()
            File(modDir, "Images/$folder").listFiles { f -> f.extension == "png" && f.name !in keep }?.forEach {
                println("Removed stale ${folder}/${it.name}")
                it.delete()
            }
        }
        writeContactSheets(rendered)
        // Targets that have a vanilla icon of the same ID: a sheet to decide whether the vanilla one already fits the spec
        val vanilla = targets.mapNotNull { target ->
            File("..").listFiles { f -> f.name.startsWith("Images.") }.orEmpty()
                .map { File(it, "${target.folder}/${target.name}.png") }.firstOrNull { it.exists() }
                ?.let { target.copy(folder = "Vanilla${target.folder}") to ImageIO.read(it) }
        }
        writeContactSheets(vanilla)
        println("Kotakula icons: ${rendered.size} rendered, ${missing.size} without SVG of ${targets.size}")
        for (target in missing) println("MISSING ${target.folder}/${target.name} (${target.russian})")
    }

    /** Every object of section 13.6-13.9 that is unique to the mod */
    @Suppress("UNCHECKED_CAST")
    fun targets(modDir: File): List<Target> {
        val jsons = File(modDir, "jsons")
        val russian = readTranslationFile(File(jsons, "translations/Russian.properties"))
        fun ru(name: String) = russian[name] ?: name
        val result = ArrayList<Target>()
        fun factionObjects(file: String, folder: String) {
            for (obj in JsonText.readArray(File(jsons, file)))
                if (obj["faction"] != null) result += Target(folder, obj["name"] as String, ru(obj["name"] as String))
        }
        // Techs.json is a list of columns
        for (column in JsonText.readArray(File(jsons, "Techs.json")))
            for (tech in column["techs"] as List<JObj>)
                if (tech["faction"] != null) result += Target("TechIcons", tech["name"] as String, ru(tech["name"] as String))
        factionObjects("Units.json", "UnitIcons")
        factionObjects("Buildings.json", "BuildingIcons")
        for (branch in JsonText.readArray(File(jsons, "Policies.json"))) {
            if (branch["faction"] == null) continue
            result += Target("PolicyBranchIcons", branch["name"] as String, ru(branch["name"] as String))
            for (policy in branch["policies"] as List<JObj>) {
                val name = policy["name"] as String
                if (!name.endsWith(" Complete")) result += Target("PolicyIcons", name, ru(name))
            }
        }
        val vanillaReligions = JsonText.read(File("jsons/Civ V - Gods & Kings/Religions.json")) as List<String>
        for (religion in JsonText.read(File(jsons, "Religions.json")) as List<String>)
            if (religion !in vanillaReligions) result += Target("ReligionIcons", religion, ru(religion))
        // Unciv shows beliefs with ReligionIcons/<belief name> (ImageGetter.getReligionPortrait)
        factionObjects("Beliefs.json", "ReligionIcons")
        for (action in JsonText.readArray(File(jsons, "FactionActions.json")))
            result += Target("FactionActionIcons", action["name"] as String, ru(action["name"] as String))
        // The nations of the lore mod and everything unique to them (GenLore)
        val lore = File(GenLore.LORE_PATH, "jsons")
        for ((file, folder) in listOf("Nations.json" to "NationIcons", "Units.json" to "UnitIcons",
                "Buildings.json" to "BuildingIcons", "TileImprovements.json" to "ImprovementIcons"))
            for (obj in JsonText.readArray(File(lore, file)))
                result += Target(folder, obj["name"] as String, ru(obj["name"] as String))
        return result
    }

    fun render(svg: File, size: Int, color: Color): BufferedImage {
        val document = SVGLoader().load(svg.toURI().toURL()) ?: error("Cannot parse $svg")
        val raw = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = raw.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val docSize = document.size()
        g.scale(size / docSize.width.toDouble(), size / docSize.height.toDouble())
        document.render(null, g)
        g.dispose()
        // Black = silhouette, white = cut out: alpha * darkness, in the colour of the folder
        val result = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val rgb = color.rgb and 0xFFFFFF
        for (y in 0 until size) for (x in 0 until size) {
            val p = raw.getRGB(x, y)
            val alpha = p ushr 24
            if (alpha == 0) continue
            val luminance = (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000
            val out = alpha * (255 - luminance) / 255
            if (out > 0) result.setRGB(x, y, (out shl 24) or rgb)
        }
        return result
    }

    /** One sheet per folder: icons at 96 px on the background the game puts them on, with their IDs */
    private fun writeContactSheets(rendered: List<Pair<Target, BufferedImage>>) {
        val out = File("../../tests/build").apply { mkdirs() }
        for ((folder, items) in rendered.groupBy { it.first.folder }) {
            val cell = 120
            val columns = 10
            val rows = (items.size + columns - 1) / columns
            val sheet = BufferedImage(columns * cell, rows * cell, BufferedImage.TYPE_INT_ARGB)
            val g = sheet.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.color = Color(40, 44, 52)
            g.fillRect(0, 0, sheet.width, sheet.height)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
            for ((index, item) in items.withIndex()) {
                val x = (index % columns) * cell
                val y = (index / columns) * cell
                g.color = if (folder == "BuildingIcons" || folder == "ImprovementIcons") Color(225, 225, 225) else Color(70, 110, 170)
                g.fillOval(x + 10, y + 4, 100, 100)
                g.drawImage(item.second, x + 22, y + 16, 76, 76, null)
                g.color = Color.WHITE
                val label = item.first.name.take(20)
                g.drawString(label, x + (cell - g.fontMetrics.stringWidth(label)) / 2, y + 116)
            }
            g.dispose()
            ImageIO.write(sheet, "png", File(out, "kotakula-icons-$folder.png"))
        }
    }
}
