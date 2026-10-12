package org.fxboomk.fcitx5.android.input.predict

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Decodes complete entries even when the surrounding streamed JSON is unfinished. */
internal object LlmSuggestionJson {
    data class Candidate(val text: String, val exactInsertion: Boolean)

    private val arrayHeader = Regex("""^\s*\{\s*["“”]suggestions["“”]\s*:\s*\[""")

    fun read(raw: String): List<Candidate> {
        val input = raw.trim()
        val complete = runCatching { Json.parseToJsonElement(input) }.getOrNull()
        if (complete != null) {
            val array = when (complete) {
                is JsonArray -> complete
                is JsonObject -> complete["suggestions"] as? JsonArray
                else -> null
            } ?: return emptyList()
            return array.mapNotNull(::candidate)
        }
        val start = if (input.startsWith('[')) 1 else arrayHeader.find(input)?.range?.last?.plus(1)
            ?: return emptyList()
        val result = mutableListOf<Candidate>()
        var index = start
        var needsValue = true
        while (index < input.length) {
            while (index < input.length && input[index].isWhitespace()) index++
            if (index >= input.length || input[index] == ']') break
            if (!needsValue) {
                if (input[index] != ',' && input[index] != '，') break
                needsValue = true
                index++
                continue
            }
            val char = input[index]
            val end = when (char) {
                '"', '“', '”' -> stringEnd(input, index)
                '{', '[' -> compoundEnd(input, index)
                else -> scalarEnd(input, index)
            } ?: break
            val token = input.substring(index, end)
            val value = if (char == '“' || char == '”' || token.lastOrNull() == '”') {
                runCatching { Json.parseToJsonElement("\"${token.substring(1, token.lastIndex)}\"") }.getOrNull()
            } else {
                runCatching { Json.parseToJsonElement(token) }.getOrNull()
            }
            value?.let(::candidate)?.let(result::add)
            index = end
            needsValue = false
        }
        return result
    }

    private fun candidate(value: JsonElement): Candidate? = when (value) {
        is JsonPrimitive -> value.takeIf { it.isString }?.let { Candidate(it.content, false) }
        is JsonObject -> if (value.keys == setOf("insert_text")) {
            (value["insert_text"] as? JsonPrimitive)?.takeIf { it.isString }
                ?.let { Candidate(it.content, true) }
        } else null
        else -> null
    }

    private fun stringEnd(input: String, start: Int): Int? {
        val opening = input[start]
        var escaped = false
        for (index in start + 1 until input.length) {
            val char = input[index]
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"' || char == '”') {
                if (char == '"' && opening == '"') return index + 1
                // Tolerate legacy smart quotes only at a value boundary, never inside its text.
                val next = input.substring(index + 1).firstOrNull { !it.isWhitespace() }
                if (next == null || next in ",，]}") return index + 1
            }
        }
        return null
    }

    private fun compoundEnd(input: String, start: Int): Int? {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (index in start until input.length) {
            val char = input[index]
            if (inString) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> stack.addLast('}')
                    '[' -> stack.addLast(']')
                    '}', ']' -> {
                        if (stack.removeLastOrNull() != char) return null
                        if (stack.isEmpty()) return index + 1
                    }
                }
            }
        }
        return null
    }

    private fun scalarEnd(input: String, start: Int): Int? {
        for (index in start until input.length) {
            if (input[index] == ',' || input[index] == '，' || input[index] == ']') return index
        }
        return null
    }
}
