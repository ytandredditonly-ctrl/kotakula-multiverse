package com.unciv.logic.kotakula

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * KC-07: resources of the other faction (section 3.1).
 *
 * - Invisible and unusable before the reveal tech.
 * - After the reveal tech ([UniqueType.KMRevealsOpposingResources]): drawn half-transparent, still unusable.
 * - An improvement that works such a resource does not work for that civilization (as if pillaged).
 *
 * "Usable" itself is decided by [Civilization.canSeeResource], which checks [FactionRules.canUse].
 */
object FactionResources {

    /** Is [resource] a resource of a faction [civ] cannot use? */
    @Readonly
    fun isForeign(civ: Civilization, resource: TileResource?): Boolean =
        resource != null && !FactionRules.canUse(civ, resource.faction)

    /** Can [civ] see (but not use) the foreign [resource] on the map? */
    @Readonly
    fun canSeeForeignResource(civ: Civilization, resource: TileResource?): Boolean =
        isForeign(civ, resource) && civ.hasUnique(UniqueType.KMRevealsOpposingResources)

    /** Does [improvement] on [tile] only serve a resource that is foreign to [civ]? Such an improvement gives [civ] nothing. */
    @Readonly
    fun isImprovementDisabled(tile: Tile, improvement: TileImprovement, civ: Civilization): Boolean {
        val resource = tile.tileResource ?: return false
        if (!isForeign(civ, resource)) return false
        return resource.isImprovedBy(improvement.name) || !FactionRules.canUse(civ, improvement.faction)
    }

    /** Can [civ] not build [improvement] on [tile] because of a foreign resource? (section 3.1, test T4) */
    @Readonly
    fun forbidsImprovement(tile: Tile, improvement: TileImprovement, civ: Civilization): Boolean {
        if (improvement.isRoad()) return false
        val resource = tile.tileResource ?: return false
        return isForeign(civ, resource) && resource.isImprovedBy(improvement.name)
    }
}
