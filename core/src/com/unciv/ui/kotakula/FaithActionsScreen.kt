package com.unciv.ui.kotakula

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.kotakula.FaithActions
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.FactionAction
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.ui.screens.pickerscreens.PickerPane
import com.unciv.ui.screens.pickerscreens.PickerScreen

/**
 * KC-12: "Corporate initiatives" (Technocrats) / "Prayers and rituals" (Humanists) - section 9.4.
 * Opened from the button next to the Faith counter of the top bar.
 */
class FaithActionsScreen(
    private val civ: Civilization,
    private val canChangeState: Boolean
) : PickerScreen() {

    private var selected: FactionAction? = null

    init {
        setDefaultCloseAction()
        rebuild()
        rightSideButton.onClick(UncivSound.Choir) { startSelected() }
    }

    private fun rebuild() {
        topTable.clear()
        topTable.defaults().pad(5f)

        val header = Table()
        header.add("Faith actions".toLabel(fontSize = 24)).padBottom(5f).row()
        val slotsText = "{Active actions}: ${FaithActions.getUsedSlots(civ).tr()}/${FaithActions.getSlots(civ).tr()}" +
            "   {Discount}: ${FaithActions.getDiscountPercent(civ).tr()}%" +
            "   ${Fonts.faith} ${civ.religionManager.storedFaith.tr()}"
        header.add(slotsText.toLabel())
        topTable.add(header).colspan(2).row()

        if (!FaithActions.areUnlocked(civ))
            topTable.add(FaithActions.Unavailability.Locked.text.toLabel(Color.ORANGE)).colspan(2).row()

        for (action in FaithActions.getActions(civ)) {
            val button = PickerPane.getPickerOptionButton(
                ImageGetter.getImage("FactionActionIcons/${action.name}").apply { color = Color.WHITE },
                getButtonText(action))
            button.onClick { select(action) }
            topTable.add(button).growX().row()
        }
        setRightSideButtonEnabled(false)
        selected?.let { select(it) }
    }

    private fun getButtonText(action: FactionAction): String {
        val cost = FaithActions.getCost(civ, action)
        val state = civ.factionState
        val status = when {
            action.name in state.activeFaithActions -> "{Active}: ${state.activeFaithActions[action.name]!!.tr()}${Fonts.turn}"
            (state.faithActionCooldowns[action.name] ?: 0) > 0 -> "{On cooldown}: ${state.faithActionCooldowns[action.name]!!.tr()}${Fonts.turn}"
            else -> ""
        }
        val duration = if (action.isInstant) "{Instant action}" else "${action.duration.tr()}${Fonts.turn}"
        return "{${action.name}} ($cost${Fonts.faith}, $duration) $status"
    }

    private fun select(action: FactionAction) {
        selected = action
        val lines = ArrayList<FormattedLine>()
        lines += FormattedLine(action.name, header = 3, link = action.makeLink())
        lines += action.getCivilopediaTextLines(civ.gameInfo.ruleset)
        descriptionLabel.setText("")
        descriptionScroll.actor = MarkupRenderer.render(lines, 600f) { openCivilopedia(it) }

        val problem = FaithActions.getUnavailability(civ, action)
        if (problem == null && canChangeState) pick("Start [${action.name}]".tr())
        else {
            rightSideButton.setText((problem?.text ?: "Not your turn").tr())
            rightSideButton.isEnabled = false
        }
    }

    private fun startSelected() {
        val action = selected ?: return
        if (!FaithActions.canStart(civ, action)) return
        if (FaithActions.needsCityState(action)) {
            chooseCityState(action)
            return
        }
        FaithActions.start(civ, action)
        game.popScreen()
    }

    /** "Mergers and acquisitions": the player picks the known city-state */
    private fun chooseCityState(action: FactionAction) {
        val popup = Popup(this)
        popup.addGoodSizedLabel("Choose a city-state").row()
        for (cityState in FaithActions.getChoosableCityStates(civ)) {
            val influence = cityState.getDiplomacyManager(civ)?.getInfluence()?.toInt() ?: 0
            popup.add("{${cityState.civName}} ($influence)".toTextButton().onClick {
                popup.close()
                FaithActions.start(civ, action, cityState)
                game.popScreen()
            }).growX().row()
        }
        popup.addCloseButton()
        popup.open()
    }
}
