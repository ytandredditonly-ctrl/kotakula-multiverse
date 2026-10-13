package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.TechManager
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.objectdescriptions.TechnologyDescriptions
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Concurrency
import yairm210.purity.annotations.Readonly
import kotlin.math.abs


class TechPickerScreen(
    internal val civInfo: Civilization,
    centerOnTech: Technology? = null,
) : PickerScreen() {

    private val freeTechPick: Boolean = civInfo.tech.freeTechs != 0
    private val ruleset = civInfo.gameInfo.ruleset
    private var techNameToButton = HashMap<String, TechButton>()
    private var selectedTech: Technology? = null
    private var civTech: TechManager = civInfo.tech
    private var tempTechsToResearch: ArrayList<String>
    private var lines = NonTransformGroup().apply { touchable = Touchable.disabled }
    private var orderIndicators = NonTransformGroup().apply { touchable = Touchable.disabled }
    private var eraLabels = ArrayList<Label>()
    /** Kotakula: the grid, kept to route the connecting lines through free lanes (see [addConnectingLines]) */
    private var techMatrix = arrayOf<Array<Technology?>>()
    private var cellTables = arrayOf<Array<Table?>>()

    /** We need this to be a separate table, and NOT the topTable, because *inhales*
     * When call setConnectingLines we need to pack() the table so that the lines will align correctly, BUT
     *  this causes the table to be SMALLER THAN THE SCREEN for small tech trees from mods,
     *  meaning the tech tree is in a crumpled heap at the lower-left corner of the screen
     * Having this be a separate table allows us to leave the TopTable as is (that is: auto-width to fit the scrollPane)
     *  leaving us the juicy small tech tree right in the center.
     */
    private val techTable = object : Table(){
        override fun draw(batch: Batch?, parentAlpha: Float) {
            cullingArea = topTable.cullingArea
            super.draw(batch, parentAlpha)
        }
    }

    // All these are to counter performance problems when updating buttons for all techs.
    private var researchableTechs = ruleset.technologies.keys
            .filter { civTech.canBeResearched(it) }.toHashSet()

    private val currentTechColor = skinStrings.getUIColor("TechPickerScreen/CurrentTechColor", colorFromRGB(72, 147, 175))
    private val researchedTechColor = skinStrings.getUIColor("TechPickerScreen/ResearchedTechColor", colorFromRGB(255, 215, 0))
    private val researchableTechColor = skinStrings.getUIColor("TechPickerScreen/ResearchableTechColor", colorFromRGB(28, 170, 0))
    private val queuedTechColor = skinStrings.getUIColor("TechPickerScreen/QueuedTechColor", colorFromRGB(7*2, 46*2, 43*2))
    private val researchedFutureTechColor = skinStrings.getUIColor("TechPickerScreen/ResearchedFutureTechColor", colorFromRGB(127, 50, 0))

    private val turnsToTech = ruleset.technologies.values.associateBy({ it.name }, { civTech.turnsToTech(it.name) })

    init {
        Gdx.input.inputProcessor = null // Avoid ANRs while building the tech screen

        setDefaultCloseAction()
        scrollPane.setOverscroll(false, false)

        descriptionLabel.onActivation {
            if (selectedTech != null)
                openCivilopedia(selectedTech!!.makeLink())
        }
        descriptionLabel.keyShortcuts.add(KeyboardBinding.Civilopedia)

        tempTechsToResearch = ArrayList(civTech.techsToResearch)

        createTechTable()
        setButtonsInfo()
        techTable.addActor(lines)
        techTable.addActor(orderIndicators)
        topTable.add(techTable)
        techTable.background = skinStrings.getUiBackground("TechPickerScreen/Background", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.bottomTable.background = skinStrings.getUiBackground("TechPickerScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)

        rightSideButton.setText(if (freeTechPick) "Pick a free tech".tr() else "Pick a tech".tr())
        rightSideButton.onClick(UncivSound.Paper) { tryExit() }
        // Kotakula: the bottom pane keeps one size whatever the description and the button say, otherwise the
        // split moves on every selection and the whole tree jumps up and down
        pickerPane.bottomTable.getCell(descriptionScroll)?.height(130f)
        pickerPane.bottomTable.getCell(pickerPane.rightSideGroup)?.minWidth(360f)

        // per default show current/recent technology,
        // and possibly select it to show description,
        // which is very helpful when just discovered and clicking the notification
        val tech = centerOnTech ?: civInfo.tech.currentTechnology()
        if (tech != null) {
            // select only if there it doesn't mess up tempTechsToResearch
            if (civInfo.tech.isResearched(tech.name) || civInfo.tech.techsToResearch.size <= 1)
                selectTechnology(tech, queue = false, center = true)
            else centerOnTechnology(tech)
        } else {
            // center on any possible technology which is ready for the research right now
            val firstAvailable = researchableTechs.firstOrNull()
            val firstAvailableTech = ruleset.technologies[firstAvailable]
            if (firstAvailableTech != null)
                centerOnTechnology(firstAvailableTech)
        }
        Gdx.input.inputProcessor = stage // Return input 
    }

    override fun getCivilopediaRuleset() = ruleset


    private fun tryExit() {
        if (freeTechPick) {
            val freeTech = selectedTech!!.name
            // More evil people fast-clicking to cheat - #4977
            if (!researchableTechs.contains(freeTech)) return
            civTech.getFreeTechnology(selectedTech!!.name)
        }
        else civTech.techsToResearch = tempTechsToResearch

        civTech.updateResearchProgress()

        game.settings.addCompletedTutorialTask("Pick technology")

        game.popScreen()
    }

    private fun createTechTable() {

        for (label in eraLabels) label.remove()
        eraLabels.clear()

        // Computed once for all techs instead of per-TechButton - see #15641
        val techIconsIndex = TechnologyDescriptions.buildTechIconsIndex(ruleset, civInfo)

        // Kotakula (KC-09): the other faction's techs are not shown at all
        val allTechs = ruleset.technologies.values.filter { com.unciv.logic.kotakula.FactionRules.canUse(civInfo, it) }
        if (allTechs.isEmpty()) return
        val columns = allTechs.maxOf { it.column!!.columnNumber } + 1
        val rows = allTechs.maxOf { it.row } + 1
        techMatrix = Array<Array<Technology?>>(columns) { arrayOfNulls(rows) } // Divided into columns, then rows
        cellTables = Array(columns) { arrayOfNulls<Table>(rows) }

        for (technology in allTechs) {
            techMatrix[technology.column!!.columnNumber][technology.row - 1] = technology
        }

        val erasNamesToColumns = LinkedHashMap<String, ArrayList<Int>>()
        for (tech in allTechs) {
            val era = tech.era()
            if (!erasNamesToColumns.containsKey(era)) erasNamesToColumns[era] = ArrayList()
            val columnNumber = tech.column!!.columnNumber
            if (!erasNamesToColumns[era]!!.contains(columnNumber)) erasNamesToColumns[era]!!.add(columnNumber)
        }
        for ((era, eraColumns) in erasNamesToColumns) {
            val columnSpan = eraColumns.size
            val color = when {
                civTech.era.name == era -> queuedTechColor
                ruleset.eras[era]!!.eraNumber < civTech.era.eraNumber -> colorFromRGB(255, 175, 0)
                else -> ImageGetter.CHARCOAL.cpy()
            }

            val table1 = Table().pad(1f)
            val table2 = Table()

            table1.background = skinStrings.getUiBackground("General/Border", tintColor = Color.WHITE)
            table2.background = skinStrings.getUiBackground("General/Border", tintColor = color)

            val label = era.toLabel().apply {
                setAlignment(Align.center)
                if (ruleset.eras[era]!!.eraNumber < civTech.era.eraNumber)
                    this.color = colorFromRGB(120, 46, 16) }

            eraLabels.add(label)

            table2.add(label).growX()
            table1.add(table2).growX()

            techTable.add(table1).fill().colspan(columnSpan)
        }

        for (rowIndex in 0 until rows) {

            techTable.row()

            for (columnIndex in techMatrix.indices) {
                val tech = techMatrix[columnIndex][rowIndex]

                val table = Table().pad(2f).padRight(60f).padLeft(20f)
                cellTables[columnIndex][rowIndex] = table
                if (rowIndex == 0)
                    table.padTop(7f)

                if (erasNamesToColumns[civTech.era.name]?.contains(columnIndex) == true)
                    table.background = skinStrings.getUiBackground("TechPickerScreen/Background", tintColor = queuedTechColor.darken(0.5f))

                if (tech == null) {
                    techTable.add(table).fill()
                } else {
                    val techButton = TechButton(tech.name, civTech, false, techIconsIndex)
                    // Kotakula (KC-09): frame color - common grey, Technocrat steel blue, Humanist violet
                    if (ruleset.hasFactions) {
                        val frame = Table()
                        frame.background = skinStrings.getUiBackground("General/Border",
                            tintColor = com.unciv.ui.kotakula.FactionDisplay.getColor(tech.faction))
                        frame.add(techButton).pad(3f)
                        table.add(frame)
                    } else table.add(techButton)
                    techNameToButton[tech.name] = techButton
                    techButton.onClick { selectTechnology(tech, queue = false, center = false) }
                    techButton.onRightClick { selectTechnology(tech, queue = true, center = false) }
                    techButton.onDoubleClick(UncivSound.Paper) { tryExit() }
                    techTable.add(table).fillX()
                }
            }
        }
    }

    private fun setButtonsInfo() {
        for ((techName, techButton) in techNameToButton) {
            val isResearched = civTech.isResearched(techName)
            techButton.setButtonColor(when {
                isResearched && ruleset.technologies[techName]?.isContinuallyResearchable() != true -> researchedTechColor
                isResearched -> researchedFutureTechColor
                // if we're here to pick a free tech, show the current tech like the rest of the researchables so it'll be obvious what we can pick
                tempTechsToResearch.firstOrNull() == techName && !freeTechPick -> currentTechColor
                researchableTechs.contains(techName) -> researchableTechColor
                tempTechsToResearch.contains(techName) -> queuedTechColor
                else -> ImageGetter.CHARCOAL.cpy()
            })

            if (isResearched && ruleset.technologies[techName]?.isContinuallyResearchable() != true) {
                techButton.text.color = colorFromRGB(154, 98, 16)
            }

            if (!isResearched || ruleset.technologies[techName]?.isContinuallyResearchable() == true) {
                techButton.turns.setText(turnsToTech[techName] + "${Fonts.turn}".tr())
            }

            techButton.text.setText(techName.tr(true))
        }

        addConnectingLines()

        addOrderIndicators()
    }

    private fun addConnectingLines() {
        techTable.pack() // required for the table to have the button positions set, so topTable.stageToLocalCoordinates will be correct
        scrollPane.updateVisualScroll()

        lines.clear()

        for (eraLabel in eraLabels) {
            val coords = Vector2(0f, 0f)
            eraLabel.localToStageCoordinates(coords)
            techTable.stageToLocalCoordinates(coords)
            val line = ImageGetter.getLine(coords.x-1f, coords.y, coords.x-1f, coords.y - 1000f, 1f)
            line.color = Color.GRAY.cpy().apply { a = 0.6f }
            line.toBack()
            lines.addActor(line)
        }

        // Kotakula (KC-09): the other faction's techs have no buttons on purpose - no lines and no warning for them
        for (tech in ruleset.technologies.values.filter { com.unciv.logic.kotakula.FactionRules.canUse(civInfo, it) }) {
            if (!techNameToButton.containsKey(tech.name)) {
                ToastPopup("Tech ${tech.name} appears to be missing - perhaps two techs have the same row & column", this)
                continue
            }
            val techButton = techNameToButton[tech.name]!!
            for (prerequisite in tech.prerequisites) {
                if (!techNameToButton.containsKey(prerequisite)) {
                    ToastPopup("Tech $prerequisite. prerequisite of ${tech.name}, appears to be missing - perhaps two techs have the same row & column", this)
                    continue
                }
                val prerequisiteButton = techNameToButton[prerequisite]!!
                val techButtonCoords = Vector2(0f, techButton.height / 2)
                techButton.localToStageCoordinates(techButtonCoords)
                techTable.stageToLocalCoordinates(techButtonCoords)

                val prerequisiteCoords = Vector2(prerequisiteButton.width, prerequisiteButton.height / 2)
                prerequisiteButton.localToStageCoordinates(prerequisiteCoords)
                techTable.stageToLocalCoordinates(prerequisiteCoords)

                val lineColor = when {
                    civTech.isResearched(tech.name) && !tech.isContinuallyResearchable() -> Color.WHITE.cpy()
                    civTech.isResearched(prerequisite) -> researchableTechColor
                    tempTechsToResearch.contains(tech.name) -> currentTechColor
                    else -> Color.WHITE.cpy()
                }

                val lineSize = when {
                    tempTechsToResearch.contains(tech.name) && !civTech.isResearched(prerequisite) -> 4f
                    else -> 2f
                }

                // Kotakula: a prerequisite can be several columns away and the grid is shared by both factions,
                // so the vanilla "half way" elbow would cross the cards in between. Route through free lanes instead
                for (segment in routeLine(prerequisite, tech.name, prerequisiteCoords, techButtonCoords)) {
                    val (from, to) = segment
                    val piece = ImageGetter.getWhiteDot().apply {
                        if (from.y == to.y) {
                            width = abs(to.x - from.x) + lineSize
                            height = lineSize
                            x = minOf(from.x, to.x) - lineSize / 2
                            y = from.y - lineSize / 2
                        } else {
                            width = lineSize
                            height = abs(to.y - from.y) + lineSize
                            x = from.x - lineSize / 2
                            y = minOf(from.y, to.y) - lineSize / 2
                        }
                        color = lineColor
                    }
                    lines.addActor(piece)
                }
            }
        }

        lines.children.filter { it.color == currentTechColor && it.color != Color.WHITE.cpy() }
            .forEach { it.toFront() }

        lines.setSize(techTable.width, techTable.height)
    }

    /**
     * Kotakula: axis-aligned segments from the right edge of [prerequisite] ([start]) to the left edge of [tech] ([end]).
     * Vertical parts run in the empty gap between two columns (the padding of the cells); a horizontal part crossing
     * other columns uses the prerequisite row, the tech row or the nearest row that has no card in those columns.
     */
    private fun routeLine(prerequisite: String, tech: String, start: Vector2, end: Vector2): List<Pair<Vector2, Vector2>> {
        val from = ruleset.technologies[prerequisite]!!
        val to = ruleset.technologies[tech]!!
        val fromColumn = from.column!!.columnNumber
        val toColumn = to.column!!.columnNumber
        val segments = ArrayList<Pair<Vector2, Vector2>>()
        fun segment(a: Vector2, b: Vector2) { if (a.x != b.x || a.y != b.y) segments += a to b }
        // Middle of the 80 px gap after a column: 60 px right padding of its cells + 20 px left padding of the next
        fun laneAfter(column: Int): Float {
            val cell = cellTables[column].firstOrNull { it != null } ?: return start.x + 10f
            return cell.x + cell.width - 20f
        }
        fun rowCenter(row: Int): Float {
            val cell = cellTables[fromColumn][row] ?: return start.y
            return cell.y + cell.height / 2
        }

        if (toColumn <= fromColumn + 1) {
            val lane = if (toColumn > fromColumn) laneAfter(fromColumn) else (start.x + end.x) / 2
            segment(start, Vector2(lane, start.y))
            segment(Vector2(lane, start.y), Vector2(lane, end.y))
            segment(Vector2(lane, end.y), end)
            return segments
        }

        val fromRow = from.row - 1
        val toRow = to.row - 1
        val between = (fromColumn + 1) until toColumn
        fun isFree(row: Int) = between.all { techMatrix[it].getOrNull(row) == null }
        val row = listOf(fromRow, toRow).firstOrNull { isFree(it) }
            ?: techMatrix[fromColumn].indices.filter { isFree(it) }.minByOrNull { abs(it - fromRow) + abs(it - toRow) }
            ?: fromRow
        val y = when (row) {
            fromRow -> start.y
            toRow -> end.y
            else -> rowCenter(row)
        }
        val laneOut = laneAfter(fromColumn)
        val laneIn = laneAfter(toColumn - 1)
        segment(start, Vector2(laneOut, start.y))
        segment(Vector2(laneOut, start.y), Vector2(laneOut, y))
        segment(Vector2(laneOut, y), Vector2(laneIn, y))
        segment(Vector2(laneIn, y), Vector2(laneIn, end.y))
        segment(Vector2(laneIn, end.y), end)
        return segments
    }

    private fun addOrderIndicators() {
        orderIndicators.clear()
        for ((techName, techButton) in techNameToButton) {
            val techButtonCoords = Vector2(0f, techButton.height / 2)
            techButton.localToStageCoordinates(techButtonCoords)
            techTable.stageToLocalCoordinates(techButtonCoords)
            if (tempTechsToResearch.contains(techName) && tempTechsToResearch.size > 1) {
                val index = tempTechsToResearch.indexOf(techName) + 1
                val orderIndicator = index.tr().toLabel(fontSize = 18)
                    .apply { setAlignment(Align.center) }
                    .surroundWithCircle(28f, color = skinStrings.skinConfig.baseColor)
                    .surroundWithCircle(30f,false)
                    .apply { setPosition(techButtonCoords.x - width, techButtonCoords.y - height / 2) }
                orderIndicators.addActor(orderIndicator)
            }
        }
        orderIndicators.toFront()
        orderIndicators.setSize(techTable.width, techTable.height)
    }

    private fun selectTechnology(tech: Technology?, queue: Boolean = false, center: Boolean = false, switchFromWorldScreen: Boolean = true) {

        val previousSelectedTech = selectedTech
        selectedTech = tech
        descriptionLabel.setText(tech?.getDescription(civInfo))

        if (!switchFromWorldScreen)
            return

        if (tech == null)
            return

        // center on technology
        if (center) centerOnTechnology(tech)

        if (freeTechPick) {
            selectTechnologyForFreeTech(tech)
            setButtonsInfo()
            return
        }

        if (civInfo.gameInfo.gameParameters.godMode && !civInfo.tech.isResearched(tech.name)
                && selectedTech == previousSelectedTech) {
            civInfo.tech.addTechnology(tech.name)
        }

        if (civTech.isResearched(tech.name) && !tech.isContinuallyResearchable()) {
            rightSideButton.setText("Pick a tech".tr())
            rightSideButton.disable()
            setButtonsInfo()
            return
        }

        if (!GUI.isAllowedChangeState()) {
            rightSideButton.disable()
            return
        }

        val pathToTech = civTech.getRequiredTechsToDestination(tech)
        for (requiredTech in pathToTech) {
            val unavailableUniques = requiredTech.uniqueObjects.filter {
                it.type == UniqueType.OnlyAvailable && !it.conditionalsApply(civInfo.state) ||
                    it.type == UniqueType.Unavailable && it.conditionalsApply(civInfo.state)
            }
            for (unique in unavailableUniques) {
                rightSideButton.setText(unique.getDisplayText().tr())
                rightSideButton.disable()
                return
            }
        }

        if (queue){
            for (pathTech in pathToTech) {
                if (pathTech.name !in tempTechsToResearch) {
                    tempTechsToResearch.add(pathTech.name)
                }
            }
        }else{
            tempTechsToResearch.clear()
            tempTechsToResearch.addAll(pathToTech.map { it.name })
        }

        if (tempTechsToResearch.any()) {
            val label = "Research [${tempTechsToResearch[0]}]".tr()
            val techProgression = getTechProgressLabel(tempTechsToResearch)
            pick("${label}\n${techProgression}")
        } else {
            rightSideButton.setText("Unavailable".tr())
            rightSideButton.disable()
        }
        setButtonsInfo()
    }

    @Readonly
    private fun getTechProgressLabel(techs: List<String>): String {
        val progress = techs.sumOf { tech -> civTech.researchOfTech(tech) } + civTech.getOverflowScience()
        val techCost = techs.sumOf { tech -> civInfo.tech.costOfTech(tech) }
        return "(${progress}/${techCost})"
    }

    private fun centerOnTechnology(tech: Technology) {
        Concurrency.runOnGLThread {
            techNameToButton[tech.name]?.parent?.let {
                scrollPane.scrollTo(it.x, it.y, it.width, it.height, true, true)
                scrollPane.updateVisualScroll()
            }
        }
    }

    private fun selectTechnologyForFreeTech(tech: Technology) {
        if (researchableTechs.contains(tech.name)) {
            val label = "Pick [${tech.name}] as free tech".tr()
            val techProgression = getTechProgressLabel(listOf(tech.name))
            pick("${label}\n${techProgression}")
        } else {
            rightSideButton.setText("Pick a free tech".tr())
            rightSideButton.disable()
        }
    }
}
