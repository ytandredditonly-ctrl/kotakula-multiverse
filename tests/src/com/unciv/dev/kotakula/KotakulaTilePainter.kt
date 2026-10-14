package com.unciv.dev.kotakula

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Three-layer map (docs/kotakula/layers.md, 6.4 and 7.4): pixel-art images of the "Kotakula" tileset, drawn by code -
 * terrains, features, biomes, layer resources and improvements, edges, route segments, the city on the other layers,
 * transition overlays, fog of war and the column markers.
 *
 * Canvas and hexagon shape are taken from FantasyHex `Tiles/Grassland.png` (32 x 28); the colours are the palette of 7.4;
 * light from the top left, outline one tone darker than the fill, no anti-aliasing.
 *
 *     ./gradlew :tests:kotakulaTiles      (then :tests:kotakulaAtlas)
 *
 * Writes into `Images/TileSets/Kotakula` of the mod and a contact sheet `tests/build/kotakula-tiles.png` (8x).
 */
object KotakulaTilePainter {
    private const val TILESET = "Images/TileSets/Kotakula"

    // 7.4 palette
    private val rock = c("3A3430"); private val rockLight = c("4A4440"); private val caveFloor = c("6B5A4A")
    private val glow = c("7CFFB0"); private val magma = c("FF6A1A"); private val magmaCore = c("FFD24A")
    private val darkWater = c("1C2B3A"); private val crystal = c("9FD8FF")
    private val skyTop = c("A9D8FF"); private val skyBottom = c("D8EEFF"); private val cloud = c("FFFFFF"); private val cloudShadow = c("C9D6E3")
    private val grass = c("7BC96F"); private val islandBottom = c("8A7A6A"); private val storm = c("4B5568"); private val lightning = c("FFE45C")
    private val sand = c("D9B26A"); private val ice = c("CFEFFF"); private val roots = c("5A3E26"); private val water = c("3B6E8F"); private val poison = c("7FD13B")

    private fun c(hex: String) = Color(hex.toInt(16))

    private lateinit var mask: Array<BooleanArray>
    private var w = 32
    private var h = 28
    private val written = ArrayList<Pair<String, BufferedImage>>()

    @JvmStatic
    fun main(args: Array<String>) {
        val shape = ImageIO.read(File("../Images.Tilesets/TileSets/FantasyHex/Tiles/Grassland.png"))
        w = shape.width
        h = shape.height
        mask = Array(w) { x -> BooleanArray(h) { y -> (shape.getRGB(x, y) ushr 24) > 0 } }
        val out = File(KotakulaModGenerator.MOD_PATH, TILESET)
        out.deleteRecursively()

        terrains(out)
        features(out)
        biomes(out)
        resourcesAndWonders(out)
        improvements(out)
        cityAndTransitions(out)
        edges(out)
        routes(out)
        signs(out)
        contactSheet()
        println("Kotakula tiles: ${written.size} images")
    }

    //region Canvas helpers

    private class Canvas(val image: BufferedImage) {
        fun set(x: Int, y: Int, color: Color, alpha: Int = 255) {
            if (x !in 0 until image.width || y !in 0 until image.height) return
            image.setRGB(x, y, (alpha shl 24) or (color.rgb and 0xFFFFFF))
        }
        fun get(x: Int, y: Int): Int = if (x !in 0 until image.width || y !in 0 until image.height) 0 else image.getRGB(x, y)
        fun isSet(x: Int, y: Int) = (get(x, y) ushr 24) > 0
    }

    private fun canvas(width: Int = w, height: Int = h) = Canvas(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB))
    private fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[x][y]
    private fun shade(color: Color, factor: Float) = Color(
        (color.red * factor).roundToInt().coerceIn(0, 255), (color.green * factor).roundToInt().coerceIn(0, 255),
        (color.blue * factor).roundToInt().coerceIn(0, 255))
    private fun mix(a: Color, b: Color, t: Float) = Color(
        (a.red + (b.red - a.red) * t).roundToInt(), (a.green + (b.green - a.green) * t).roundToInt(), (a.blue + (b.blue - a.blue) * t).roundToInt())

    /** Hexagon filled with [color], light from the top left, some noise, and the outline one tone darker */
    private fun hex(color: Color, random: Random, noise: Float = 0.08f, gradientTo: Color? = null): Canvas {
        val canvas = canvas()
        for (x in 0 until w) for (y in 0 until h) {
            if (!inside(x, y)) continue
            var base = if (gradientTo != null) mix(color, gradientTo, y / (h - 1f)) else color
            val light = 1.06f - 0.12f * ((x + y).toFloat() / (w + h))
            base = shade(base, light + (random.nextFloat() - 0.5f) * noise)
            canvas.set(x, y, base)
        }
        outline(canvas)
        return canvas
    }

    /** Pixels at the border of the hexagon get a darker tone */
    private fun outline(canvas: Canvas, factor: Float = 0.8f) {
        val edges = ArrayList<Pair<Int, Int>>()
        for (x in 0 until w) for (y in 0 until h)
            if (inside(x, y) && (!inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1))) edges += x to y
        for ((x, y) in edges) canvas.set(x, y, shade(Color(canvas.get(x, y) and 0xFFFFFF), factor))
    }

    private fun disc(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Color, alpha: Int = 255, clip: Boolean = true) {
        for (x in (cx - r).toInt() - 1..(cx + r).toInt() + 1) for (y in (cy - r).toInt() - 1..(cy + r).toInt() + 1)
            if (hypot(x - cx, y - cy) <= r && (!clip || inside(x, y))) canvas.set(x, y, color, alpha)
    }

    private fun rect(canvas: Canvas, x0: Int, y0: Int, width: Int, height: Int, color: Color, alpha: Int = 255) {
        for (x in x0 until x0 + width) for (y in y0 until y0 + height) canvas.set(x, y, color, alpha)
    }

    private fun line(canvas: Canvas, x0: Int, y0: Int, x1: Int, y1: Int, color: Color, alpha: Int = 255) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..steps) canvas.set(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, color, alpha)
    }

    /** Clouds of puffs, white with a shadow at the bottom */
    private fun puffs(canvas: Canvas, random: Random, count: Int, color: Color = cloud, shadow: Color = cloudShadow, rMin: Float = 2.5f, rMax: Float = 4.5f) {
        repeat(count) {
            val cx = 6f + random.nextFloat() * (w - 12)
            val cy = 6f + random.nextFloat() * (h - 12)
            val r = rMin + random.nextFloat() * (rMax - rMin)
            disc(canvas, cx, cy + 1, r, shadow)
            disc(canvas, cx, cy, r, color)
        }
    }

    private fun save(out: File, path: String, canvas: Canvas) {
        val file = File(out, "$path.png")
        file.parentFile.mkdirs()
        ImageIO.write(canvas.image, "png", file)
        written += path to canvas.image
    }

    private fun random(name: String) = Random(name.hashCode())

    //endregion
    //region Terrains (6.4, 7.4)

    private fun terrains(out: File) {
        save(out, "Tiles/Solid Rock", solidRock(random("Solid Rock")))
        save(out, "Tiles/Cave", cave(random("Cave"), caveFloor))
        save(out, "Tiles/Cave-Ice", cave(random("Cave-Ice"), mix(caveFloor, ice, 0.55f)).also { sprinkle(it, random("ice"), ice, 10) })
        save(out, "Tiles/Cave-Sand", cave(random("Cave-Sand"), mix(caveFloor, sand, 0.5f)).also { sprinkle(it, random("sand"), sand, 14) })
        save(out, "Tiles/Fungal Grotto", fungalGrotto(random("Fungal Grotto")))
        save(out, "Tiles/Underground Lake", undergroundLake(random("Underground Lake")))
        save(out, "Tiles/Magma Chamber", magmaChamber(random("Magma Chamber")))
        save(out, "Tiles/Open Sky", hex(skyTop, random("Open Sky"), 0.02f, skyBottom))
        save(out, "Tiles/Clouds", clouds(random("Clouds"), cloud))
        save(out, "Tiles/Clouds-Mirage", clouds(random("Clouds-Mirage"), mix(cloud, sand, 0.35f)))
        save(out, "Tiles/Clouds-Snow", clouds(random("Clouds-Snow"), mix(cloud, ice, 0.4f)).also { sprinkle(it, random("snow"), Color.WHITE, 12) })
        save(out, "Tiles/Floating Island", floatingIsland(random("Floating Island")))
        save(out, "Tiles/Storm Front", stormFront(random("Storm Front")))
    }

    private fun solidRock(random: Random): Canvas {
        val canvas = hex(rock, random, 0.1f)
        // Blocks of stone with a light top-left face
        for (row in 0 until 5) for (col in 0 until 5) {
            val x0 = col * 7 + (if (row % 2 == 0) 0 else 3)
            val y0 = row * 6 + 1
            for (i in 0 until 5) { if (inside(x0 + i, y0)) canvas.set(x0 + i, y0, rockLight) }
            for (j in 0 until 4) { if (inside(x0, y0 + j)) canvas.set(x0, y0 + j, rockLight) }
        }
        outline(canvas, 0.85f)
        return canvas
    }

    private fun cave(random: Random, floor: Color): Canvas {
        val canvas = hex(floor, random, 0.12f)
        // Dark vault at the top with stalactites
        for (x in 0 until w) for (y in 0 until 7) if (inside(x, y)) canvas.set(x, y, shade(rock, 1f + (random.nextFloat() - 0.5f) * 0.1f))
        for (x in 3 until w - 3 step 3) {
            val length = 2 + random.nextInt(4)
            for (y in 5 until 5 + length) if (inside(x, y)) canvas.set(x, y, rockLight)
        }
        // A few pebbles on the floor
        repeat(6) {
            val x = 4 + random.nextInt(w - 8); val y = 14 + random.nextInt(h - 18)
            if (inside(x, y)) canvas.set(x, y, shade(floor, 0.7f))
        }
        outline(canvas, 0.85f)
        return canvas
    }

    private fun mushroom(canvas: Canvas, x: Int, y: Int, cap: Color) {
        canvas.set(x, y, c("E8DCC0")); canvas.set(x, y + 1, c("E8DCC0"))
        for (dx in -1..1) canvas.set(x + dx, y - 1, cap)
        canvas.set(x, y - 2, cap)
        canvas.set(x - 1, y - 2, Color.WHITE)
    }

    private fun fungalGrotto(random: Random): Canvas {
        val canvas = hex(shade(caveFloor, 0.75f), random, 0.12f)
        for (x in 0 until w) for (y in 0 until 5) if (inside(x, y)) canvas.set(x, y, rock)
        val spots = listOf(7 to 12, 13 to 17, 20 to 11, 24 to 18, 10 to 22, 17 to 23, 26 to 14)
        for ((x, y) in spots) {
            disc(canvas, x.toFloat(), y.toFloat() - 1, 2.5f, glow, 60)
            mushroom(canvas, x, y, glow)
        }
        outline(canvas, 0.85f)
        return canvas
    }

    private fun undergroundLake(random: Random): Canvas {
        val canvas = hex(darkWater, random, 0.05f)
        for (i in 0 until 4) {
            val y = 8 + i * 5
            val x0 = 6 + random.nextInt(6)
            line(canvas, x0, y, x0 + 6 + random.nextInt(6), y, mix(darkWater, crystal, 0.25f))
        }
        disc(canvas, 21f, 9f, 1.2f, c("DDEEFF")) // the glint
        return canvas
    }

    private fun magmaChamber(random: Random): Canvas {
        val canvas = hex(rock, random, 0.1f)
        repeat(4) {
            var x = 6 + random.nextInt(w - 12)
            var y = 4 + random.nextInt(h - 8)
            repeat(12) {
                if (inside(x, y)) canvas.set(x, y, magma)
                if (inside(x + 1, y)) canvas.set(x + 1, y, shade(magma, 0.8f))
                x += random.nextInt(3) - 1
                y += random.nextInt(3) - 1
            }
        }
        disc(canvas, w / 2f, h / 2f, 3f, magma)
        disc(canvas, w / 2f, h / 2f, 1.6f, magmaCore)
        outline(canvas, 0.8f)
        return canvas
    }

    private fun clouds(random: Random, color: Color): Canvas {
        val canvas = hex(skyTop, random, 0.02f, skyBottom)
        puffs(canvas, random, 9, color, shade(color, 0.8f))
        outline(canvas, 0.92f)
        return canvas
    }

    private fun floatingIsland(random: Random): Canvas {
        val canvas = hex(skyTop, random, 0.02f, skyBottom)
        // Earth below, pointing down, with roots
        for (y in 14 until 25) {
            val half = (12 - (y - 14)).coerceAtLeast(1)
            for (x in w / 2 - half until w / 2 + half) if (inside(x, y)) canvas.set(x, y, shade(islandBottom, 1f - (y - 14) * 0.02f))
        }
        for (x in listOf(11, 15, 19)) line(canvas, x, 20, x + random.nextInt(3) - 1, 26, roots)
        // Grass on top
        for (y in 10 until 15) for (x in 4 until w - 4) {
            if (hypot((x - w / 2f) / 12f, (y - 13f) / 3.2f) <= 1f && inside(x, y))
                canvas.set(x, y, shade(grass, 1.05f - (y - 10) * 0.04f))
        }
        canvas.set(10, 11, c("A8E89C")); canvas.set(18, 11, c("A8E89C"))
        outline(canvas, 0.92f)
        return canvas
    }

    private fun stormFront(random: Random): Canvas {
        val canvas = hex(storm, random, 0.08f)
        puffs(canvas, random, 6, shade(storm, 1.25f), shade(storm, 0.8f))
        // Lightning bolt
        val points = listOf(18 to 5, 14 to 13, 18 to 13, 13 to 23)
        for (i in 0 until points.size - 1)
            line(canvas, points[i].first, points[i].second, points[i + 1].first, points[i + 1].second, lightning)
        outline(canvas, 0.8f)
        return canvas
    }

    private fun sprinkle(canvas: Canvas, random: Random, color: Color, count: Int, alpha: Int = 255) {
        repeat(count) {
            val x = random.nextInt(w); val y = random.nextInt(h)
            if (inside(x, y)) canvas.set(x, y, color, alpha)
        }
    }

    //endregion
    //region Features and biomes

    private fun features(out: File) {
        // Crystal Vein: shards on the cave floor
        val vein = canvas()
        for ((x, y) in listOf(10 to 18, 15 to 15, 20 to 19, 24 to 16)) {
            for (i in 0 until 4) vein.set(x, y - i, crystal)
            vein.set(x + 1, y - 1, shade(crystal, 0.75f)); vein.set(x - 1, y - 1, Color.WHITE)
        }
        save(out, "Tiles/Crystal Vein", vein)
        // Jet Stream: wind arrows
        val jet = canvas()
        for (y in listOf(9, 15, 21)) {
            for (x in 5 until 24) if (inside(x, y)) jet.set(x, y + (if ((x / 3) % 2 == 0) 0 else 1), Color.WHITE, 220)
            jet.set(24, y - 1, Color.WHITE); jet.set(25, y, Color.WHITE); jet.set(24, y + 1, Color.WHITE)
        }
        save(out, "Tiles/Jet Stream", jet)
        // Cave Entrance: an arch in the rock
        val entrance = canvas()
        for (y in 9 until 22) for (x in 9 until 23) {
            val d = hypot((x - 16f) / 7f, (y - 21f) / 12f)
            if (d <= 1f) entrance.set(x, y, if (d < 0.62f) c("120F0D") else if (x + y < 30) rockLight else rock)
        }
        save(out, "Tiles/Cave Entrance", entrance)
        // Updraft: a spiral going up with an arrow
        val updraft = canvas()
        for (i in 0 until 40) {
            val t = i / 40f
            val x = 16 + (cos(t * 12.5) * (7 - t * 5)).toFloat()
            val y = 23 - t * 16
            updraft.set(x.roundToInt(), y.roundToInt(), Color.WHITE, 230)
        }
        line(updraft, 16, 4, 13, 7, Color.WHITE); line(updraft, 16, 4, 19, 7, Color.WHITE)
        save(out, "Tiles/Updraft", updraft)
    }

    /** 7.3, 7.4: the 16 biomes as half transparent overlays */
    private fun biomes(out: File) {
        fun overlay(name: String, draw: (Canvas, Random) -> Unit) {
            val canvas = canvas()
            draw(canvas, random(name))
            save(out, "Tiles/$name", canvas)
        }
        overlay("Sand Caves") { canvas, r -> sprinkle(canvas, r, sand, 40, 170) }
        overlay("Ice Caves") { canvas, r -> repeat(8) { val x = 4 + r.nextInt(24); val y = 6 + r.nextInt(16)
            line(canvas, x, y - 2, x, y + 2, ice, 200); line(canvas, x - 2, y, x + 2, y, ice, 200) } }
        overlay("Root Grottoes") { canvas, r -> repeat(6) { val x = 4 + r.nextInt(24); line(canvas, x, 2, x + r.nextInt(5) - 2, 10 + r.nextInt(6), roots, 220) } }
        overlay("Flooded Caves") { canvas, r -> repeat(4) { disc(canvas, 6f + r.nextInt(20), 10f + r.nextInt(12), 2.5f, water, 170) } }
        overlay("Deep Caves") { canvas, r -> repeat(5) { val x = 5 + r.nextInt(22); val y = 12 + r.nextInt(10)
            for (i in 0 until 4) canvas.set(x, y - i, rockLight, 230); canvas.set(x - 1, y, rockLight, 230); canvas.set(x + 1, y, rockLight, 230) } }
        overlay("Salt Caves") { canvas, r -> repeat(10) { val x = 4 + r.nextInt(24); val y = 8 + r.nextInt(16)
            canvas.set(x, y, Color.WHITE, 230); canvas.set(x + 1, y, c("E8F0F4"), 230); canvas.set(x, y - 1, c("E8F0F4"), 230) } }
        overlay("Catacombs") { canvas, _ ->
            for ((x, y) in listOf(9 to 16, 21 to 17, 15 to 21)) { rect(canvas, x, y, 3, 2, c("E8E2D0")); canvas.set(x, y + 1, Color.BLACK); canvas.set(x + 2, y + 1, Color.BLACK) }
            line(canvas, 6, 23, 26, 23, c("D8D0B8"), 200) }
        overlay("Poisoned Caves") { canvas, r -> repeat(6) { disc(canvas, 6f + r.nextInt(20), 9f + r.nextInt(14), 2f, poison, 170) } }
        overlay("Mirage Clouds") { canvas, r -> repeat(5) { val y = 6 + r.nextInt(16); line(canvas, 6, y, 26, y, sand, 110) } }
        overlay("Snow Clouds") { canvas, r -> repeat(12) { val x = 3 + r.nextInt(26); val y = 3 + r.nextInt(22); canvas.set(x, y, Color.WHITE)
            canvas.set(x + 1, y + 1, ice) } }
        overlay("Mist Clouds") { canvas, r -> repeat(5) { disc(canvas, 6f + r.nextInt(20), 7f + r.nextInt(14), 4f, Color.WHITE, 90) } }
        overlay("Rain Clouds") { canvas, r -> repeat(10) { val x = 4 + r.nextInt(24); val y = 8 + r.nextInt(14); line(canvas, x, y, x - 1, y + 2, water, 220) } }
        overlay("High Winds") { canvas, r -> repeat(5) { val y = 5 + r.nextInt(18); val x = 3 + r.nextInt(8); line(canvas, x, y, x + 14, y, Color.WHITE, 200) } }
        overlay("City Haze") { canvas, r -> repeat(5) { disc(canvas, 8f + r.nextInt(16), 8f + r.nextInt(12), 4f, c("B8B8B8"), 80) } }
        overlay("Smog") { canvas, r -> repeat(6) { disc(canvas, 6f + r.nextInt(20), 6f + r.nextInt(14), 4.5f, c("5A5A5A"), 110) } }
        overlay("Toxic Clouds") { canvas, r -> repeat(6) { disc(canvas, 6f + r.nextInt(20), 6f + r.nextInt(14), 3.5f, poison, 120) } }
    }

    //endregion
    //region Resources, wonders, improvements

    private fun resourcesAndWonders(out: File) {
        val glowcaps = canvas()
        for ((x, y) in listOf(12 to 17, 16 to 15, 20 to 17, 14 to 21, 18 to 21)) {
            disc(glowcaps, x.toFloat(), y.toFloat() - 1, 2.6f, glow, 70, clip = false)
            mushroom(glowcaps, x, y, glow)
        }
        save(out, "Tiles/Glowcaps", glowcaps)
        val silk = canvas()
        disc(silk, 16f, 15f, 5f, Color.WHITE, clip = false)
        for (i in -4..4 step 2) line(silk, 12, 15 + i, 20, 15 + i / 2, cloudShadow)
        line(silk, 20, 18, 26, 22, Color.WHITE)
        save(out, "Tiles/Cloud Silk", silk)

        val heart = solidRock(random("Heart of the Mountain"))
        disc(heart, 13.5f, 12f, 3.2f, c("C0182A")); disc(heart, 18.5f, 12f, 3.2f, c("C0182A"))
        for (y in 12 until 21) { val half = (20 - y) * 0.75f; for (x in (16 - half).toInt()..(16 + half).toInt()) heart.set(x, y, c("C0182A")) }
        heart.set(13, 10, c("FF7080")); heart.set(14, 10, c("FF7080"))
        save(out, "Tiles/Heart of the Mountain", heart)
        val throne = clouds(random("Celestial Throne"), cloud)
        rect(throne, 12, 6, 8, 10, c("8C8C96")); rect(throne, 10, 14, 12, 4, c("A0A0AA")); rect(throne, 13, 7, 6, 6, c("6E6E78"))
        rect(throne, 12, 18, 2, 4, c("8C8C96")); rect(throne, 18, 18, 2, 4, c("8C8C96"))
        save(out, "Tiles/Celestial Throne", throne)
    }

    /** 3.5, 7.2: the improvements of the layers as small sprites over the tile */
    private fun improvements(out: File) {
        fun sprite(name: String, draw: (Canvas) -> Unit) {
            val canvas = canvas()
            draw(canvas)
            save(out, "Tiles/$name", canvas)
        }
        val wood = c("8B5A2B"); val metal = c("9AA3AD"); val stone = c("8C8C96"); val darkHole = c("120F0D")
        sprite("Mine Shaft") { s -> line(s, 11, 22, 16, 8, wood); line(s, 21, 22, 16, 8, wood); line(s, 13, 15, 19, 15, wood)
            disc(s, 16f, 8f, 2.2f, metal, clip = false); rect(s, 13, 21, 7, 3, darkHole) }
        sprite("Tunnel") { s -> rect(s, 11, 12, 2, 10, wood); rect(s, 20, 12, 2, 10, wood); rect(s, 11, 11, 11, 2, wood); rect(s, 13, 13, 7, 9, darkHole) }
        sprite("Aerostat Mast") { s -> line(s, 16, 22, 16, 11, metal); disc(s, 16f, 7f, 4f, c("D04040"), clip = false); line(s, 16, 11, 13, 9, Color.WHITE); line(s, 16, 11, 19, 9, Color.WHITE) }
        sprite("Air Platform") { s -> rect(s, 8, 13, 16, 5, metal); for (x in 8 until 24 step 3) line(s, x, 13, x, 17, shade(metal, 0.7f)); line(s, 10, 18, 9, 22, metal); line(s, 22, 18, 23, 22, metal) }
        sprite("Cloud Stair") { s -> for (i in 0 until 4) rect(s, 9 + i * 3, 21 - i * 3, 8, 3, if (i % 2 == 0) Color.WHITE else cloudShadow) }
        sprite("Cloud Bridge") { s -> for (x in 6 until 27) { val y = 17 - (6 - abs(x - 16) / 2); s.set(x, y, Color.WHITE); s.set(x, y + 1, cloudShadow) } }
        sprite("Sinkhole") { s -> for (x in 8 until 25) for (y in 12 until 24) { val d = hypot((x - 16f) / 8f, (y - 18f) / 5f)
            if (d <= 1f) s.set(x, y, if (d < 0.7f) darkHole else c("6B5038")) } }
        sprite("Magic Passage") { s -> for ((x, y) in listOf(9 to 18, 13 to 15, 17 to 18, 21 to 15)) { rect(s, x, y, 3, 3, c("B070FF")); s.set(x + 1, y + 1, Color.WHITE) } }
        sprite("Mushroom Farm") { s -> for (row in 0 until 2) for (col in 0 until 4) mushroom(s, 9 + col * 5, 15 + row * 6, glow) }
        sprite("Deep Mine") { s -> line(s, 11, 22, 21, 12, wood); line(s, 17, 10, 23, 14, metal); line(s, 18, 9, 24, 13, metal); rect(s, 9, 20, 6, 3, stone) }
        sprite("Sky Garden") { s -> for (col in 0 until 4) { disc(s, 9f + col * 5, 16f, 2.2f, c("3E9A3A"), clip = false); s.set(9 + col * 5, 19, c("6B4A2B")) } }
        sprite("Sky Quarry") { s -> rect(s, 9, 15, 6, 5, stone); rect(s, 16, 13, 7, 7, shade(stone, 1.15f)); rect(s, 12, 20, 9, 3, shade(stone, 0.85f)) }
        sprite("Cloud Loom") { s -> line(s, 10, 22, 22, 10, wood); disc(s, 16f, 16f, 3.5f, Color.WHITE, clip = false); line(s, 19, 16, 26, 20, Color.WHITE) }
        sprite("Storm Rod") { s -> line(s, 16, 22, 16, 6, metal); s.set(16, 5, c("FFD700")); line(s, 19, 7, 17, 11, lightning); line(s, 17, 11, 20, 12, lightning) }
        sprite("Transit Node") { s -> for (i in 0 until 28) { val a = i / 28.0 * Math.PI * 2; s.set((16 + cos(a) * 6).roundToInt(), (16 + sin(a) * 5).roundToInt(), metal) }
            line(s, 16, 8, 16, 24, crystal); s.set(15, 9, crystal); s.set(17, 9, crystal); s.set(15, 23, crystal); s.set(17, 23, crystal) }
        sprite("Elemental Axis") { s -> rect(s, 15, 6, 3, 18, stone); disc(s, 16f, 6f, 2.5f, glow, clip = false); disc(s, 16f, 24f, 2.5f, magma, 200, clip = false) }
    }

    /** 7.4: the city on the other layers and the transition buildings, overlays of the center column */
    private fun cityAndTransitions(out: File) {
        val catacombs = canvas()
        for (y in 10 until 24) for (x in 8 until 24) { val d = hypot((x - 16f) / 8f, (y - 23f) / 13f); if (d <= 1f) catacombs.set(x, y, if (d < 0.65f) c("120F0D") else c("9A8A70")) }
        for (i in 0 until 3) rect(catacombs, 12 + i, 20 + i, 8 - 2 * i, 1, c("6B5A4A"))
        save(out, "Tiles/City center-Underground", catacombs)
        val platform = canvas()
        for (y in 12 until 22) for (x in 4 until 28) { val d = hypot((x - 16f) / 12f, (y - 17f) / 5f); if (d <= 1f) platform.set(x, y, if (y < 17) c("B4BCC8") else c("7E8794")) }
        for (x in 6 until 27 step 4) line(platform, x, 13, x, 10, c("D8DEE6"))
        save(out, "Tiles/City center-Sky", platform)
        fun overlay(name: String, draw: (Canvas) -> Unit) { val canvas = canvas(); draw(canvas); save(out, "Tiles/$name", canvas) }
        overlay("Transition-Lift") { s -> rect(s, 22, 6, 5, 7, c("5A5A5A")); line(s, 24, 0, 24, 6, c("C8C8C8")); line(s, 22, 6, 26, 12, c("8A8A8A")) }
        overlay("Transition-Aerostat") { s -> disc(s, 24f, 6f, 3.5f, c("D04040"), clip = false); line(s, 24, 10, 24, 14, Color.WHITE) }
        overlay("Transition-Cloud Gate") { s -> for (i in 0 until 16) { val a = Math.PI * i / 15; s.set((24 + cos(a) * 4).roundToInt(), (11 - sin(a) * 5).roundToInt(), Color.WHITE) }
            line(s, 20, 11, 20, 14, Color.WHITE); line(s, 28, 11, 28, 14, Color.WHITE) }
        overlay("Transition-Earth Portal") { s -> rect(s, 21, 5, 7, 9, c("6E6058")); rect(s, 23, 7, 3, 7, c("2A2420")); s.set(25, 10, c("FFD24A")) }
        overlay("Transition-Axis") { s -> rect(s, 23, 1, 3, 15, c("B8B0A0")); disc(s, 24f, 2f, 2f, glow, clip = false); disc(s, 24f, 15f, 2f, magma, clip = false) }
    }

    //endregion
    //region Edges, routes, fog and signs

    /** Directions of the edge images and their side of the hexagon, as angles from the centre (y down) */
    private val directions = linkedMapOf("Top" to -90.0, "TopRight" to -30.0, "BottomRight" to 30.0,
        "Bottom" to 90.0, "BottomLeft" to 150.0, "TopLeft" to -150.0)

    /** Pixels of the hexagon near the side towards [angle] */
    private fun sidePixels(angle: Double, depth: Float): List<Pair<Int, Int>> {
        val dx = cos(Math.toRadians(angle)); val dy = sin(Math.toRadians(angle))
        val cx = (w - 1) / 2.0; val cy = (h - 1) / 2.0
        val maxProjection = (0 until w).flatMap { x -> (0 until h).map { y -> x to y } }.filter { (x, y) -> inside(x, y) }
            .maxOf { (x, y) -> (x - cx) * dx + (y - cy) * dy * (w.toDouble() / h) }
        return (0 until w).flatMap { x -> (0 until h).map { y -> x to y } }.filter { (x, y) ->
            inside(x, y) && maxProjection - ((x - cx) * dx + (y - cy) * dy * (w.toDouble() / h)) < depth
        }
    }

    private fun edges(out: File) {
        for ((direction, angle) in directions) {
            // Rock walls where a Cave or a Fungal Grotto touches Solid Rock
            for (floor in listOf("Cave", "Fungal Grotto")) {
                val wall = canvas()
                for ((x, y) in sidePixels(angle, 3.2f)) wall.set(x, y, if ((x + y) % 3 == 0) rockLight else rock)
                save(out, "Edges/RockWall/RockWall-$floor-Solid Rock-$direction", wall)
            }
            // Island edges: the stone underside with roots at the bottom, a grassy rim at the top
            val island = canvas()
            val bottom = direction.startsWith("Bottom")
            for ((x, y) in sidePixels(angle, if (bottom) 4f else 2.2f)) island.set(x, y, if (bottom) (if ((x * 7 + y) % 5 == 0) roots else islandBottom) else grass)
            save(out, "Edges/IslandEdge/IslandEdge-Floating Island-Open Sky-$direction", island)
            // Soft cloud edges
            val cloudEdge = canvas()
            for ((x, y) in sidePixels(angle, 2.8f)) cloudEdge.set(x, y, if ((x + y) % 4 == 0) cloudShadow else Color.WHITE, 200)
            save(out, "Edges/CloudEdge/CloudEdge-Clouds-Open Sky-$direction", cloudEdge)
        }
    }

    /** 7.4: road-like segments of the layer routes, the size of FantasyHex Road.png */
    private fun routes(out: File) {
        fun segment(name: String, base: Color, detail: Color, every: Int) {
            val canvas = canvas(60, 32)
            for (x in 0 until 60) for (y in 10 until 22) canvas.set(x, y, if (y == 10 || y == 21) shade(base, 0.7f) else base)
            for (x in 0 until 60 step every) for (y in 11 until 21) canvas.set(x, y, detail)
            save(out, "Roads/$name", canvas)
        }
        segment("Tunnel", c("2A2420"), c("8B5A2B"), 10)
        segment("Magic Passage", c("2C1F48"), c("B070FF"), 8)
        segment("Air Platform", c("9AA3AD"), c("6E7680"), 6)
        segment("Cloud Bridge", Color.WHITE, cloudShadow, 12)
    }

    private fun signs(out: File) {
        // Fog of war and unexplored tiles of the layers (7.4)
        save(out, "UnexploredTile-Underground", hex(c("1A1714"), random("unexplored-u"), 0.15f))
        save(out, "UnexploredTile-Sky", hex(c("E2ECF6"), random("unexplored-s"), 0.03f))
        // Column markers (white, tinted by the game) and the transition sign
        fun arrow(up: Boolean): Canvas {
            val canvas = canvas(16, 16)
            for (y in 0 until 16) for (x in 0 until 16) {
                val yy = if (up) y else 15 - y
                val isHead = yy < 8 && abs(x - 7.5f) <= yy
                val isShaft = yy >= 8 && x in 5..10
                if (isHead || isShaft) canvas.set(x, y, Color.WHITE)
            }
            return canvas
        }
        save(out, "LayerMarkerUp", arrow(true))
        save(out, "LayerMarkerDown", arrow(false))
        val ladder = canvas(16, 16)
        line(ladder, 3, 15, 3, 2, Color.WHITE); line(ladder, 8, 15, 8, 2, Color.WHITE)
        for (y in 4 until 16 step 3) line(ladder, 3, y, 8, y, Color.WHITE)
        line(ladder, 12, 14, 12, 2, Color.WHITE); line(ladder, 12, 2, 10, 5, Color.WHITE); line(ladder, 12, 2, 14, 5, Color.WHITE)
        save(out, "TransitionIcon", ladder)
    }

    /** All images at 8x on one sheet, for looking at them */
    private fun contactSheet() {
        val scale = 4
        val cell = 64 * scale / 2
        val columns = 10
        val rows = (written.size + columns - 1) / columns
        val sheet = BufferedImage(columns * cell, rows * (cell + 14), BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        g.color = Color(40, 44, 52)
        g.fillRect(0, 0, sheet.width, sheet.height)
        for ((index, item) in written.withIndex()) {
            val x = (index % columns) * cell
            val y = (index / columns) * (cell + 14)
            val image = item.second
            val factor = minOf(cell / image.width, cell / image.height)
            g.drawImage(image, x, y, image.width * factor, image.height * factor, null)
            g.color = Color.WHITE
            g.drawString(item.first.substringAfterLast('/').take(22), x + 2, y + cell + 11)
        }
        g.dispose()
        val out = File("../../tests/build").apply { mkdirs() }
        ImageIO.write(sheet, "png", File(out, "kotakula-tiles.png"))
    }

    //endregion
}
