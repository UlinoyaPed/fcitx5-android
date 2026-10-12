/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fxboomk.fcitx5.android.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseEnglishSpacingTest {

    private fun pad(text: String, cursor: Int = -1, previous: Int? = null) =
        ChineseEnglishSpacing.pad(text, cursor, previous)

    @Test
    fun prependsSpaceWhenChinesePrecedesAsciiLetterCommit() {
        val result = pad("app", previous = '好'.code)
        // Chinese before an ASCII word: pad on both sides.
        assertEquals(" app ", result.text)
    }

    @Test
    fun appendsSpaceAfterAsciiWordWithoutChinesePrefix() {
        val result = pad("app", previous = ' '.code)
        assertEquals("app ", result.text)
    }

    @Test
    fun doesNotTouchChineseCommit() {
        val result = pad("你好", previous = 'a'.code)
        assertEquals("你好", result.text)
    }

    @Test
    fun doesNotPrependWhenPrecedingCharIsAscii() {
        val result = pad("hello", previous = 'x'.code)
        // No Chinese neighbour, but it is still an ASCII word → trailing pad only.
        assertEquals("hello ", result.text)
    }

    @Test
    fun doesNotDoublePadWhenWordAlreadyEndsWithSpace() {
        val result = pad("app ", previous = '好'.code)
        assertEquals(" app ", result.text)
    }

    @Test
    fun prependsForLetterLedCommitButNeverAppendsForMixedCommit() {
        // "a1" is not a pure ASCII word, so it is not padded on the right, but it
        // still starts with a letter, so a Chinese neighbour triggers a prepend.
        assertEquals(" a1", pad("a1", previous = '好'.code).text)
        // Punctuation neither starts with a letter nor is an ASCII word.
        assertEquals("，", pad("，", previous = 'a'.code).text)
    }

    @Test
    fun treatsNullPrecedingCharAsNoPrepend() {
        val result = pad("word", previous = null)
        assertEquals("word ", result.text)
    }

    @Test
    fun supplementaryChinesePrefixIsRecognized() {
        // U+20000, a CJK Extension B ideograph, as a surrogate pair.
        val supplementary = 0x20000
        val result = pad("app", previous = supplementary)
        assertEquals(" app ", result.text)
    }

    @Test
    fun keepsCommitCursorWhenUnset() {
        assertEquals(-1, pad("app", cursor = -1, previous = '好'.code).cursor)
    }

    @Test
    fun shiftsCursorPastPrependedAndAppendedSpaces() {
        // cursor at end of a 3-char word, Chinese before → +1 (prepend) +1 (append).
        val result = pad("app", cursor = 3, previous = '好'.code)
        assertEquals(" app ", result.text)
        assertEquals(5, result.cursor)
    }

    @Test
    fun shiftsCursorForPrependOnlyWhenCursorNotAtWordEnd() {
        // Mid-word cursor: prepend adds 1; append does not move an interior cursor.
        val result = pad("app", cursor = 1, previous = '好'.code)
        assertEquals(" app ", result.text)
        assertEquals(2, result.cursor)
    }

    @Test
    fun emptyCommitIsReturnedUnchanged() {
        val result = pad("", cursor = 0, previous = '好'.code)
        assertEquals("", result.text)
        assertEquals(0, result.cursor)
    }
}
