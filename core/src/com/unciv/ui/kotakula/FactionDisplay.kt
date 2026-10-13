package com.unciv.ui.kotakula

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.kotakula.Faction
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.surroundWithCircle

/** UI helpers for showing factions (KC-20 colors, section 13.1) */
object FactionDisplay {
    /** Steel blue of the Technocrats (sections 5.2, 13.1) */
    val technocratColor: Color = Color.valueOf("3A6EA5")
    /** Violet of the Humanists (sections 5.2, 13.1) */
    val humanistColor: Color = Color.valueOf("7B4BB7")
    /** Grey frame of common content (section 5.2) */
    val commonColor: Color = Color.valueOf("8C8C8C")

    fun getColor(faction: Faction?): Color = when (faction) {
        Faction.Technocrat -> technocratColor
        Faction.Humanist -> humanistColor
        else -> commonColor
    }

    /** Translation key of the plural display name: "Technocrats" / "Humanists" */
    fun getDisplayName(faction: Faction?): String = when (faction) {
        Faction.Technocrat -> "Technocrats"
        Faction.Humanist -> "Humanists"
        else -> "Common"
    }

    /** Faction icon on a circle of the faction color */
    fun getFactionIcon(faction: Faction, size: Float): IconCircleGroup {
        val image = ImageGetter.getImage("FactionIcons/${faction.name}")
        return image.surroundWithCircle(size, color = getColor(faction))
    }

    /** Icon plus name, used in diplomacy, first contact and Civilopedia */
    fun getFactionBadge(faction: Faction, iconSize: Float = 24f): Actor {
        val table = Table()
        if (faction == Faction.None) return table
        table.add(getFactionIcon(faction, iconSize)).padRight(6f)
        table.add(getDisplayName(faction).toLabel(fontColor = getColor(faction).cpy().lerp(Color.WHITE, 0.35f)))
        return table
    }
}
