/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input

/**
 * Pads a commit coming out of fcitx with spaces where Chinese meets Latin
 * letters, for the "insert space between Chinese and English" preference.
 *
 * Applies to text committed out of the composition: picking a candidate, or a
 * key such as Return (also with Ctrl / Shift) committing the preedit as is.
 */
internal object ChineseEnglishSpacing {

    data class PaddedCommit(val text: String, val cursor: Int)

    /**
     * @param text text fcitx is committing.
     * @param cursor commit cursor reported by fcitx, `-1` when unset.
     * @param previousCodePoint code point right before the position [text] lands
     * at, or `null` when nothing precedes it.
     */
    fun pad(text: String, cursor: Int, previousCodePoint: Int?): PaddedCommit {
        if (text.isEmpty()) {
            return PaddedCommit(text, cursor)
        }
        val prependSpace = previousCodePoint != null &&
            isChineseCodePoint(previousCodePoint) &&
            isAsciiLetter(text.codePointAt(0))
        val appendSpace = isAsciiWord(text) && !text.endsWith(' ')
        if (!prependSpace && !appendSpace) {
            return PaddedCommit(text, cursor)
        }
        val paddedText = buildString(text.length + 2) {
            if (prependSpace) {
                append(' ')
            }
            append(text)
            if (appendSpace) {
                append(' ')
            }
        }
        val cursorOffset = (if (prependSpace) 1 else 0) +
            (if (appendSpace && cursor == text.length) 1 else 0)
        val paddedCursor = if (cursor == -1) {
            -1
        } else {
            cursor + cursorOffset
        }
        return PaddedCommit(paddedText, paddedCursor)
    }

    fun isAsciiWord(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            if (!isAsciiLetter(codePoint)) {
                return false
            }
            offset += Character.charCount(codePoint)
        }
        return true
    }

    fun isAsciiLetter(codePoint: Int): Boolean =
        codePoint in 'A'.code..'Z'.code ||
            codePoint in 'a'.code..'z'.code

    fun isChineseCodePoint(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2A6DF ||
            codePoint in 0x2A700..0x2B73F ||
            codePoint in 0x2B740..0x2B81F ||
            codePoint in 0x2B820..0x2CEAF ||
            codePoint in 0x2CEB0..0x2EBEF ||
            codePoint in 0x30000..0x3134F ||
            codePoint in 0x31350..0x323AF
}
