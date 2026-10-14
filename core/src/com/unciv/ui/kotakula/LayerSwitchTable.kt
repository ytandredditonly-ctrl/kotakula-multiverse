package com.unciv.ui.kotakula

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.kotakula.layers.LayerRules
import com.unciv.logic.map.MapLayer
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder

/**
 * Kotakula (6.1): the layer switch under the minimap - "Sky", "Surface", "Underground".
 * A closed layer is a gray button; clicking it tells which tech opens it. PageUp / PageDown do the same as the buttons.
 */
class LayerSwitchTable(private val mapHolder: WorldMapHolder) : Table() {

    private val shownLayers = listOf(MapLayer.Sky, MapLayer.Surface, MapLayer.Underground)

    init {
        defaults().pad(2f).growX()
    }

    fun update(viewingCiv: Civilization?) {
        clearChildren()
        if (!mapHolder.tileMap.hasLayers()) {
            isVisible = false
            return
        }
        isVisible = true
        for (layer in shownLayers) {
            val button = layer.name.toTextButton()
            val isOpen = viewingCiv == null || viewingCiv.isSpectator() || LayerRules.hasAccess(viewingCiv, layer)
            when {
                layer == mapHolder.activeLayer -> button.color = Color.GOLD
                !isOpen -> {
                    button.color = Color.GRAY
                    val tech = LayerRules.unlockingTech(viewingCiv!!, layer)
                    val hint = if (tech != null) "Opens with [$tech]".tr() else layer.name.tr()
                    button.addTooltip(hint, 18f)
                    button.onClick { ToastPopup(hint, mapHolder.worldScreen) }
                }
                else -> button.onClick { mapHolder.setActiveLayer(layer) }
            }
            add(button)
        }
        pack()
    }

    companion object {
        /** PageUp / PageDown: the next layer in [up] direction, if it exists */
        fun step(mapHolder: WorldMapHolder, up: Boolean, viewingCiv: Civilization?) {
            val target = (if (up) mapHolder.activeLayer.up() else mapHolder.activeLayer.down()) ?: return
            if (viewingCiv != null && !viewingCiv.isSpectator() && !LayerRules.hasAccess(viewingCiv, target)) return
            mapHolder.setActiveLayer(target)
        }
    }
}
