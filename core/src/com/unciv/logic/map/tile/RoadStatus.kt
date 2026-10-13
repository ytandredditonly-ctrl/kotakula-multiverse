package com.unciv.logic.map.tile

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TileImprovement
import yairm210.purity.annotations.Readonly

/**
 * You can use RoadStatus.name to identify [Road] and [Railroad]
 * in string-based identification, as done in [improvement].
 *
 * Note: Order is important, [ordinal] _is_ compared - please interpret as "roadLevel".
 */
enum class RoadStatus(
    val upkeep: Int = 0,
    val movement: Float = 1f,
    val movementImproved: Float = 1f,
    val removeAction: String? = null
) : IsPartOfGameInfoSerialization {

    None,
    Road (1, 0.5f, 1/3f, "Remove Road"),
    Railroad (2, 0.1f, 0.1f, "Remove Railroad");

    /** returns null for [None] */
    @Readonly fun improvement(ruleset: Ruleset) = ruleset.tileImprovements[this.name]

    /** The improvement [civ] builds for this road level - e.g. the Kotakula Humanist "Ley Line" that `replaces` the Railroad */
    @Readonly fun improvement(ruleset: Ruleset, civ: Civilization?): TileImprovement? {
        val base = improvement(ruleset) ?: return null
        return civ?.getEquivalentTileImprovement(base) ?: base
    }

    companion object {
        /** The road level an improvement builds: by name, or by the road it `replaces` (Kotakula "Ley Line"); null if not a road */
        @Readonly
        fun ofImprovement(improvement: TileImprovement): RoadStatus? =
            entries.firstOrNull { it != None && it.name == improvement.name }
                ?: entries.firstOrNull { it != None && it.name == improvement.replaces }
    }
}
