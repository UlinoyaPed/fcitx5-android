/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.input.keyboard

import java.util.Locale

internal fun isSwipeLetterText(text: String): Boolean = text.isNotEmpty() &&
    text.codePoints().anyMatch(Character::isLetter) && text.codePoints().allMatch { Character.isLetter(it) || Character.getType(it) in setOf(6, 8) }

private fun parseSwipeCaseMappings(raw: String): List<Pair<String, String>> =
    raw.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split('=')
        if (parts.size != 2) return@mapNotNull null
        val lower = parts[0].trim()
        val upper = parts[1].trim()
        if (!isSwipeLetterText(lower) || !isSwipeLetterText(upper)) null else lower to upper
    }.toList()

internal fun isValidSwipeCaseMappings(raw: String): Boolean {
    val pairs = parseSwipeCaseMappings(raw)
    return pairs.size == raw.lineSequence().count { it.isNotBlank() } &&
        pairs.flatMap { listOf(it.first, it.second) }.let { it.distinct().size == it.size }
}

internal fun transformSwipeLetters(text: String, uppercase: Boolean, mappings: String = "ß=ẞ"): String {
    if (!isSwipeLetterText(text)) return text
    val pairs = parseSwipeCaseMappings(mappings)
    val overrides = buildMap {
        pairs.forEach { (lower, upper) ->
            put(lower, if (uppercase) upper else lower)
            put(upper, if (uppercase) upper else lower)
        }
    }
    overrides[text]?.let { return it }
    if (text.codePoints().noneMatch { String(Character.toChars(it)) in overrides }) {
        return if (uppercase) text.uppercase(Locale.ROOT) else text.lowercase(Locale.ROOT)
    }
    return buildString {
        text.codePoints().forEach { codePoint ->
            val token = String(Character.toChars(codePoint))
            append(overrides[token] ?: if (uppercase) token.uppercase(Locale.ROOT) else token.lowercase(Locale.ROOT))
        }
    }
}
