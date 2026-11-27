/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reading values out of a service's answer, by a path a module wrote.
 *
 * The path language is intentionally tiny: dotted keys, `[n]` for an index, `*`
 * for "every element of this array". It cannot loop, recurse or allocate beyond
 * the shape of the document it is reading, because every step only ever walks
 * deeper into a value that already exists. A path that names something absent
 * returns null rather than throwing, so a module written against one version of
 * a service degrades into "not found" instead of failing the whole resolve.
 */
internal object JsonPath {

    /** the first value at [path], or null when the path does not resolve */
    fun first(root: JsonElement?, path: String): JsonElement? {
        var current: JsonElement = root ?: return null
        for (step in steps(path)) {
            current =
                when (step) {
                    is Step.Key -> (current as? JsonObject)?.get(step.name) ?: return null
                    is Step.Index -> (current as? JsonArray)?.getOrNull(step.index) ?: return null
                    Step.All -> return null
                }
        }
        return current.takeUnless { it is JsonNull }
    }

    /**
     * Every value at [path].
     *
     * A path that lands on an array is spread into its elements, because that is
     * what a path to "the results" almost always means: the module names the list,
     * not the array object. `*` does the same thing explicitly, for the cases where
     * the step that reaches the array is itself already inside a list.
     */
    fun all(root: JsonElement?, path: String): List<JsonElement> {
        var current: List<JsonElement> = listOfNotNull(root)
        for (step in steps(path)) {
            current =
                when (step) {
                    is Step.Key ->
                        current.mapNotNull { (it as? JsonObject)?.get(step.name)?.takeUnless { v -> v is JsonNull } }
                    is Step.Index ->
                        current.mapNotNull { (it as? JsonArray)?.getOrNull(step.index)?.takeUnless { v -> v is JsonNull } }
                    Step.All ->
                        current.flatMap { element ->
                            when (element) {
                                is JsonArray -> element.filterNot { it is JsonNull }
                                else -> listOf(element)
                            }
                        }
                }
            if (current.isEmpty()) return emptyList()
        }
        return current.flatMap { element ->
            when (element) {
                is JsonArray -> element.filterNot { it is JsonNull }
                else -> listOf(element)
            }
        }
    }

    fun string(root: JsonElement?, path: String): String? = first(root, path)?.asText()?.takeIf { it.isNotBlank() }

    fun number(root: JsonElement?, path: String): Double? = first(root, path)?.asDouble()

    /** a value as the text a person would read, whatever shape it actually is */
    fun JsonElement.asText(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject -> {
                // services routinely return {"name": "…"} where a string is expected;
                // preferring the obvious keys keeps modules from having to know that
                sequenceOf("name", "title", "text", "value", "label")
                    .mapNotNull { (this[it] as? JsonPrimitive)?.contentOrNull }
                    .firstOrNull()
            }
            is JsonArray -> joinToString(", ") { it.asText().orEmpty() }.takeIf { it.isNotBlank() }
            JsonNull -> null
        }

    fun JsonElement.asDouble(): Double? =
        when (this) {
            is JsonPrimitive -> contentOrNull?.let { text ->
                text.toDoubleOrNull() ?: text.toLongOrNull()?.toDouble()
            }
            is JsonObject ->
                sequenceOf("value", "count", "number")
                    .mapNotNull { key -> (this[key] as? JsonPrimitive)?.asDouble() }
                    .firstOrNull()
            else -> null
        }

    private sealed interface Step {
        data class Key(val name: String) : Step
        data class Index(val index: Int) : Step
        data object All : Step
    }

    /**
     * `a.b[2].c` becomes key(a), key(b), index(2), key(c); `a.*.b` fans out.
     *
     * A leading `$` or `.` is tolerated, because half the documentation people
     * copy paths from writes them that way and refusing it would only be rude.
     */
    private fun steps(path: String): List<Step> {
        if (path.isBlank()) return emptyList()
        val normalised = path.trim().removePrefix("$").removePrefix(".")
        if (normalised.isEmpty()) return emptyList()
        val result = mutableListOf<Step>()
        var index = 0
        val buffer = StringBuilder()
        fun flushKey() {
            val key = buffer.toString()
            buffer.clear()
            if (key.isEmpty()) return
            if (key == "*") result += Step.All else result += Step.Key(key)
        }
        while (index < normalised.length) {
            when (val char = normalised[index]) {
                '.' -> {
                    flushKey()
                    index++
                }
                '[' -> {
                    flushKey()
                    val close = normalised.indexOf(']', index)
                    if (close < 0) return result.also { buffer.clear() }
                    val inside = normalised.substring(index + 1, close).trim()
                    if (inside == "*") {
                        result += Step.All
                    } else {
                        inside.toIntOrNull()?.let { result += Step.Index(it) }
                    }
                    index = close + 1
                }
                else -> {
                    buffer.append(char)
                    index++
                }
            }
        }
        flushKey()
        return result
    }
}

/**
 * Filling a module's templates in.
 *
 * `{title}`, `{artist}`, `{album}`, `{duration}`, `{isrc}`, and `{picks.<id>}` for
 * the options the user chose. Placeholders are replaced textually into a string
 * that is then percent-encoded where it is going into a URL, so a track whose
 * title contains `&` or `/` cannot break out of the parameter it was placed in —
 * which is the entire security story of having a template language at all.
 */
internal object Template {

    private val Placeholder = Regex("\\{([a-zA-Z0-9_.]+)}")

    /** every placeholder a template mentions, so a missing one can be reported early */
    fun placeholders(template: String): Set<String> =
        Placeholder.findAll(template).map { it.groupValues[1] }.toSet()

    fun render(
        template: String,
        values: Map<String, String>,
        urlEncode: Boolean,
    ): String =
        Placeholder.replace(template) { match ->
            val key = match.groupValues[1]
            val raw = values[key] ?: ""
            // an absent value collapses to empty rather than leaving {braces} in a
            // URL, where they would be sent to the service and rejected
            if (urlEncode) percentEncode(raw) else raw
        }

    /**
     * Percent-encodes one parameter value.
     *
     * Everything except the unreserved set is escaped, including `/` and `&` and
     * `?`, because the value is always being placed *inside* a query parameter or
     * a path segment and must not be able to end it.
     */
    fun percentEncode(value: String): String {
        val out = StringBuilder(value.length)
        for (byte in value.encodeToByteArray()) {
            val char = byte.toInt() and 0xFF
            val unreserved =
                char in 'a'.code..'z'.code ||
                    char in 'A'.code..'Z'.code ||
                    char in '0'.code..'9'.code ||
                    char == '-'.code || char == '_'.code || char == '.'.code || char == '~'.code
            if (unreserved) {
                out.append(char.toChar())
            } else {
                out.append('%')
                out.append(HexDigits[char shr 4])
                out.append(HexDigits[char and 0x0F])
            }
        }
        return out.toString()
    }

    private const val HexDigits = "0123456789ABCDEF"
}

/**
 * An index into a module's own answers, used to carry values from the search step
 * into the stream step without evaluating anything: `{match.id}` is whatever the
 * match step stored under `id`.
 */
internal fun Map<String, String>.withMatch(vararg pairs: Pair<String, String>): Map<String, String> =
    HashMap(this).apply { pairs.forEach { (key, value) -> if (value.isNotBlank()) put("match.$key", value) } }
