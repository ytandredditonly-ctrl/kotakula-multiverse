package com.unciv.dev.kotakula

/**
 * Section 4.2: rewrites Russian texts for one faction, keeping the grammatical case of every word.
 * Humanists: наука -> мана, научный -> магический, учёный -> чародей, Великий учёный -> Великий архимаг,
 * исследование -> изыскание, исследовать -> постигать, технология -> таинство, Дерево технологий -> Гримуар.
 * Technocrats: вера -> корпоративность, религия -> корпорация, основать религию -> провести IPO, пантеон -> стартап,
 * верования -> директивы, Великий пророк -> Великий CEO, миссионер -> менеджер по продажам, инквизитор -> аудитор,
 * святое место / святой город -> штаб-квартира, религиозное давление -> рыночное давление,
 * обращение в религию -> поглощение, последователи -> акционеры, реформация -> ребрендинг.
 */
object FactionWording {

    private const val L = "\\p{L}"
    /** Word start/end for Cyrillic (\b does not know Cyrillic in Java without UNICODE_CHARACTER_CLASS) */
    private const val START = "(?<![$L])"
    private const val END = "(?![$L])"

    private class Rule(pattern: String, val replace: (MatchResult) -> String) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun matchCase(original: String, replacement: String): String = when {
        original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } -> replacement.uppercase()
        original.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercaseChar() }
        else -> replacement
    }

    /** Rule replacing a stem + ending with a new stem + mapped ending */
    private fun stemRule(stem: String, endings: Map<String, String>, newStem: String, suffix: String = ""): Rule {
        val alternatives = endings.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        return Rule("$START($stem)($alternatives)$END") { m ->
            val ending = m.groupValues[2].lowercase()
            matchCase(m.value, newStem + endings[ending]!! + suffix)
        }
    }

    private fun same(vararg endings: String) = endings.associateWith { it }

    private val adjectiveEndings = mapOf(
        "ый" to "ий", "ий" to "ий", "ая" to "ая", "ое" to "ое", "ые" to "ие", "ие" to "ие", "ого" to "ого", "ой" to "ой",
        "ому" to "ому", "ым" to "им", "им" to "им", "ыми" to "ими", "ими" to "ими", "ых" to "их", "их" to "их", "ую" to "ую", "ом" to "ом",
    )

    private val humanistRules: List<Rule> = listOf(
        Rule("$START(дерев)(о|а|у|ом|е) технологий$END") { m ->
            val ending = mapOf("о" to "", "а" to "а", "у" to "у", "ом" to "ом", "е" to "е")[m.groupValues[2].lowercase()]!!
            matchCase(m.value, "гримуар$ending")
        },
        Rule("${START}технология изучена$END") { m -> matchCase(m.value, "таинство постигнуто") },
        Rule("$START(велик)(ий|ого|ому|им|ом|ие|их|ими) уч[её]н(ый|ого|ому|ым|ом|ые|ых|ыми)$END") { m ->
            val noun = mapOf("ый" to "", "ого" to "а", "ому" to "у", "ым" to "ом", "ом" to "е", "ые" to "и", "ых" to "ов", "ыми" to "ами")
            val adjective = m.groupValues[1] + m.groupValues[2]
            matchCase(m.value, adjective.lowercase() + " архимаг" + noun[m.groupValues[3].lowercase()]!!)
        },
        stemRule("уч[её]н", mapOf("ый" to "й", "ого" to "я", "ому" to "ю", "ым" to "ем", "ом" to "е", "ые" to "и", "ых" to "ев", "ыми" to "ями"), "чароде"),
        stemRule("научн", adjectiveEndings, "магическ"),
        Rule("${START}научно$END") { m -> matchCase(m.value, "магически") },
        stemRule("наук", mapOf("а" to "а", "и" to "ы", "е" to "е", "у" to "у", "ой" to "ой", "ою" to "ою", "ам" to "ам", "ами" to "ами", "ах" to "ах", "" to ""), "ман"),
        stemRule("технологическ", adjectiveEndings + same("ая", "ое", "ую"), "магическ"),
        stemRule("нанотехнологи", mapOf("я" to "о", "и" to "а", "ю" to "о", "ей" to "ом", "й" to "", "ям" to "ам", "ями" to "ами", "ях" to "ах"), "нанотаинств"),
        stemRule("исследовательск", adjectiveEndings + same("ая", "ое", "ую"), "изыскательск"),
        stemRule("технологи", mapOf("я" to "о", "и" to "а", "ю" to "о", "ей" to "ом", "й" to "", "ям" to "ам", "ями" to "ами", "ях" to "ах"), "таинств"),
        stemRule("исследовател", mapOf("ь" to "ь", "я" to "я", "ю" to "ю", "ем" to "ем", "е" to "е", "и" to "и", "ей" to "ей", "ям" to "ям", "ями" to "ями", "ях" to "ях"), "изыскател"),
        stemRule("исследовани", same("е", "я", "ю", "ем", "и", "й", "ям", "ями", "ях"), "изыскани"),
        Rule("${START}исследованн([$L]*)$END") { m -> matchCase(m.value, "постигнут" + m.groupValues[1].lowercase()) },
        stemRule("исследован", same("а", "о", "ы", ""), "постигнут"),
        Rule("${START}исследова(ть|л|ла|ло|ли|в|вший|вшая|вшие)$END") { m -> matchCase(m.value, "постига" + m.groupValues[1].lowercase()) },
        Rule("${START}исследу([$L]*)$END") { m -> matchCase(m.value, "постига" + m.groupValues[1].lowercase()) },
        // "технология" is feminine, "таинство" neuter: fix the adjective right before it
        Rule("$START([$L]+?)([щчжш]?)(ую|ая) таинство$END") { m ->
            val soft = m.groupValues[2].isNotEmpty()
            m.groupValues[1] + m.groupValues[2] + (if (soft) "ее" else "ое") + " таинство"
        },
        Rule("$START([$L]+?)([щчжш]?)(ой|ей) (таинства|таинству|таинством)$END") { m ->
            val soft = m.groupValues[2].isNotEmpty() || m.groupValues[3] == "ей"
            val ending = when (m.groupValues[4].lowercase()) {
                "таинства" -> if (soft) "его" else "ого"
                "таинству" -> if (soft) "ему" else "ому"
                else -> if (soft) "им" else "ым"
            }
            m.groupValues[1] + m.groupValues[2] + ending + " " + m.groupValues[4]
        },
        Rule("${START}(которую|которая) (постига|изуча)") { m -> "которое " + m.groupValues[2] },
    )

    private val technocratRules: List<Rule> = listOf(
        Rule("${START}религиозн(ое|ого|ому|ым|ом) давлени(е|я|ю|ем|и)$END") { m ->
            matchCase(m.value, "рыночн" + m.groupValues[1].lowercase() + " давлени" + m.groupValues[2].lowercase())
        },
        Rule("${START}основать религию$END") { m -> matchCase(m.value, "провести IPO") },
        Rule("${START}основани(е|я|ю|ем|и) религии$END") { m -> matchCase(m.value, "проведени" + m.groupValues[1].lowercase() + " IPO") },
        Rule("${START}основал(а|и|)? религию$END") { m ->
            matchCase(m.value, when (m.groupValues[1].lowercase()) { "а" -> "провела IPO"; "и" -> "провели IPO"; else -> "провёл IPO" })
        },
        Rule("${START}обращени(е|я|ю|ем|и) в религию$END") { m -> matchCase(m.value, "поглощени" + m.groupValues[1].lowercase()) },
        Rule("$START(велик)(ий|ого|ому|им|ом|ие|их|ими) пророк(|а|у|ом|е|и|ов|ам|ами|ах)$END") { m ->
            matchCase(m.value, (m.groupValues[1] + m.groupValues[2]).lowercase() + " CEO")
        },
        stemRule("пророк", mapOf("" to "", "а" to "", "у" to "", "ом" to "", "е" to "", "и" to "", "ов" to "", "ам" to "", "ами" to "", "ах" to ""), "CEO"),
        Rule("${START}свят(ое|ого|ому|ым|ом) мест(о|а|у|ом|е)$END") { m ->
            matchCase(m.value, "штаб-квартир" + mapOf("о" to "а", "а" to "ы", "у" to "е", "ом" to "ой", "е" to "е")[m.groupValues[2].lowercase()]!!)
        },
        Rule("${START}свят(ой|ого|ому|ым|ом) город(|а|у|ом|е)$END") { m ->
            matchCase(m.value, "штаб-квартир" + mapOf("" to "а", "а" to "ы", "у" to "е", "ом" to "ой", "е" to "е")[m.groupValues[2].lowercase()]!!)
        },
        stemRule("религиозн", adjectiveEndings.mapValues { (k, _) -> k }, "корпоративн"),
        stemRule("религи", same("я", "и", "ю", "ей", "й", "ям", "ями", "ях"), "корпораци"),
        stemRule("пантеон", same("", "а", "у", "ом", "е", "ы", "ов", "ам", "ами", "ах"), "стартап"),
        stemRule("веровани", mapOf("е" to "а", "я" to "ы", "ю" to "е", "ем" to "ой", "и" to "е", "й" to "", "ям" to "ам", "ями" to "ами", "ях" to "ах"), "директив"),
        stemRule("миссионер", same("", "а", "у", "ом", "е", "ы", "ов", "ам", "ами", "ах"), "менеджер", " по продажам"),
        stemRule("инквизитор", same("", "а", "у", "ом", "е", "ы", "ов", "ам", "ами", "ах"), "аудитор"),
        stemRule("последовател", mapOf("ь" to "", "я" to "а", "ю" to "у", "ем" to "ом", "е" to "е", "и" to "ы", "ей" to "ов", "ям" to "ам", "ями" to "ами", "ях" to "ах"), "акционер"),
        stemRule("верующ", mapOf("ий" to "", "его" to "а", "ему" to "у", "им" to "ом", "ем" to "е", "ие" to "ы", "их" to "ов", "ими" to "ами"), "акционер"),
        stemRule("реформаци", mapOf("я" to "", "и" to "а", "ю" to "", "ей" to "ом"), "ребрендинг"),
        stemRule("вер", mapOf("а" to "ь", "ы" to "и", "е" to "и", "у" to "ь", "ой" to "ью", "ою" to "ью"), "корпоративност"),
    )

    private fun apply(text: String, rules: List<Rule>): String {
        // Never touch placeholders and markup: [names], {sentences} contents are translated on their own
        val protectedParts = Regex("""\[[^\]]*]""")
        val pieces = ArrayList<Pair<String, Boolean>>() // text, isProtected
        var last = 0
        for (m in protectedParts.findAll(text)) {
            if (m.range.first > last) pieces += text.substring(last, m.range.first) to false
            pieces += m.value to true
            last = m.range.last + 1
        }
        if (last < text.length) pieces += text.substring(last) to false
        return pieces.joinToString("") { (piece, isProtected) ->
            if (isProtected) piece else rules.fold(piece) { acc, rule -> rule.regex.replace(acc) { rule.replace(it) } }
        }
    }

    fun forHumanist(text: String) = apply(text, humanistRules)
    fun forTechnocrat(text: String) = apply(text, technocratRules)

    // Section 14.1: stems that must not stay in a faction's texts (used by the check and the tests)
    val forbiddenForHumanists = Regex("(?i)(наук|уч[её]н(ый|ого|ому|ым|ом|ые|ых|ыми)|исследова|технолог)")
    val forbiddenForTechnocrats = Regex("(?i)($START(вер(а|ы|е|у|ой|ою)$END|верующ|веровани)|религи|пророк|миссионер)")
}
