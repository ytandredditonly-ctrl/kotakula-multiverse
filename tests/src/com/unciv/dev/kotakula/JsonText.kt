package com.unciv.dev.kotakula

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import java.io.File

/** Ordered JSON object, as used by the generator */
typealias JObj = LinkedHashMap<String, Any?>

fun jobj(vararg pairs: Pair<String, Any?>): JObj = linkedMapOf(*pairs)

/**
 * Minimal pretty JSON writer and a reader for the lenient vanilla Unciv json files (comments, trailing commas).
 * Values: [Map] (object), [List] (array), [String], [Number], [Boolean], null. Null values in objects are skipped.
 */
object JsonText {

    fun write(value: Any?): String {
        val sb = StringBuilder()
        writeValue(sb, value, 0)
        sb.append('\n')
        return sb.toString()
    }

    private fun indent(sb: StringBuilder, level: Int) { repeat(level) { sb.append('\t') } }

    private fun isSimple(value: Any?) = value == null || value is String || value is Number || value is Boolean

    private fun writeValue(sb: StringBuilder, value: Any?, level: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Float -> sb.append(if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString())
            is Double -> sb.append(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
            is Number, is Boolean -> sb.append(value.toString())
            is Map<*, *> -> {
                val entries = value.entries.filter { it.value != null }
                if (entries.isEmpty()) { sb.append("{}"); return }
                sb.append("{\n")
                entries.forEachIndexed { i, (k, v) ->
                    indent(sb, level + 1)
                    writeString(sb, k.toString())
                    sb.append(": ")
                    writeValue(sb, v, level + 1)
                    if (i < entries.size - 1) sb.append(',')
                    sb.append('\n')
                }
                indent(sb, level)
                sb.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) { sb.append("[]"); return }
                if (value.all { isSimple(it) } && value.sumOf { it.toString().length + 4 } < 100) {
                    sb.append('[')
                    value.forEachIndexed { i, v -> if (i > 0) sb.append(", "); writeValue(sb, v, level) }
                    sb.append(']')
                    return
                }
                sb.append("[\n")
                value.forEachIndexed { i, v ->
                    indent(sb, level + 1)
                    writeValue(sb, v, level + 1)
                    if (i < value.size - 1) sb.append(',')
                    sb.append('\n')
                }
                indent(sb, level)
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Cannot write ${value::class}")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        sb.append('"')
    }

    /** Reads a lenient Unciv json file into Kotlin maps/lists */
    fun read(file: File): Any? = toKotlin(JsonReader().parse(file.readText(Charsets.UTF_8)))
    fun parse(text: String): Any? = toKotlin(JsonReader().parse(text))

    fun readArray(file: File): MutableList<JObj> {
        @Suppress("UNCHECKED_CAST")
        return (read(file) as List<Any?>).map { it as JObj }.toMutableList()
    }

    private fun toKotlin(value: JsonValue): Any? = when {
        value.isObject -> {
            val map = JObj()
            var child = value.child
            while (child != null) { map[child.name] = toKotlin(child); child = child.next }
            map
        }
        value.isArray -> {
            val list = ArrayList<Any?>()
            var child = value.child
            while (child != null) { list += toKotlin(child); child = child.next }
            list
        }
        value.isString -> value.asString()
        value.isDouble -> {
            val d = value.asDouble()
            if (d == Math.floor(d) && !value.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json).contains('.')) d.toLong() else d
        }
        value.isLong -> value.asLong()
        value.isBoolean -> value.asBoolean()
        value.isNull -> null
        else -> value.asString()
    }
}
