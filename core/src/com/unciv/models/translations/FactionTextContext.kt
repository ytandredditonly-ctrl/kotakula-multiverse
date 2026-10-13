package com.unciv.models.translations

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.kotakula.Faction
import com.unciv.ui.components.fonts.Fonts
import yairm210.purity.annotations.Readonly

/**
 * Kotakula Multiverse (KC-08): the faction of the civilization a human is currently looking through ("viewer").
 *
 * - [Translations.get] first looks for a faction variant of a key: `Science#Humanist = Мана`, then the plain key.
 * - [com.unciv.ui.images.ImageGetter] first looks for `StatIcons/Science#Humanist`, then the plain image.
 * - The font glyphs for Science and Faith are re-rendered from those images when the viewer changes.
 *
 * Spectators, the main menu and loading screens see the plain keys ([Faction.None]).
 * Data of other civilizations is still shown in the viewer's words (section 4.1.5).
 */
object FactionTextContext {

    @Volatile
    var viewerFaction: Faction = Faction.None
        private set

    /** Suffix for translation keys and image names, empty without a viewer faction */
    @Volatile
    var suffix: String = ""
        private set

    /** The faction a viewer [civ] should see texts for */
    @Readonly
    fun factionFor(civ: Civilization?): Faction =
        if (civ == null || civ.isSpectator() || !civ.isMajorCiv()) Faction.None else civ.faction

    /** Does the context need to change to show [civ]'s view? */
    @Readonly
    fun needsUpdateFor(civ: Civilization?): Boolean = factionFor(civ) != viewerFaction

    /** Sets the viewer, e.g. when a WorldScreen is built; null or a spectator resets to plain texts */
    fun setViewer(civ: Civilization?) = setViewerFaction(factionFor(civ))

    fun setViewerFaction(faction: Faction) {
        if (faction == viewerFaction) return
        viewerFaction = faction
        suffix = faction.keySuffix()
        Fonts.onViewerFactionChanged()
    }

    /** For tests: run [action] as if [faction] were viewing */
    fun <T> withViewer(faction: Faction, action: () -> T): T {
        val previous = viewerFaction
        viewerFaction = faction
        suffix = faction.keySuffix()
        try {
            return action()
        } finally {
            viewerFaction = previous
            suffix = previous.keySuffix()
        }
    }
}
