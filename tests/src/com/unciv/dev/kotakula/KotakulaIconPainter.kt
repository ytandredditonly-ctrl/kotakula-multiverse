package com.unciv.dev.kotakula

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the P1 icons of section 13.3-13.5: white silhouettes on transparency (256x256, object ~200x200, details as cut-outs),
 * plus the two colored 512x512 illustrations of the faction choice window. Run from android/assets:
 *
 *     ./gradlew :tests:kotakulaIcons
 */
object KotakulaIconPainter {
    private const val SIZE = 256
    private lateinit var imagesDir: File

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("java.awt.headless", "true")
        imagesDir = File(args.getOrElse(0) { KotakulaModGenerator.MOD_PATH }, "Images")
        // P1: stats and factions (13.4)
        icon("StatIcons/Science#Humanist", ::manaDrop)
        icon("StatIcons/Faith#Technocrat", ::corporateBriefcase)
        icon("FactionIcons/Technocrat", ::gearWithCalculator)
        icon("FactionIcons/Humanist", ::staffWithCrystal)
        illustration("Illustrations/ChooseTechnocrat", ::technocratIllustration)
        illustration("Illustrations/ChooseHumanist", ::humanistIllustration)
        // P1: resources (13.5)
        icon("ResourceIcons/Silicon", ::silicon)
        icon("ResourceIcons/Coffee", ::coffee)
        icon("ResourceIcons/Rare Earths", ::rareEarths)
        icon("ResourceIcons/Mana Crystals", ::manaCrystals)
        icon("ResourceIcons/Moonsilver", ::moonsilver)
        icon("ResourceIcons/Dragon Bone", ::dragonBone)
        icon("ResourceIcons/Stardust", ::stardust)
        icon("ResourceIcons/Titan Blood", ::titanBlood)
        icon("ResourceIcons/Mandrake", ::mandrake)
        icon("ResourceIcons/Moonlily", ::moonlily)
        // P1: improvements (13.5)
        icon("ImprovementIcons/Silicon Quarry", ::siliconQuarry)
        icon("ImprovementIcons/Ley Line", ::leyLine)
        icon("ImprovementIcons/Crystal Mine", ::crystalMine)
        icon("ImprovementIcons/Moon Well", ::moonWell)
        icon("ImprovementIcons/Moon Well (Water)", ::moonWellWater)
        icon("ImprovementIcons/Ossuary", ::ossuary)
        icon("ImprovementIcons/Star Catcher", ::starCatcher)
        icon("ImprovementIcons/Titan Altar", ::titanAltar)
        icon("ImprovementIcons/Alchemist Garden", ::alchemistGarden)
        // Engine: espionage marker (KC-13)
        icon("OtherIcons/SpyEye", ::spyEye)
        writePreview()
        println("Icons written to ${imagesDir.absolutePath}")
    }

    /** Contact sheet of all icons on a dark background, for reviewing: build/kotakula-icons-preview.png */
    private fun writePreview() {
        val files = imagesDir.walkTopDown().filter { it.isFile && it.extension == "png" && !it.path.contains("Illustrations") }.toList()
        val columns = 6
        val cell = 140
        val sheet = BufferedImage(columns * cell, ((files.size + columns - 1) / columns) * cell, BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        g.color = Color(60, 70, 90); g.fillRect(0, 0, sheet.width, sheet.height)
        for ((i, file) in files.withIndex()) {
            val x = (i % columns) * cell; val y = (i / columns) * cell
            g.drawImage(ImageIO.read(file), x + 10, y + 4, 120, 120, null)
            g.color = Color.YELLOW; g.drawString(file.nameWithoutExtension.take(22), x + 4, y + cell - 4)
        }
        g.dispose()
        ImageIO.write(sheet, "png", File("../../build/kotakula-icons-preview.png").apply { parentFile.mkdirs() })
    }

    //region Canvas helpers

    private fun icon(name: String, paint: (Area) -> Unit) {
        val shape = Area()
        paint(shape)
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.color = Color.WHITE
        g.fill(shape)
        g.dispose()
        save(name, image)
    }

    private fun illustration(name: String, paint: (Graphics2D) -> Unit) {
        val image = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        paint(g)
        g.dispose()
        save(name, image)
    }

    private fun save(name: String, image: BufferedImage) {
        val file = File(imagesDir, "$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double = rx) = Ellipse2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry)
    private fun rect(x: Double, y: Double, w: Double, h: Double) = Rectangle2D.Double(x, y, w, h)
    private fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double) = RoundRectangle2D.Double(x, y, w, h, r, r)
    private fun stroke(shape: Shape, width: Double) = BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(shape)
    private fun polygon(vararg points: Double): Path2D.Double {
        val path = Path2D.Double()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) { path.lineTo(points[i], points[i + 1]); i += 2 }
        path.closePath()
        return path
    }
    private fun polyline(vararg points: Double): Path2D.Double {
        val path = Path2D.Double()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) { path.lineTo(points[i], points[i + 1]); i += 2 }
        return path
    }
    private fun rotated(shape: Shape, degrees: Double, cx: Double, cy: Double): Shape =
        AffineTransform.getRotateInstance(Math.toRadians(degrees), cx, cy).createTransformedShape(shape)

    private fun star(cx: Double, cy: Double, outer: Double, inner: Double, points: Int = 5, rotation: Double = -PI / 2): Path2D.Double {
        val path = Path2D.Double()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = rotation + i * PI / points
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.closePath()
        return path
    }

    /** A teardrop pointing up */
    private fun drop(cx: Double, cy: Double, r: Double): Path2D.Double {
        val path = Path2D.Double()
        path.moveTo(cx, cy - r * 1.9)
        path.curveTo(cx + r * 0.35, cy - r * 1.2, cx + r, cy - r * 0.6, cx + r, cy)
        path.curveTo(cx + r, cy + r * 0.56, cx + r * 0.55, cy + r, cx, cy + r)
        path.curveTo(cx - r * 0.55, cy + r, cx - r, cy + r * 0.56, cx - r, cy)
        path.curveTo(cx - r, cy - r * 0.6, cx - r * 0.35, cy - r * 1.2, cx, cy - r * 1.9)
        path.closePath()
        return path
    }

    private fun crescent(cx: Double, cy: Double, r: Double, offset: Double): Area {
        val moon = Area(ellipse(cx, cy, r))
        moon.subtract(Area(ellipse(cx + offset, cy - offset * 0.4, r * 0.9)))
        return moon
    }

    private fun crystal(cx: Double, bottom: Double, width: Double, height: Double): Path2D.Double =
        polygon(cx - width / 2, bottom, cx - width / 2, bottom - height * 0.7, cx, bottom - height,
            cx + width / 2, bottom - height * 0.7, cx + width / 2, bottom)

    //endregion
    //region Stats and factions (13.4)

    /** Mana: a drop with a spark inside */
    private fun manaDrop(a: Area) {
        a.add(Area(drop(128.0, 150.0, 70.0)))
        a.subtract(Area(star(128.0, 150.0, 40.0, 12.0, 4, 0.0)))
    }

    /** Corporate spirit: a briefcase with a growth chart on its side */
    private fun corporateBriefcase(a: Area) {
        a.add(Area(roundRect(28.0, 78.0, 200.0, 140.0, 26.0)))
        a.add(Area(stroke(roundRect(88.0, 44.0, 80.0, 50.0, 18.0), 16.0)))
        a.subtract(Area(stroke(polyline(56.0, 188.0, 98.0, 150.0, 128.0, 168.0, 186.0, 108.0), 14.0)))
        a.subtract(Area(polygon(170.0, 100.0, 200.0, 96.0, 194.0, 126.0)))
    }

    /** Technocrats: a gear with a calculator in its middle */
    private fun gearWithCalculator(a: Area) {
        a.add(Area(ellipse(128.0, 128.0, 82.0)))
        for (i in 0 until 10) {
            val tooth = rotated(rect(116.0, 22.0, 24.0, 40.0), i * 36.0, 128.0, 128.0)
            a.add(Area(tooth))
        }
        val screen = roundRect(88.0, 80.0, 80.0, 100.0, 12.0)
        a.subtract(Area(screen))
        a.add(Area(rect(98.0, 90.0, 60.0, 20.0)))
        for (row in 0 until 3) for (column in 0 until 3)
            a.add(Area(roundRect(98.0 + column * 22, 118.0 + row * 20, 16.0, 14.0, 4.0)))
    }

    /** Humanists: a staff with a crystal and a feather */
    private fun staffWithCrystal(a: Area) {
        a.add(Area(stroke(polyline(70.0, 232.0, 150.0, 70.0), 18.0)))
        val gem = polygon(160.0, 18.0, 196.0, 52.0, 162.0, 104.0, 128.0, 52.0)
        a.add(Area(gem))
        a.subtract(Area(polygon(160.0, 34.0, 178.0, 52.0, 161.0, 80.0)))
        val feather = GeneralPath()
        feather.moveTo(150.0, 120.0)
        feather.curveTo(220.0, 120.0, 236.0, 190.0, 208.0, 228.0)
        feather.curveTo(180.0, 196.0, 150.0, 170.0, 150.0, 120.0)
        feather.closePath()
        a.add(Area(feather))
        a.subtract(Area(stroke(polyline(156.0, 132.0, 204.0, 218.0), 5.0)))
    }

    private fun technocratIllustration(g: Graphics2D) {
        g.paint = GradientPaint(0f, 0f, Color(20, 34, 60), 0f, 512f, Color(40, 80, 130))
        g.fillRect(0, 0, 512, 512)
        // Blueprint grid
        g.color = Color(120, 170, 230, 60)
        g.stroke = BasicStroke(1.5f)
        for (i in 0..512 step 32) { g.drawLine(i, 0, i, 512); g.drawLine(0, i, 512, i) }
        g.color = Color(170, 210, 255, 120)
        g.stroke = BasicStroke(3f)
        g.drawOval(330, 40, 120, 120); g.drawLine(330, 100, 450, 100); g.drawLine(390, 40, 390, 160)
        // Server racks
        for ((x, h) in listOf(20 to 300, 110 to 340, 410 to 320)) {
            g.color = Color(30, 30, 40)
            g.fillRoundRect(x, 512 - h, 80, h, 10, 10)
            for (y in 512 - h + 20 until 500 step 28) {
                g.color = Color(60, 60, 75); g.fillRect(x + 8, y, 64, 18)
                g.color = if ((x + y) % 3 == 0) Color(90, 255, 140) else Color(80, 180, 255)
                g.fillOval(x + 56, y + 5, 8, 8)
            }
        }
        // Technocrat: body, head, glasses, coffee
        g.color = Color(58, 110, 165)
        g.fillRoundRect(186, 300, 150, 212, 60, 60)
        g.color = Color(240, 200, 170)
        g.fillOval(206, 170, 110, 130)
        g.color = Color(60, 40, 30)
        g.fillArc(200, 160, 122, 80, 0, 180)
        g.color = Color.BLACK
        g.stroke = BasicStroke(5f)
        g.drawOval(222, 220, 34, 26); g.drawOval(266, 220, 34, 26); g.drawLine(256, 232, 266, 232)
        g.drawArc(240, 260, 40, 20, 200, 140)
        g.color = Color.WHITE
        g.fillRoundRect(330, 360, 60, 70, 12, 12)
        g.stroke = BasicStroke(8f); g.drawArc(372, 375, 34, 34, -90, 180)
        g.color = Color(110, 70, 40); g.fillRect(336, 366, 48, 12)
        g.color = Color(255, 255, 255, 150); g.stroke = BasicStroke(4f)
        for (i in 0..2) g.drawArc(338 + i * 16, 310, 14, 40, 0, 180)
    }

    private fun humanistIllustration(g: Graphics2D) {
        g.paint = GradientPaint(0f, 0f, Color(14, 10, 40), 0f, 512f, Color(80, 50, 140))
        g.fillRect(0, 0, 512, 512)
        val random = java.util.Random(7)
        for (i in 0 until 120) {
            val s = 1 + random.nextInt(3)
            g.color = Color(255, 255, 220, 120 + random.nextInt(135))
            g.fillOval(random.nextInt(512), random.nextInt(330), s, s)
        }
        g.color = Color(255, 245, 200)
        g.fill(crescent(420.0, 90.0, 50.0, 24.0))
        // Robe, hat, face, beard
        g.color = Color(123, 75, 183)
        g.fill(polygon(256.0, 260.0, 380.0, 512.0, 132.0, 512.0))
        g.color = Color(240, 205, 175)
        g.fillOval(214, 196, 84, 90)
        g.color = Color(230, 230, 240)
        g.fill(polygon(222.0, 262.0, 290.0, 262.0, 256.0, 340.0))
        g.color = Color(90, 50, 150)
        g.fill(polygon(256.0, 40.0, 320.0, 214.0, 192.0, 214.0))
        g.fillOval(176, 198, 160, 30)
        g.color = Color(255, 230, 120)
        g.fill(star(262.0, 130.0, 18.0, 7.0))
        // Book of poems
        g.color = Color(170, 60, 60)
        g.fillRoundRect(190, 360, 132, 90, 10, 10)
        g.color = Color(250, 240, 220)
        g.fillRect(200, 368, 54, 74); g.fillRect(258, 368, 54, 74)
        g.color = Color(120, 100, 90); g.stroke = BasicStroke(2f)
        for (y in 380..430 step 12) { g.drawLine(206, y, 248, y); g.drawLine(264, y, 306, y) }
        // Staff
        g.color = Color(130, 90, 50); g.stroke = BasicStroke(10f)
        g.drawLine(110, 500, 150, 250)
        g.color = Color(130, 220, 255)
        g.fill(polygon(152.0, 200.0, 172.0, 236.0, 150.0, 262.0, 130.0, 236.0))
    }

    //endregion
    //region Resources (13.5)

    private fun silicon(a: Area) {
        a.add(Area(polygon(128.0, 24.0, 210.0, 70.0, 210.0, 170.0, 128.0, 232.0, 46.0, 170.0, 46.0, 70.0)))
        val traces = Area()
        traces.add(Area(stroke(polyline(88.0, 100.0, 128.0, 100.0, 128.0, 150.0, 168.0, 150.0), 10.0)))
        traces.add(Area(stroke(polyline(88.0, 170.0, 108.0, 170.0, 108.0, 125.0), 10.0)))
        traces.add(Area(ellipse(88.0, 100.0, 11.0))); traces.add(Area(ellipse(168.0, 150.0, 11.0))); traces.add(Area(ellipse(88.0, 170.0, 11.0)))
        a.subtract(traces)
    }

    private fun coffee(a: Area) {
        a.add(Area(roundRect(50.0, 110.0, 120.0, 120.0, 30.0)))
        a.add(Area(stroke(ellipse(178.0, 160.0, 26.0), 16.0)))
        a.add(Area(roundRect(30.0, 222.0, 170.0, 16.0, 16.0)))
        for (i in 0..2) {
            val steam = GeneralPath()
            val x = 76.0 + i * 34
            steam.moveTo(x, 96.0); steam.curveTo(x - 18, 70.0, x + 18, 54.0, x, 26.0)
            a.add(Area(stroke(steam, 10.0)))
        }
    }

    private fun rareEarths(a: Area) {
        for ((x, y, r) in listOf(Triple(90.0, 170.0, 44.0), Triple(166.0, 176.0, 38.0), Triple(128.0, 110.0, 40.0), Triple(196.0, 116.0, 26.0), Triple(60.0, 112.0, 24.0)))
            a.add(Area(ellipse(x, y, r, r * 0.8)))
        for ((x, y) in listOf(80.0 to 160.0, 120.0 to 98.0, 160.0 to 166.0)) a.subtract(Area(ellipse(x, y, 7.0)))
    }

    private fun manaCrystals(a: Area) {
        a.add(Area(crystal(128.0, 228.0, 60.0, 200.0)))
        a.add(Area(rotated(crystal(78.0, 228.0, 44.0, 140.0), -18.0, 78.0, 228.0)))
        a.add(Area(rotated(crystal(180.0, 228.0, 44.0, 150.0), 18.0, 180.0, 228.0)))
        a.subtract(Area(stroke(polyline(128.0, 60.0, 128.0, 200.0), 6.0)))
        a.subtract(Area(star(196.0, 50.0, 22.0, 6.0, 4, 0.0)))
        a.add(Area(star(196.0, 50.0, 16.0, 4.0, 4, 0.0)))
    }

    private fun moonsilver(a: Area) {
        a.add(Area(drop(128.0, 150.0, 74.0)))
        a.subtract(crescent(128.0, 156.0, 40.0, 18.0))
    }

    private fun dragonBone(a: Area) {
        val bone = GeneralPath()
        bone.moveTo(50.0, 200.0); bone.curveTo(80.0, 110.0, 150.0, 70.0, 206.0, 60.0)
        a.add(Area(stroke(bone, 30.0)))
        a.add(Area(ellipse(42.0, 196.0, 22.0))); a.add(Area(ellipse(56.0, 220.0, 22.0)))
        val claw = GeneralPath()
        claw.moveTo(196.0, 40.0); claw.curveTo(240.0, 50.0, 246.0, 100.0, 222.0, 128.0)
        claw.curveTo(224.0, 98.0, 214.0, 74.0, 190.0, 72.0); claw.closePath()
        a.add(Area(claw))
    }

    private fun stardust(a: Area) {
        val pouch = GeneralPath()
        pouch.moveTo(90.0, 110.0); pouch.curveTo(30.0, 140.0, 40.0, 236.0, 128.0, 236.0)
        pouch.curveTo(216.0, 236.0, 226.0, 140.0, 166.0, 110.0); pouch.closePath()
        a.add(Area(pouch))
        a.add(Area(roundRect(86.0, 92.0, 84.0, 22.0, 10.0)))
        a.subtract(Area(star(128.0, 180.0, 30.0, 12.0)))
        a.add(Area(star(100.0, 52.0, 18.0, 7.0))); a.add(Area(star(150.0, 30.0, 14.0, 5.0))); a.add(Area(star(186.0, 66.0, 16.0, 6.0)))
    }

    private fun titanBlood(a: Area) {
        a.add(Area(roundRect(106.0, 22.0, 44.0, 70.0, 12.0)))
        a.add(Area(ellipse(128.0, 166.0, 76.0)))
        a.subtract(Area(drop(128.0, 180.0, 34.0)))
        a.add(Area(drop(128.0, 186.0, 22.0)))
    }

    private fun mandrake(a: Area) {
        val root = GeneralPath()
        root.moveTo(80.0, 90.0); root.curveTo(60.0, 160.0, 110.0, 240.0, 128.0, 244.0)
        root.curveTo(146.0, 240.0, 196.0, 160.0, 176.0, 90.0); root.closePath()
        a.add(Area(root))
        for (angle in listOf(-40.0, 0.0, 40.0)) a.add(Area(rotated(ellipse(128.0, 50.0, 16.0, 42.0), angle, 128.0, 92.0)))
        a.subtract(Area(ellipse(108.0, 136.0, 9.0))); a.subtract(Area(ellipse(148.0, 136.0, 9.0)))
        a.subtract(Area(ellipse(128.0, 176.0, 16.0, 22.0)))
    }

    private fun moonlily(a: Area) {
        a.add(Area(ellipse(128.0, 190.0, 100.0, 34.0)))
        a.subtract(Area(polygon(128.0, 190.0, 228.0, 170.0, 228.0, 210.0)))
        for (angle in listOf(-50.0, -25.0, 0.0, 25.0, 50.0)) a.add(Area(rotated(ellipse(128.0, 110.0, 20.0, 56.0), angle, 128.0, 166.0)))
        a.subtract(Area(crescent(186.0, 60.0, 26.0, 12.0)))
        a.add(Area(crescent(186.0, 60.0, 20.0, 9.0)))
    }

    //endregion
    //region Improvements (13.5)

    private fun siliconQuarry(a: Area) {
        a.add(Area(stroke(polyline(30.0, 60.0, 120.0, 40.0, 150.0, 110.0), 16.0)))
        a.add(Area(polygon(120.0, 100.0, 200.0, 100.0, 214.0, 160.0, 134.0, 172.0)))
        for (i in 0..3) a.add(Area(polygon(140.0 + i * 20, 168.0, 150.0 + i * 20, 168.0, 146.0 + i * 20, 186.0)))
        a.add(Area(polygon(70.0, 236.0, 70.0, 196.0, 92.0, 168.0, 114.0, 196.0, 114.0, 236.0)))
    }

    private fun leyLine(a: Area) {
        val line = GeneralPath()
        line.moveTo(20.0, 200.0); line.curveTo(90.0, 120.0, 160.0, 220.0, 236.0, 90.0)
        a.add(Area(stroke(line, 20.0)))
        for ((x, y) in listOf(64.0 to 120.0, 132.0 to 110.0, 196.0 to 190.0)) {
            a.add(Area(roundRect(x - 18, y - 24, 36.0, 48.0, 10.0)))
            a.subtract(Area(stroke(polyline(x - 6, y - 14, x - 6, y + 14, x + 8, y - 2), 5.0)))
        }
    }

    private fun crystalMine(a: Area) {
        val arch = Area(rect(40.0, 110.0, 176.0, 126.0))
        arch.add(Area(ellipse(128.0, 110.0, 88.0, 70.0)))
        a.add(arch)
        a.subtract(Area(rect(80.0, 130.0, 96.0, 106.0)))
        a.subtract(Area(ellipse(128.0, 130.0, 48.0, 40.0)))
        a.add(Area(crystal(110.0, 236.0, 24.0, 80.0)))
        a.add(Area(crystal(146.0, 236.0, 28.0, 100.0)))
    }

    private fun well(a: Area, top: Double) {
        a.add(Area(rect(70.0, top + 40, 116.0, 70.0)))
        a.subtract(Area(rect(84.0, top + 52, 88.0, 14.0)))
        a.add(Area(stroke(polyline(80.0, top + 40, 80.0, top - 10, 176.0, top - 10, 176.0, top + 40), 12.0)))
        a.add(Area(polygon(60.0, top - 10, 128.0, top - 46, 196.0, top - 10)))
    }

    private fun moonWell(a: Area) {
        well(a, 130.0)
        a.add(crescent(128.0, 44.0, 34.0, 14.0))
    }

    private fun moonWellWater(a: Area) {
        well(a, 104.0)
        a.add(Area(roundRect(40.0, 214.0, 176.0, 22.0, 8.0)))
        for (i in 0..4) a.subtract(Area(rect(52.0 + i * 34, 214.0, 4.0, 22.0)))
        a.add(Area(stroke(polyline(20.0, 246.0, 60.0, 238.0, 100.0, 246.0, 140.0, 238.0, 180.0, 246.0, 236.0, 238.0), 6.0)))
        a.add(crescent(206.0, 36.0, 26.0, 11.0))
    }

    private fun ossuary(a: Area) {
        val arch = GeneralPath()
        arch.moveTo(50.0, 236.0); arch.lineTo(50.0, 120.0); arch.curveTo(50.0, 30.0, 206.0, 30.0, 206.0, 120.0); arch.lineTo(206.0, 236.0)
        a.add(Area(stroke(arch, 34.0)))
        for (i in 0..6) {
            val angle = PI + i * PI / 6
            a.subtract(Area(stroke(polyline(128 + 78 * cos(angle), 120 + 78 * sin(angle), 128 + 100 * cos(angle), 120 + 100 * sin(angle)), 4.0)))
        }
        a.add(Area(ellipse(128.0, 200.0, 28.0, 30.0)))
        a.subtract(Area(ellipse(118.0, 196.0, 7.0))); a.subtract(Area(ellipse(138.0, 196.0, 7.0)))
    }

    private fun starCatcher(a: Area) {
        a.add(Area(stroke(polyline(40.0, 236.0, 120.0, 140.0), 14.0)))
        val net = Area(ellipse(160.0, 100.0, 66.0))
        net.subtract(Area(ellipse(160.0, 100.0, 54.0)))
        a.add(net)
        for (i in -2..2) {
            a.add(Area(stroke(polyline(160.0 + i * 20, 46.0, 160.0 + i * 20, 154.0), 4.0)))
            a.add(Area(stroke(polyline(106.0, 100.0 + i * 20, 214.0, 100.0 + i * 20), 4.0)))
        }
        a.intersect(Area(rect(0.0, 0.0, 256.0, 256.0)))
        a.add(Area(star(160.0, 100.0, 36.0, 15.0)))
    }

    private fun titanAltar(a: Area) {
        a.add(Area(roundRect(30.0, 176.0, 196.0, 56.0, 14.0)))
        a.add(Area(roundRect(60.0, 150.0, 136.0, 36.0, 10.0)))
        a.add(Area(drop(128.0, 100.0, 40.0)))
    }

    private fun alchemistGarden(a: Area) {
        a.add(Area(roundRect(20.0, 190.0, 216.0, 46.0, 12.0)))
        for (x in listOf(50.0, 90.0, 166.0, 206.0)) {
            a.add(Area(stroke(polyline(x, 190.0, x, 150.0), 8.0)))
            a.add(Area(ellipse(x - 10, 146.0, 12.0, 7.0))); a.add(Area(ellipse(x + 10, 140.0, 12.0, 7.0)))
        }
        val flask = Area(ellipse(128.0, 150.0, 34.0))
        flask.add(Area(rect(118.0, 70.0, 20.0, 60.0)))
        flask.add(Area(rect(110.0, 64.0, 36.0, 10.0)))
        a.add(flask)
        a.subtract(Area(ellipse(118.0, 156.0, 7.0))); a.subtract(Area(ellipse(138.0, 146.0, 5.0)))
    }

    //endregion

    private fun spyEye(a: Area) {
        val eye = GeneralPath()
        eye.moveTo(16.0, 128.0); eye.curveTo(80.0, 40.0, 176.0, 40.0, 240.0, 128.0)
        eye.curveTo(176.0, 216.0, 80.0, 216.0, 16.0, 128.0); eye.closePath()
        a.add(Area(eye))
        a.subtract(Area(ellipse(128.0, 128.0, 52.0)))
        a.add(Area(ellipse(128.0, 128.0, 30.0)))
        a.subtract(Area(ellipse(140.0, 116.0, 9.0)))
    }
}
