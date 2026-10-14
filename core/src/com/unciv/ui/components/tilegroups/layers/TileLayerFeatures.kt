package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.view.CivView
import com.unciv.view.TileView
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.tilegroups.TileGroup
import kotlin.math.atan2


private class RoadImage {
    var roadStatus: RoadStatus = RoadStatus.None
    /** Kotakula (7.4): the layer route the segment is drawn for */
    var routeName: String? = null
    var image: Image? = null
}

class TileLayerFeatures(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private val roadImages = HashMap<TileView, RoadImage>()

    private fun updateRoadImages(viewingCiv: CivView?) {

        if (tileGroup.isForMapEditorIcon)
            return

        val tileView = tileGroup.tileView
        val isTileVisible = viewingCiv == null || viewingCiv.canSeeTile(tileView)

        for (neighbor in tileView.getVisibleNeighbors()) {
            var roadImage = roadImages[neighbor]
            val currentStatus = roadImage?.roadStatus ?: RoadStatus.None
            // Kotakula (7.4): Tunnels, Magic Passages, Air Platforms and Cloud Bridges are drawn like roads, with their own image
            val ownRoute = tileView.layerRouteName()
            val routeName = if (ownRoute != null && neighbor.layerRouteName() != null && (isTileVisible || viewingCiv.canSeeTile(neighbor))) ownRoute else null
            if (roadImage?.routeName != routeName) {
                roadImage?.image?.let { removeOwnedActor(it) }
                roadImage?.image = null
                roadImage?.roadStatus = RoadStatus.None
                roadImage?.routeName = routeName
            }
            if (routeName != null) {
                if (roadImage?.image != null) continue
                if (roadImage == null) roadImage = RoadImage().also { roadImages[neighbor] = it }
                roadImage.routeName = routeName
                roadImage.image = addSegment(routeImage(routeName), tileView, neighbor)
                continue
            }

            val roadStatus = when {
                !isTileVisible && !viewingCiv.canSeeTile(neighbor) -> RoadStatus.None // don't show roads on non-visible tiles
                tileView.roadStatus == RoadStatus.None || neighbor.roadStatus === RoadStatus.None -> RoadStatus.None
                tileView.roadStatus == RoadStatus.Road || neighbor.roadStatus === RoadStatus.Road -> RoadStatus.Road
                else -> RoadStatus.Railroad
            }
            if (currentStatus == roadStatus) continue // the image is correct

            if (roadImage == null) { // create when missing
                roadImage = RoadImage().also { roadImages[neighbor] = it }
                roadImages[neighbor] = roadImage
            }

            roadImage.roadStatus = roadStatus

            if (roadImage.image != null) {
                removeOwnedActor(roadImage.image!!)
                roadImage.image = null
            }
            if (roadStatus == RoadStatus.None) continue // no road image

            roadImage.image = addSegment(strings.orFallback { roadsMap[roadStatus]!! }, tileView, neighbor)
        }

    }

    /** Kotakula (7.4): image of a layer route segment - Roads/<route id> of the tileset or of the Kotakula tileset, else the road */
    private fun routeImage(routeName: String): String =
        listOf("${strings.tileSetLocation}Roads/$routeName", "TileSets/Kotakula/Roads/$routeName")
            .firstOrNull { ImageGetter.imageExists(it) } ?: strings.orFallback { roadsMap[RoadStatus.Road]!! }

    private fun addSegment(imageName: String, tileView: TileView, neighbor: TileView): Image {
        val tileMapView = tileView.getTileMap()
        val image = ImageGetter.getImage(imageName)

        val relativeWorldPosition = tileMapView.getNeighborTilePositionAsWorldCoords(tileView, neighbor)

        // This is some crazy voodoo magic so I'll explain.
        // Roads start at the tile origin; we offset them to the tile centre then toward the neighbor.
        image.setPosition(
            tileX + 25f - relativeWorldPosition.x * 0.8f * 25f,
            tileY + 25f - relativeWorldPosition.y * 0.8f * 25f
        )

        image.setSize(10f, 6f)
        image.setOrigin(0f, 3f) // This is so that the rotation is calculated from the middle of the road and not the edge

        image.rotation = (180 / Math.PI * atan2(relativeWorldPosition.y.toDouble(),relativeWorldPosition.x.toDouble())).toFloat()

        addOwnedActor(image)
        return image
    }

    override fun doUpdate(viewingCiv: CivView?) {
        updateRoadImages(viewingCiv)
    }

    fun dim() {
        forEachOwnedActor { it.color.a = 0.5f }
    }

}
