package com.unciv.logic.map

import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/**
 * Kotakula (KL-01): a layer of the three-layer map. The order matters: "up" is ordinal + 1.
 *
 * The [Surface] is the classic Unciv map ([com.unciv.logic.GameInfo.tileMap]); [Underground] and [Sky]
 * are maps of the same size with the same coordinates. Three tiles with one position form a column.
 */
enum class MapLayer(
    /** Sorting of the tiles of a column and of the [com.unciv.logic.map.tile.Tile.globalIndex]: Surface first, so vanilla indices stay */
    val indexOrder: Int
) {
    Underground(1),
    Surface(0),
    Sky(2);

    @Pure fun up(): MapLayer? = entries.getOrNull(ordinal + 1)
    @Pure fun down(): MapLayer? = entries.getOrNull(ordinal - 1)

    companion object {
        /** Layers in the order of [MapLayer.indexOrder] */
        val byIndexOrder = entries.sortedBy { it.indexOrder }

        /** The layers besides the [Surface] */
        val extraLayers = listOf(Underground, Sky)

        @Pure
        fun fromString(name: String?): MapLayer? = entries.firstOrNull { it.name == name }

        /** Distance between the key spaces of the layers, far beyond any map coordinate */
        private const val KEY_OFFSET = 20000

        /**
         * A position that also says the layer: the surface position itself, or the position moved far away for the other layers.
         * Used where the engine stores positions (worked tiles, unit destinations, notifications) and must not confuse
         * a tile of the surface with the tile of the same column on another layer.
         */
        @Pure
        fun toKey(position: HexCoord, layer: MapLayer): HexCoord =
            if (layer == Surface) position
            else HexCoord(position.x + layer.indexOrder * KEY_OFFSET, position.y + layer.indexOrder * KEY_OFFSET)

        @Pure
        fun layerOfKey(key: HexCoord): MapLayer {
            return when (Math.floorDiv(key.x + KEY_OFFSET / 2, KEY_OFFSET)) {
                1 -> Underground
                2 -> Sky
                else -> Surface
            }
        }

        @Pure
        fun positionOfKey(key: HexCoord): HexCoord {
            val order = layerOfKey(key).indexOrder
            if (order == 0) return key
            return HexCoord(key.x - order * KEY_OFFSET, key.y - order * KEY_OFFSET)
        }

        @Readonly
        fun isLayerKey(key: HexCoord) = layerOfKey(key) != Surface
    }
}
