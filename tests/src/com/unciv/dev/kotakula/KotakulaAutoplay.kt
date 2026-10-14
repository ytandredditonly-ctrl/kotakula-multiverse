package com.unciv.dev.kotakula

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessFiles
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.kotakula.KotakulaTestSupport
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.kotakula.Faction
import com.unciv.logic.kotakula.FactionVictory
import com.unciv.logic.kotakula.Summoning
import com.unciv.logic.map.MapSize
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import java.io.File
import java.util.Locale
import kotlin.random.Random

/**
 * Section 14.3: AI-only games on the Kotakula Multiverse ruleset (standard map, 8 civilizations, 16 city-states,
 * Prince, standard speed, all victories) and the balance metrics of the section.
 *
 *     ./gradlew :tests:kotakulaAutoplay -Pkotakula.args="--games 50 --size medium --max-turns 500"
 *
 * Faction settings of the new game screen: `--truce N` (Standard-speed turns), `--reconcilable` (no eternal war),
 * `--world-split`; `--no-layers` plays without the three-layer map.
 *
 * One line per game goes to build/kotakula-autoplay.csv (written as the games finish, so a long run can be watched),
 * the metric table to build/kotakula-autoplay.md.
 */
object KotakulaAutoplay {
    private class GameResult(
        val seed: Long,
        var crashed: String? = null,
        var turns: Int = 0,
        var victoryType: String = "",
        var winnerFaction: String = "",
        var winners: Int = 0,
        /** Average share of summoned units in the Humanist armies, sampled every 10 turns */
        var humanistSummonedShare: Double = Double.NaN,
        /** Turn each civilization completed its 5th policy branch, by faction */
        val fiveBranchTurns: MutableMap<Faction, MutableList<Int>> = mutableMapOf(),
        /** Most branches completed by one civilization of the faction during the game */
        val maxBranches: MutableMap<Faction, Int> = mutableMapOf(),
        /** Three-layer map L13: major civilizations with an opened layer at turn 200 (or the end), and those working a tile of it */
        var layerCivs: Int = 0,
        var layerCivsWorking: Int = 0,
        /** L12: average milliseconds per game turn, save size in bytes and used memory in MB at the end */
        var msPerTurn: Double = Double.NaN,
        var saveBytes: Int = 0,
        var memoryMb: Long = 0,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        var games = 50
        var size = "medium"
        var maxTurns = 500
        var startSeed = 1L
        val parameters = GameParameters() // only the Kotakula faction settings are taken from it
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--games" -> games = args[++i].toInt()
                "--size" -> size = args[++i]
                "--max-turns" -> maxTurns = args[++i].toInt()
                "--seed" -> startSeed = args[++i].toLong()
                "--no-layers" -> com.unciv.logic.kotakula.layers.LayerWorld.disabledForMeasurement = true
                "--truce" -> parameters.kotakulaTruceTurns = args[++i].toInt()
                "--reconcilable" -> parameters.kotakulaIrreconcilable = false
                "--world-split" -> parameters.kotakulaWorldSplit = true
                else -> throw IllegalArgumentException("Unknown argument ${args[i]}")
            }
            i++
        }

        factionSettings = parameters
        Gdx.files = HeadlessFiles()
        val game = UncivGame(true)
        UncivGame.Current = game
        game.settings = GameSettings().apply {
            showTutorials = false
            turnsBetweenAutosaves = 1_000_000
        }
        game.files = UncivFiles(Gdx.files)
        KotakulaTestSupport.ensureLoaded()

        val out = File("../../tests/build").apply { mkdirs() }
        val csv = File(out, "kotakula-autoplay.csv")
        csv.writeText("seed;turns;crash;victory;winnerFaction;winners;humanistSummonedShare;fiveBranchTechnocrat;fiveBranchHumanist\n")
        val results = ArrayList<GameResult>()
        for (n in 0 until games) {
            val result = play(startSeed + n, size, maxTurns)
            results += result
            csv.appendText(listOf(result.seed, result.turns, result.crashed ?: "", result.victoryType, result.winnerFaction,
                result.winners, fmt(result.humanistSummonedShare),
                result.fiveBranchTurns[Faction.Technocrat].orEmpty().joinToString(","),
                result.fiveBranchTurns[Faction.Humanist].orEmpty().joinToString(",")).joinToString(";") + "\n")
            println("Game ${n + 1}/$games seed ${result.seed}: ${result.crashed?.let { "CRASH $it" } ?: "${result.victoryType} ${result.winnerFaction} turn ${result.turns}"}")
        }
        val report = report(results, size, maxTurns)
        File(out, "kotakula-autoplay.md").writeText(report)
        println(report)
        System.out.flush()
        kotlin.system.exitProcess(0)
    }

    private fun play(seed: Long, size: String, maxTurns: Int): GameResult {
        val result = GameResult(seed)
        try {
            val gameInfo = start(seed, size)
            val samples = ArrayList<Double>()
            val startTime = System.currentTimeMillis()
            var layersSampled = false
            while (gameInfo.victoryData == null && gameInfo.turns < maxTurns) {
                gameInfo.nextTurn()
                sample(gameInfo, result, samples)
                if (!layersSampled && gameInfo.turns >= 200) { sampleLayers(gameInfo, result); layersSampled = true }
            }
            if (!layersSampled) sampleLayers(gameInfo, result)
            result.turns = gameInfo.turns
            result.msPerTurn = (System.currentTimeMillis() - startTime).toDouble() / gameInfo.turns.coerceAtLeast(1)
            result.saveBytes = UncivFiles.gameInfoToString(gameInfo, forceZip = false).length
            System.gc()
            result.memoryMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
            if (System.getenv("KOTAKULA_DEBUG") != null) for (civ in majors(gameInfo))
                println("DEBUG ${civ.civName} ${civ.faction}: cities ${civ.cities.size}, policies ${civ.policies.getAdoptedPolicies().size} " +
                    "(${civ.policies.getAdoptedPolicies().sorted()}), culture/turn ${civ.stats.statsForNextTurn.culture}, " +
                    "stored ${civ.policies.storedCulture}/${civ.policies.getCultureNeededForNextPolicy()}, techs ${civ.tech.techsResearched.size}, " +
                    "canAdopt ${civ.policies.canAdoptPolicy()}")
            result.humanistSummonedShare = if (samples.isEmpty()) Double.NaN else samples.average()
            val victory = gameInfo.victoryData
            if (victory != null) {
                result.victoryType = victory.victoryType
                val winners = FactionVictory.getWinners(gameInfo)
                result.winners = winners.size
                result.winnerFaction = winners.map { it.faction.name }.distinct().joinToString("+")
            } else result.victoryType = "none"
        } catch (ex: Throwable) {
            result.crashed = (ex::class.simpleName + ": " + ex.message).replace(';', ',').replace('\n', ' ')
            ex.printStackTrace()
        }
        return result
    }

    /** L13: does every major civilization with an opened layer work at least one tile of it? */
    private fun sampleLayers(gameInfo: GameInfo, result: GameResult) {
        for (civ in majors(gameInfo)) {
            val layers = civ.unlockedLayers.filter { it != com.unciv.logic.map.MapLayer.Surface }
            if (layers.isEmpty()) continue
            result.layerCivs++
            if (civ.cities.any { city -> city.getWorkedTiles().any { it.layer in layers } }) result.layerCivsWorking++
        }
    }

    private fun majors(gameInfo: GameInfo): List<Civilization> =
        gameInfo.civilizations.filter { it.isMajorCiv() && it.isAlive() && !it.isSpectator() }

    private fun sample(gameInfo: GameInfo, result: GameResult, summonedSamples: MutableList<Double>) {
        for (civ in majors(gameInfo)) {
            val completed = civ.policies.completedBranches.size
            result.maxBranches[civ.faction] = maxOf(result.maxBranches[civ.faction] ?: 0, completed)
            val turns = result.fiveBranchTurns.getOrPut(civ.faction) { mutableListOf() }
            if (completed >= 5 && civ.civName !in completedFive) {
                completedFive += civ.civName
                turns += gameInfo.turns
            }
        }
        if (gameInfo.turns % 10 != 0) return
        val army = majors(gameInfo).filter { it.faction == Faction.Humanist }
            .flatMap { civ -> civ.units.getCivUnits().filter { it.isMilitary() }.toList() }
        if (army.isNotEmpty()) summonedSamples += army.count { Summoning.isSummoned(it) }.toDouble() / army.size
    }

    private val completedFive = HashSet<String>()

    private lateinit var factionSettings: GameParameters

    private fun start(seed: Long, size: String): GameInfo {
        completedFive.clear()
        val ruleset = KotakulaTestSupport.ensureLoaded()
        val random = Random(seed)
        val players = ruleset.nations.values.filter { it.isMajorCiv }.shuffled(random).take(8)
            .map { Player(it.name, PlayerType.AI) }.toMutableList()
        players += Player(Constants.spectator, PlayerType.Human)
        val setup = GameSetupInfo()
        setup.gameParameters.apply {
            baseRuleset = ruleset.name
            difficulty = "Prince"
            speed = "Standard"
            numberOfCityStates = 16
            victoryTypes = ArrayList(ruleset.victories.keys)
            this.players = ArrayList(players)
            kotakulaTruceTurns = factionSettings.kotakulaTruceTurns
            kotakulaIrreconcilable = factionSettings.kotakulaIrreconcilable
            kotakulaWorldSplit = factionSettings.kotakulaWorldSplit
        }
        setup.mapParameters.mapSize = when (size) {
            "small" -> MapSize.Small
            "medium" -> MapSize.Medium
            "large" -> MapSize.Large
            else -> throw IllegalArgumentException("Unknown size $size")
        }
        setup.mapParameters.seed = seed
        return GameStarter.startNewGame(setup)
    }

    private fun fmt(value: Double) = if (value.isNaN()) "" else String.format(Locale.ROOT, "%.3f", value)
    private fun pct(part: Int, whole: Int) = if (whole == 0) "—" else String.format(Locale.ROOT, "%.0f%%", 100.0 * part / whole)

    private fun report(results: List<GameResult>, size: String, maxTurns: Int): String {
        val finished = results.filter { it.crashed == null && it.victoryType != "none" }
        val technocratWins = finished.count { "Technocrat" in it.winnerFaction }
        val humanistWins = finished.count { "Humanist" in it.winnerFaction }
        fun avgTurn(type: String) = finished.filter { it.victoryType == type }.map { it.turns }.average()
        val flight = avgTurn("Interuniverse Flight")
        val gates = avgTurn("Gates of Paradise")
        val factionWins = finished.count { it.victoryType == "Faction Victory" }
        val summoned = results.map { it.humanistSummonedShare }.filterNot { it.isNaN() }.average()
        fun avgFive(faction: Faction) = results.flatMap { it.fiveBranchTurns[faction].orEmpty() }.average()
        val fiveT = avgFive(Faction.Technocrat)
        val fiveH = avgFive(Faction.Humanist)
        fun num(value: Double) = if (value.isNaN()) "—" else String.format(Locale.ROOT, "%.0f", value)
        return buildString {
            append("# Kotakula Multiverse: автоигры (раздел 14.3)\n\n")
            append("Партий: ${results.size}, карта $size, 8 цивилизаций, 16 городов-государств, «Принц», стандартная скорость, лимит $maxTurns ходов.\n\n")
            append("| Метрика | Цель | Результат |\n| --- | --- | --- |\n")
            append("| Падения игры | 0 | ${results.count { it.crashed != null }} |\n")
            append("| Доля побед технарей / гумов | 45–55% / 45–55% | ${pct(technocratWins, finished.size)} / ${pct(humanistWins, finished.size)} |\n")
            append("| Средний ход научной победы (полёт / врата) | 330–380, разница ≤ 15 | ${num(flight)} / ${num(gates)} |\n")
            append("| Доля фракционных побед | 20–50% | ${pct(factionWins, finished.size)} |\n")
            append("| Доля призванных юнитов в армии гумов | ≤ 25% | ${if (summoned.isNaN()) "—" else String.format(Locale.ROOT, "%.1f%%", summoned * 100)} |\n")
            append("| Средний ход завершения 5 веток (технари / гумы) | разница ≤ 15 | ${num(fiveT)} / ${num(fiveH)} |\n")
            // Three-layer map (layers.md 6.5: L12, L13)
            val layerCivs = results.sumOf { it.layerCivs }
            append("| L13: цивилизации с открытым слоем, обрабатывающие его клетку (ход 200) | ≥ 80% | ${pct(results.sumOf { it.layerCivsWorking }, layerCivs)} (из $layerCivs) |\n")
            append("| L12: мс на ход / сейв, КБ / память, МБ | см. 6.3 | ${num(results.map { it.msPerTurn }.average())} / " +
                "${num(results.map { it.saveBytes / 1024.0 }.average())} / ${num(results.map { it.memoryMb.toDouble() }.average())} |\n\n")
            append("Типы побед: ")
            append(finished.groupingBy { it.victoryType }.eachCount().entries.joinToString(", ") { "${it.key} — ${it.value}" }.ifEmpty { "—" })
            append(". Без победы до лимита: ${results.count { it.crashed == null && it.victoryType == "none" }}.\n")
            fun avgMax(faction: Faction) = results.mapNotNull { it.maxBranches[faction] }.map { it.toDouble() }.average()
            append("Больше всего завершённых веток у одной цивилизации за партию, в среднем: технари ${num(avgMax(Faction.Technocrat))}, гумы ${num(avgMax(Faction.Humanist))}.\n")
        }
    }
}
