package com.unciv.models.ruleset

import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine

/**
 * Kotakula (7.3): a biome of `jsons/LayerBiomes.json`. A tile of the Underground or the Sky gets the biome whose [surfaceFilter]
 * matches the Surface tile of its column (the one with the highest [priority]). The biome is kept as a hidden terrain feature of
 * the layer tile ([Terrain.isLayerBiome]) - the engine makes that terrain from this entry, workers cannot remove it.
 */
class LayerBiome : RulesetObject() {
    /** `Underground` or `Sky` */
    var layer = "Underground"

    /**
     * What the Surface tile of the column must be: a tile filter (terrain, feature, `Fallout`...),
     * `City center`, or `Near city with [building]` - a tile within 1 of a city that has the building.
     */
    var surfaceFilter = ""

    /** If several biomes match, the highest priority wins */
    var priority = 0

    override fun getUniqueTarget() = UniqueTarget.Terrain
    override fun makeLink() = "Layers/$name"

    /** The hidden terrain feature standing for this biome on the tiles */
    fun toTerrain(ruleset: Ruleset): Terrain {
        val terrain = Terrain()
        terrain.name = name
        terrain.type = TerrainType.TerrainFeature
        terrain.layer = layer
        terrain.isLayerBiome = true
        terrain.uniques.addAll(uniques)
        terrain.uniques.add(com.unciv.models.ruleset.unique.UniqueType.NoNaturalGeneration.text) // the surface generator must not place biomes
        terrain.civilopediaText = civilopediaText
        terrain.occursOn.addAll(ruleset.terrains.values.filter { it.layer == layer && it.type.isBaseTerrain }.map { it.name })
        terrain.originRuleset = originRuleset
        terrain.setTransients()
        return terrain
    }

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lines = ArrayList<FormattedLine>()
        lines += FormattedLine("Layer: [$layer]")
        lines += FormattedLine("Surface: [$surfaceFilter]")
        uniquesToCivilopediaTextLines(lines)
        return lines
    }
}
