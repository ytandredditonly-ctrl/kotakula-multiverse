package com.unciv.logic.kotakula.layers

import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapLayer
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerationRandomness
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.debug
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/** Terrain ids of the three-layer map (docs/kotakula/layers.md, section 3). The engine generates the layers from them. */
object LayerTerrains {
    const val cave = "Cave"
    const val fungalGrotto = "Fungal Grotto"
    const val undergroundLake = "Underground Lake"
    const val magmaChamber = "Magma Chamber"
    const val solidRock = "Solid Rock"
    const val openSky = "Open Sky"
    const val clouds = "Clouds"
    const val floatingIsland = "Floating Island"
    const val stormFront = "Storm Front"
    const val crystalVein = "Crystal Vein"
    const val jetStream = "Jet Stream"
    const val caveEntrance = "Cave Entrance"
    const val updraft = "Updraft"
    const val heartOfTheMountain = "Heart of the Mountain"
    const val celestialThrone = "Celestial Throne"
}

/**
 * Kotakula (section 5): generation of the Underground and the Sky from a finished Surface.
 *
 * Deterministic: the Underground uses `mapSeed + 1`, the Sky `mapSeed + 2` (5.x), so one seed gives the same layers (L1).
 */
class LayerMapGenerator(private val ruleset: Ruleset, private val surface: TileMap) {

    /** What the generation changed, for the log and the tests */
    class Report {
        var undergroundPassableOverLand = 0f
        var skyPassableOverLand = 0f
        var caveEntrances = 0
        var updrafts = 0
        var fairnessFixes = 0
        override fun toString() = "Underground passable over land ${(undergroundPassableOverLand * 100).roundToInt()}%, " +
            "Sky ${(skyPassableOverLand * 100).roundToInt()}%, $caveEntrances cave entrances, $updrafts updrafts, $fairnessFixes start fixes"
    }

    val report = Report()
    lateinit var underground: TileMap
    lateinit var sky: TileMap

    private val seed = surface.mapParameters.seed
    private val landTiles = surface.values.filter { it.isLand }

    /** Generates both layers and their resources (5.1-5.3). Starts are checked later by [ensureFairStarts]. */
    fun generate(): Pair<TileMap, TileMap> {
        underground = createLayerMap(MapLayer.Underground, LayerTerrains.solidRock)
        sky = createLayerMap(MapLayer.Sky, LayerTerrains.openSky)
        surface.linkLayers(underground, sky)

        val undergroundRandom = Random(seed + 1)
        generateUnderground(undergroundRandom)
        val skyRandom = Random(seed + 2)
        generateSky(skyRandom)

        placeLayerResources(Random(seed + 3))
        placeNaturalWonders(Random(seed + 4))
        placeRuins(Random(seed + 5))

        report.undergroundPassableOverLand = passableShareOverLand(MapLayer.Underground)
        report.skyPassableOverLand = passableShareOverLand(MapLayer.Sky)
        debug("LayerMapGenerator: %s", report)
        return underground to sky
    }

    private fun createLayerMap(layer: MapLayer, baseTerrain: String): TileMap {
        val map = TileMap(surface.tileList.size)
        map.layer = layer
        map.mapParameters = surface.mapParameters
        for (tile in surface.tileList)
            map.tileList.add(Tile().apply {
                position = tile.position
                this.baseTerrain = baseTerrain
            })
        map.setTransients(ruleset, setUnitCivTransients = false)
        return map
    }

    private fun terrainExists(name: String) = ruleset.terrains.containsKey(name)

    private fun Tile.setBase(name: String) {
        if (!terrainExists(name)) return
        baseTerrain = name
        setTerrainTransients()
    }

    private fun Tile.isLayerPassable() = !isImpassible()

    //region 5.1 Underground

    private enum class SurfaceKind { Mountain, Hill, Flat, Forest, Lake, Coast, Ocean }

    private fun surfaceKind(tile: Tile): SurfaceKind = when {
        tile.isWater && tile.getBaseTerrain().isFreshwater -> SurfaceKind.Lake
        tile.isWater && tile.getBaseTerrain().isCoast -> SurfaceKind.Coast
        tile.isWater -> SurfaceKind.Ocean
        tile.allTerrains.any { it.isMountain } -> SurfaceKind.Mountain
        tile.isHill() -> SurfaceKind.Hill
        tile.terrainFeatureObjects.any { it.isVegetation } -> SurfaceKind.Forest
        else -> SurfaceKind.Flat
    }

    /** 5.1 step 1: probabilities in % of Cave, Fungal Grotto, Underground Lake, Magma Chamber, Solid Rock */
    private val undergroundOdds = mapOf(
        SurfaceKind.Mountain to intArrayOf(15, 0, 0, 25, 60),
        SurfaceKind.Hill to intArrayOf(40, 10, 0, 5, 45),
        SurfaceKind.Flat to intArrayOf(25, 20, 5, 0, 50),
        SurfaceKind.Forest to intArrayOf(25, 30, 5, 0, 40),
        SurfaceKind.Lake to intArrayOf(0, 0, 70, 0, 30),
        SurfaceKind.Coast to intArrayOf(10, 0, 30, 0, 60),
        SurfaceKind.Ocean to intArrayOf(0, 0, 10, 0, 90),
    )
    private val undergroundTerrains = listOf(LayerTerrains.cave, LayerTerrains.fungalGrotto,
        LayerTerrains.undergroundLake, LayerTerrains.magmaChamber, LayerTerrains.solidRock)

    private fun generateUnderground(random: Random) {
        // Step 1: base terrain by the surface above
        for (tile in underground.values) {
            val above = tile.surfaceTile()
            val odds = undergroundOdds[surfaceKind(above)]!!
            var roll = random.nextInt(100)
            var chosen = undergroundTerrains.last()
            for ((index, chance) in odds.withIndex()) {
                if (roll < chance) { chosen = undergroundTerrains[index]; break }
                roll -= chance
            }
            tile.setBase(chosen)
        }

        // Step 2: cave systems - diggers walk under the land and turn Solid Rock into Caves
        val diggers = ceil(landTiles.size / 60.0).toInt()
        val digStarts = underground.values.filter { it.baseTerrain == LayerTerrains.cave &&
            surfaceKind(it.surfaceTile()).let { kind -> kind == SurfaceKind.Hill || kind == SurfaceKind.Mountain } }
        repeat(diggers) {
            if (digStarts.isEmpty()) return@repeat
            var current = digStarts.random(random)
            val steps = 15 + random.nextInt(16)
            repeat(steps) {
                val options = current.neighbors.filter { it.surfaceTile().isLand }.toList()
                if (options.isEmpty()) return@repeat
                current = options.random(random)
                if (current.baseTerrain == LayerTerrains.solidRock) current.setBase(LayerTerrains.cave)
            }
        }

        // Step 3: cleaning - passable groups smaller than 3 tiles become Solid Rock
        for (group in passableGroups(underground))
            if (group.size < 3) for (tile in group) tile.setBase(LayerTerrains.solidRock)

        // Calibration (OPEN_QUESTIONS 22): steps 1-3 as written give ~55% passable under land, the goal is 40-50%.
        // Erode the edges of the cave systems back to Solid Rock until the middle of the corridor is reached.
        calibrateUnderground(random)

        // Step 4: crystal veins on 10% of the caves
        if (terrainExists(LayerTerrains.crystalVein)) {
            val caves = underground.values.filter { it.baseTerrain == LayerTerrains.cave }.shuffled(random)
            for (tile in caves.take((caves.size * 0.1).roundToInt()))
                tile.addTerrainFeature(LayerTerrains.crystalVein)
        }

        // Step 5: cave entrances, one per 40 land tiles, over passable groups of 10+ tiles, 4+ apart
        if (terrainExists(LayerTerrains.caveEntrance)) {
            val bigGroupTiles = passableGroups(underground).filter { it.size >= 10 }.flatten().toHashSet()
            val candidates = landTiles.filter { canHaveCaveEntrance(it) && it.layerTile(MapLayer.Underground) in bigGroupTiles }
                .shuffled(random)
            report.caveEntrances = placeSpread(candidates, ceil(landTiles.size / 40.0).toInt(), LayerTerrains.caveEntrance)
        }
    }

    /** 3.3: Hills, Plains, Grassland, Tundra, Desert - and no other feature than a hill */
    private fun canHaveCaveEntrance(tile: Tile): Boolean {
        if (!tile.isLand || tile.isImpassible() || tile.isNaturalWonder()) return false
        if (tile.terrainFeatureObjects.any { !it.isHill }) return false
        val feature = ruleset.terrains[LayerTerrains.caveEntrance] ?: return false
        return feature.occursOn.isEmpty() || tile.baseTerrain in feature.occursOn
    }

    /** 3.3: land next to Mountains or Hills, without other features */
    private fun canHaveUpdraft(tile: Tile): Boolean {
        if (!tile.isLand || tile.isImpassible() || tile.isNaturalWonder()) return false
        if (tile.terrainFeatureObjects.any { !it.isHill }) return false
        val feature = ruleset.terrains[LayerTerrains.updraft] ?: return false
        if (feature.occursOn.isNotEmpty() && tile.baseTerrain !in feature.occursOn) return false
        return tile.isHill() || tile.neighbors.any { neighbor -> neighbor.isHill() || neighbor.allTerrains.any { it.isMountain } }
    }

    /** Puts [feature] on up to [count] of the [candidates] at least 4 tiles apart; returns how many */
    private fun placeSpread(candidates: List<Tile>, count: Int, feature: String): Int {
        val placed = ArrayList<Tile>()
        for (tile in candidates) {
            if (placed.size >= count) break
            if (placed.any { it.aerialDistanceTo(tile) < 4 }) continue
            tile.addTerrainFeature(feature)
            placed += tile
        }
        return placed.size
    }

    //endregion
    //region 5.2 Sky

    private fun generateSky(random: Random) {
        // Step 1 is the Open Sky every layer map starts with
        // Step 2: clouds from a noise map, threshold so that they cover 30% over land and 40% over water
        val randomness = MapGenerationRandomness()
        randomness.seedRNG(seed + 2)
        val noiseSeed = random.nextDouble() * 1000
        val noise = HashMap<Tile, Double>(sky.tileList.size)
        for (tile in sky.values) noise[tile] = randomness.getPerlinNoise(tile, noiseSeed, scale = 8.0)
        val overLand = sky.values.filter { it.surfaceTile().isLand }
        val overWater = sky.values.filter { !it.surfaceTile().isLand }
        fun cloudOver(tiles: List<Tile>, share: Double) {
            val sorted = tiles.sortedByDescending { noise[it]!! }
            for (tile in sorted.take((tiles.size * share).roundToInt())) tile.setBase(LayerTerrains.clouds)
        }
        cloudOver(overLand, CLOUDS_OVER_LAND)
        cloudOver(overWater, CLOUDS_OVER_WATER)

        // Step 3: floating islands - all tiles / 80, 3-7 tiles each, 4+ apart, not near the poles
        val maxLatitude = sky.values.maxOf { abs(it.latitude) }.coerceAtLeast(1)
        val islandCount = sky.tileList.size / 80
        val islandSeeds = sky.values.filter { it.baseTerrain == LayerTerrains.clouds && abs(it.latitude) <= maxLatitude * 0.6 }
            .shuffled(random)
        val islands = ArrayList<List<Tile>>()
        for (start in islandSeeds) {
            if (islands.size >= islandCount) break
            if (islands.any { island -> island.any { it.aerialDistanceTo(start) < 4 } }) continue
            val size = 3 + random.nextInt(5)
            val island = arrayListOf(start)
            while (island.size < size) {
                val options = island.flatMap { it.neighbors.toList() }.distinct()
                    .filter { it !in island && islands.none { other -> other.any { o -> o.aerialDistanceTo(it) < 4 } } }
                if (options.isEmpty()) break
                island += options.random(random)
            }
            for (tile in island) tile.setBase(LayerTerrains.floatingIsland)
            islands += island
        }
        this.islands = islands

        // Step 4: storm fronts on 5% of the tiles, at the edges of the cloud masses
        val cloudEdges = sky.values.filter { it.baseTerrain == LayerTerrains.clouds &&
            it.neighbors.any { neighbor -> neighbor.baseTerrain == LayerTerrains.openSky } }.shuffled(random)
        for (tile in cloudEdges.take((sky.tileList.size * 0.05).roundToInt()))
            tile.setBase(LayerTerrains.stormFront)

        // Calibration (OPEN_QUESTIONS 22): clouds on 30% plus the islands give ~32% passable over land, the goal is 40-50%.
        // The cloud masses over land grow along the noise map until the middle of the corridor is reached.
        calibrateSky(noise)

        // Step 5: jet streams - horizontal rows 1 tile wide, 15%-85% of the height, 4+ rows apart
        if (terrainExists(LayerTerrains.jetStream)) {
            val rows = sky.values.map { it.getRow() }
            val minRow = rows.min()
            val height = rows.max() - minRow + 1
            val chosenRows = ArrayList<Int>()
            var attempts = 0
            while (chosenRows.size < jetStreamCount() && attempts++ < 200) {
                val row = minRow + (height * 0.15).toInt() + random.nextInt(((height * 0.7).toInt()).coerceAtLeast(1))
                if (chosenRows.any { abs(it - row) < 4 }) continue
                chosenRows += row
            }
            for (tile in sky.values)
                if (tile.getRow() in chosenRows && (tile.baseTerrain == LayerTerrains.clouds || tile.baseTerrain == LayerTerrains.openSky))
                    tile.addTerrainFeature(LayerTerrains.jetStream)
        }

        // Step 6: updrafts, one per 40 land tiles, under passable sky, 4+ apart
        if (terrainExists(LayerTerrains.updraft)) {
            val candidates = landTiles.filter { canHaveUpdraft(it) && it.layerTile(MapLayer.Sky)!!.isLayerPassable() }.shuffled(random)
            report.updrafts = placeSpread(candidates, ceil(landTiles.size / 40.0).toInt(), LayerTerrains.updraft)
        }
    }

    private var islands: List<List<Tile>> = emptyList()

    /** Underground: passable tiles under land with the most Solid Rock around become rock, until [TARGET_PASSABLE] */
    private fun calibrateUnderground(random: Random) {
        val underLand = underground.values.filter { it.surfaceTile().isLand }
        if (underLand.isEmpty()) return
        val target = (underLand.size * TARGET_PASSABLE).roundToInt()
        var passable = underLand.count { it.isLayerPassable() }
        while (passable > target) {
            val edges = underLand.filter { it.isLayerPassable() && !it.isNaturalWonder() }
                .groupBy { tile -> tile.neighbors.count { it.baseTerrain == LayerTerrains.solidRock } }
            val mostExposed = edges.maxByOrNull { it.key }?.value ?: break
            for (tile in mostExposed.shuffled(random)) {
                if (passable <= target) break
                tile.setBase(LayerTerrains.solidRock)
                passable--
            }
        }
        for (group in passableGroups(underground))
            if (group.size < 3) for (tile in group) tile.setBase(LayerTerrains.solidRock)
    }

    /** Sky: Open Sky over land next to Clouds becomes Clouds in the order of the noise map, until [TARGET_PASSABLE] */
    private fun calibrateSky(noise: Map<Tile, Double>) {
        val overLand = sky.values.filter { it.surfaceTile().isLand }
        if (overLand.isEmpty()) return
        val target = (overLand.size * TARGET_PASSABLE).roundToInt()
        var passable = overLand.count { it.isLayerPassable() }
        while (passable < target) {
            val front = overLand.filter { it.baseTerrain == LayerTerrains.openSky && it.neighbors.any { n -> n.isLayerPassable() } }
                .sortedByDescending { noise[it] }
            if (front.isEmpty()) break
            for (tile in front.take(maxOf(1, front.size / 4))) {
                if (passable >= target) break
                tile.setBase(LayerTerrains.clouds)
                passable++
            }
        }
    }

    /** 5.2 step 5: Tiny and Small - 2, Medium and Large - 3, Huge - 4 */
    private fun jetStreamCount(): Int {
        val tiles = surface.tileList.size
        return when {
            tiles < 1200 -> 2
            tiles < 3000 -> 3
            else -> 4
        }
    }

    //endregion
    //region 5.3 Resources, wonders, ruins

    private fun placeLayerResources(random: Random) {
        // 1-2: a share of strategic deposits moves from the Surface into its layer, bigger by the extra amount
        for (resource in ruleset.tileResources.values) {
            for (unique in resource.getMatchingUniques(UniqueType.KMLayerDeposits)) {
                val share = unique.params[0].toFloat() / 100f
                val layer = MapLayer.fromString(unique.params[1]) ?: continue
                val terrainFilter = unique.params[2]
                val extra = unique.params[3].toInt()
                val surfaceDeposits = surface.values.filter { it.resource == resource.name }.shuffled(random)
                val toMove = (surfaceDeposits.size * share).roundToInt()
                val targets = layerMap(layer).values
                    .filter { it.resource == null && !it.isImpassible() && it.matchesTerrainFilter(terrainFilter, null) }
                    .shuffled(random).iterator()
                for (deposit in surfaceDeposits.take(toMove)) {
                    if (!targets.hasNext()) break
                    val target = targets.next()
                    val amount = deposit.resourceAmount
                    deposit.tileResource = null
                    deposit.resourceAmount = 0
                    target.tileResource = resource
                    target.resourceAmount = amount + extra
                }
            }
        }

        // 3: layer luxuries (Glowcaps, Cloud Silk) as many as an average vanilla luxury of this map
        val surfaceLuxuries = surface.values.mapNotNull { it.tileResource }.filter { it.resourceType == ResourceType.Luxury }
        val averageLuxury = if (surfaceLuxuries.isEmpty()) 4
            else surfaceLuxuries.size / surfaceLuxuries.map { it.name }.distinct().size
        for (resource in ruleset.tileResources.values) {
            if (resource.resourceType == ResourceType.Strategic) continue
            val layer = layerOfResource(resource) ?: continue
            val targets = layerMap(layer).values
                .filter { it.resource == null && !it.isImpassible() && resource.terrainsCanBeFoundOn.any { name -> it.matchesTerrainFilter(name, null) } }
                .shuffled(random)
            for (tile in targets.take(averageLuxury)) tile.setTileResource(resource, rng = random)
        }
    }

    /** The layer a resource can only be found on, from the layer of its terrains */
    private fun layerOfResource(resource: TileResource): MapLayer? {
        val layers = resource.terrainsCanBeFoundOn.mapNotNull { ruleset.terrains[it]?.mapLayer }.distinct()
        return layers.singleOrNull()?.takeIf { it != MapLayer.Surface }
    }

    private fun layerMap(layer: MapLayer) = if (layer == MapLayer.Underground) underground else sky

    /** 5.3.4: Heart of the Mountain on a Cave under a Mountain, Celestial Throne on an island of 5+ tiles */
    private fun placeNaturalWonders(random: Random) {
        if (terrainExists(LayerTerrains.heartOfTheMountain)) {
            val candidates = underground.values.filter { it.baseTerrain == LayerTerrains.cave &&
                it.surfaceTile().allTerrains.any { terrain -> terrain.isMountain } }
            candidates.randomOrNull(random)?.let { placeWonder(it, LayerTerrains.heartOfTheMountain) }
        }
        if (terrainExists(LayerTerrains.celestialThrone)) {
            val island = islands.filter { it.size >= 5 }.randomOrNull(random)
            island?.randomOrNull(random)?.let { placeWonder(it, LayerTerrains.celestialThrone) }
        }
    }

    private fun placeWonder(tile: Tile, wonder: String) {
        val terrain = ruleset.terrains[wonder]!!
        tile.naturalWonder = wonder
        if (terrain.turnsInto != null) tile.baseTerrain = terrain.turnsInto!!
        tile.setTerrainFeatures(listOf())
        tile.tileResource = null
        tile.setTerrainTransients()
    }

    /** 5.3.5: ancient ruins on each layer, half as many as on the Surface */
    private fun placeRuins(random: Random) {
        if (surface.mapParameters.noRuins) return
        val surfaceRuins = surface.values.count { it.tileImprovement?.isAncientRuinsEquivalent() == true }
        val ruins = ruleset.tileImprovements.values.firstOrNull { it.isAncientRuinsEquivalent() } ?: return
        for (map in listOf(underground, sky)) {
            val candidates = map.values.filter { !it.isImpassible() && it.resource == null && it.naturalWonder == null }.shuffled(random)
            val placed = ArrayList<Tile>()
            for (tile in candidates) {
                if (placed.size >= surfaceRuins / 2) break
                if (placed.any { it.aerialDistanceTo(tile) < 4 }) continue
                tile.setImprovementBasic(ruins)
                placed += tile
            }
        }
    }

    //endregion
    //region 5.4 Fair starts

    /**
     * 5.4: within 5 tiles of every major start there must be a Cave Entrance and an Updraft, and within 4 tiles passable groups
     * of 8+ tiles in the Underground and the Sky. What is missing is created; the number of fixes goes to [Report.fairnessFixes].
     */
    fun ensureFairStarts(starts: Collection<Tile>) {
        for (start in starts) {
            ensurePassableGroup(start, MapLayer.Underground, LayerTerrains.cave)
            ensurePassableGroup(start, MapLayer.Sky, LayerTerrains.clouds)
            ensureTransition(start, LayerTerrains.caveEntrance, MapLayer.Underground, ::canHaveCaveEntrance)
            ensureTransition(start, LayerTerrains.updraft, MapLayer.Sky, ::canHaveUpdraft)
        }
        debug("LayerMapGenerator: %d start fixes", report.fairnessFixes)
    }

    private fun ensurePassableGroup(start: Tile, layer: MapLayer, passableTerrain: String) {
        if (!terrainExists(passableTerrain)) return
        val center = start.layerTile(layer) ?: return
        if (largestGroupInRadius(center, 4) >= 8) return
        report.fairnessFixes++
        // Grow a passable group from the tile of the column: convert the nearest impassable tiles until 8 are connected
        val group = HashSet<Tile>()
        val queue = ArrayDeque<Tile>()
        queue += center
        while (group.size < 8 && queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            if (tile in group) continue
            if (tile.aerialDistanceTo(center) > 4) continue
            if (tile.isImpassible() || tile.isNaturalWonder()) {
                if (tile.isNaturalWonder()) continue
                tile.setTerrainFeatures(tile.terrainFeatures.filter { ruleset.terrains[it]?.isOverlayFeature == true })
                tile.setBase(passableTerrain)
            }
            group += tile
            queue.addAll(tile.neighbors.sortedBy { it.aerialDistanceTo(center) })
        }
    }

    /** Size of the biggest connected passable group counted within [radius] of [center] */
    private fun largestGroupInRadius(center: Tile, radius: Int): Int {
        val inRadius = ArrayList<Tile>()
        center.forEachTileInDistance(radius) { if (it.isLayerPassable()) inRadius += it }
        val seen = HashSet<Tile>()
        var best = 0
        for (tile in inRadius) {
            if (tile in seen) continue
            val bfs = BFS(tile) { it.isLayerPassable() && it.aerialDistanceTo(center) <= radius }
            bfs.stepToEnd()
            val reached = bfs.getReachedTiles()
            seen += reached
            best = maxOf(best, reached.size)
        }
        return best
    }

    private fun ensureTransition(start: Tile, feature: String, layer: MapLayer, suitable: (Tile) -> Boolean) {
        if (!terrainExists(feature)) return
        var present = false
        start.forEachTileInDistance(5) { if (feature in it.terrainFeatures) present = true }
        if (present) return
        report.fairnessFixes++
        // Nearest suitable tile above/below a passable tile; the start tile itself only as the last resort
        val candidates = ArrayList<Tile>()
        start.forEachTileInDistance(5) { candidates += it }
        val chosen = candidates.sortedWith(compareBy<Tile> { it == start }.thenBy { it.aerialDistanceTo(start) })
            .firstOrNull { suitable(it) && it.layerTile(layer)!!.isLayerPassable() }
            ?: candidates.sortedWith(compareBy<Tile> { it == start }.thenBy { it.aerialDistanceTo(start) })
                .firstOrNull { it.isLand && !it.isImpassible() && !it.isNaturalWonder() }
            ?: return
        val below = chosen.layerTile(layer)!!
        if (!below.isLayerPassable() && !below.isNaturalWonder())
            below.setBase(if (layer == MapLayer.Underground) LayerTerrains.cave else LayerTerrains.clouds)
        chosen.setTerrainFeatures(chosen.terrainFeatures.filter { ruleset.terrains[it]?.isHill == true })
        chosen.addTerrainFeature(feature)
    }

    //endregion

    private fun passableShareOverLand(layer: MapLayer): Float {
        val map = layerMap(layer)
        val overLand = map.values.filter { it.surfaceTile().isLand }
        if (overLand.isEmpty()) return 0f
        return overLand.count { it.isLayerPassable() }.toFloat() / overLand.size
    }

    companion object {
        /** 5.2 step 2 */
        const val CLOUDS_OVER_LAND = 0.30
        const val CLOUDS_OVER_WATER = 0.40
        /** 5.1, 5.2: the middle of the 40-50% corridor of passable tiles over land */
        const val TARGET_PASSABLE = 0.45

        /** Connected groups of passable tiles of [map] */
        fun passableGroups(map: TileMap): List<List<Tile>> {
            val seen = HashSet<Tile>()
            val groups = ArrayList<List<Tile>>()
            for (tile in map.values) {
                if (tile in seen || tile.isImpassible()) continue
                val bfs = BFS(tile) { !it.isImpassible() }
                bfs.stepToEnd()
                val group = bfs.getReachedTiles().toList()
                seen += group
                groups += group
            }
            return groups
        }

        /** Share of passable tiles of [layer] over land, for the tests (L2) */
        fun passableShareOverLand(surface: TileMap, layer: MapLayer): Float {
            val map = surface.layerMap(layer) ?: return 0f
            val overLand = map.values.filter { it.surfaceTile().isLand }
            if (overLand.isEmpty()) return 0f
            return overLand.count { !it.isImpassible() }.toFloat() / overLand.size
        }
    }
}
