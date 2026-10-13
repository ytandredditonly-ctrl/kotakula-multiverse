package com.unciv.dev.kotakula

import java.io.File

/**
 * Reads the tables of the Kotakula Multiverse spec (docs/kotakula/spec.md), so that names, numbers and texts
 * go into the mod exactly as written in the spec.
 */
class SpecDoc(file: File) {
    val lines: List<String> = file.readLines(Charsets.UTF_8)

    /** A table row: header cell to value cell */
    class Row(private val cells: Map<String, String>, val raw: List<String>) {
        operator fun get(header: String): String =
            cells[header] ?: throw IllegalArgumentException("No column '$header' in ${cells.keys}")
        fun getOrNull(header: String): String? = cells[header]
        val headers get() = cells.keys
        override fun toString() = raw.joinToString(" | ")
    }

    /** Index of the first line starting with [headingPrefix] */
    fun headingIndex(headingPrefix: String): Int {
        val index = lines.indexOfFirst { it.startsWith(headingPrefix) }
        require(index >= 0) { "Heading '$headingPrefix' not found in spec" }
        return index
    }

    /** The [nth] table (0-based) in the section that starts with [headingPrefix] */
    fun table(headingPrefix: String, nth: Int = 0): List<Row> {
        var index = headingIndex(headingPrefix) + 1
        var tableNumber = -1
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("## ") || line.startsWith("### ")) break
            if (line.startsWith("|")) {
                tableNumber++
                val tableLines = lines.drop(index).takeWhile { it.startsWith("|") }
                if (tableNumber == nth) return parseTable(tableLines)
                index += tableLines.size
                continue
            }
            index++
        }
        throw IllegalArgumentException("Table $nth not found under '$headingPrefix'")
    }

    /** Plain text paragraph lines of a section (no tables, no headings) */
    fun sectionText(headingPrefix: String): List<String> {
        val result = ArrayList<String>()
        var index = headingIndex(headingPrefix) + 1
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("## ") || line.startsWith("### ")) break
            if (!line.startsWith("|") && line.isNotBlank()) result += line
            index++
        }
        return result
    }

    private fun parseTable(tableLines: List<String>): List<Row> {
        val header = splitCells(tableLines[0])
        return tableLines.drop(2).map { line ->
            val cells = splitCells(line)
            Row(header.indices.associate { header[it] to cells.getOrElse(it) { "" } }, cells)
        }
    }

    private fun splitCells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { unescape(it.trim()) }

    companion object {
        fun unescape(text: String) = text.replace("\\[", "[").replace("\\]", "]")

        private val backtickRegex = Regex("`([^`]*)`")

        /** All `code` segments of a cell, e.g. uniques */
        fun backticked(cell: String): List<String> = backtickRegex.findAll(cell).map { it.groupValues[1].trim() }.toList()

        /** The ID of a cell like "`Workshop`" or "`First Calculator` Первый Калькулятор" */
        fun id(cell: String): String = backticked(cell).firstOrNull() ?: cell.trim()

        /** The text of a cell without its `code` segments, e.g. the Russian name after an ID */
        fun withoutBackticks(cell: String): String =
            backtickRegex.replace(cell, "").trim().trim(',', '.', ';', ' ').replace(Regex("\\s{2,}"), " ")

        /** "—" or empty means no value */
        fun isEmpty(cell: String) = cell.isBlank() || cell == "—" || cell == "-"

        /** Splits a list cell like "Колесо, Бронзовое литьё" */
        fun list(cell: String): List<String> =
            if (isEmpty(cell)) emptyList() else cell.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /** Strips «» quotes */
        fun unquote(text: String) = text.trim().removePrefix("«").removeSuffix("»").trim()
    }
}
