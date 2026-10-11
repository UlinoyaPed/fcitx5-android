/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.input.picker.PickerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCloseBracketTest {

    @Test
    fun halfWidthOpenersResolveToTheirClosers() {
        assertEquals(")", AutoCloseBracket.closerFor("("))
        assertEquals("]", AutoCloseBracket.closerFor("["))
        assertEquals("}", AutoCloseBracket.closerFor("{"))
        assertEquals(">", AutoCloseBracket.closerFor("<"))
        assertEquals("\"", AutoCloseBracket.closerFor("\""))
        assertEquals("'", AutoCloseBracket.closerFor("'"))
        assertEquals("`", AutoCloseBracket.closerFor("`"))
    }

    @Test
    fun fullWidthAndCjkOpenersResolveToTheirClosers() {
        assertEquals("）", AutoCloseBracket.closerFor("（"))
        assertEquals("］", AutoCloseBracket.closerFor("［"))
        assertEquals("｝", AutoCloseBracket.closerFor("｛"))
        assertEquals("】", AutoCloseBracket.closerFor("【"))
        assertEquals("》", AutoCloseBracket.closerFor("《"))
        assertEquals("」", AutoCloseBracket.closerFor("「"))
        assertEquals("』", AutoCloseBracket.closerFor("『"))
        assertEquals("〉", AutoCloseBracket.closerFor("〈"))
        assertEquals("〕", AutoCloseBracket.closerFor("〔"))
        assertEquals("〙", AutoCloseBracket.closerFor("〘"))
        assertEquals("”", AutoCloseBracket.closerFor("“"))
        assertEquals("’", AutoCloseBracket.closerFor("‘"))
    }

    @Test
    fun ornamentalAndGuillemetOpenersResolveToTheirClosers() {
        assertEquals("❩", AutoCloseBracket.closerFor("❨"))
        assertEquals("❳", AutoCloseBracket.closerFor("❲"))
        assertEquals("❵", AutoCloseBracket.closerFor("❴"))
        assertEquals("❜", AutoCloseBracket.closerFor("❛"))
        assertEquals("❞", AutoCloseBracket.closerFor("❝"))
        assertEquals("›", AutoCloseBracket.closerFor("‹"))
        assertEquals("»", AutoCloseBracket.closerFor("«"))
    }

    @Test
    fun verticalPresentationFormOpenersResolveToTheirClosers() {
        assertEquals("︶", AutoCloseBracket.closerFor("︵"))
        assertEquals("︼", AutoCloseBracket.closerFor("︻"))
        assertEquals("﹂", AutoCloseBracket.closerFor("﹁"))
    }

    /**
     * 需求：输入法内置符号表（PickerData.Symbol 的「( )」括号类目）里的所有成对符号都要支持。
     * 该类目的数组严格按「左, 右, 左, 右……」排列，这里逐对校验闭合映射存在且正确。
     */
    @Test
    fun everyPairInTheBuiltInBracketCategoryIsSupported() {
        val bracketCategory = PickerData.Symbol
            .first { it.first.label == "( )" }
            .second
        assertTrue("bracket category should contain an even number of symbols", bracketCategory.size % 2 == 0)
        for (i in bracketCategory.indices step 2) {
            val opener = bracketCategory[i]
            val closer = bracketCategory[i + 1]
            assertEquals("built-in pair $opener$closer must be auto-closable", closer, AutoCloseBracket.closerFor(opener))
        }
    }

    @Test
    fun closersAndUnrelatedTextDoNotResolve() {
        // A closing half must not itself open a pair.
        assertNull(AutoCloseBracket.closerFor(")"))
        assertNull(AutoCloseBracket.closerFor("）"))
        assertNull(AutoCloseBracket.closerFor("】"))
        assertNull(AutoCloseBracket.closerFor("»"))
        // Ordinary text / multi-character commits are never treated as openers.
        assertNull(AutoCloseBracket.closerFor("a"))
        assertNull(AutoCloseBracket.closerFor("，"))
        assertNull(AutoCloseBracket.closerFor("()"))
        assertNull(AutoCloseBracket.closerFor(""))
    }

    @Test
    fun isPairRecognizesAdjacentOpenerAndCloser() {
        assertTrue(AutoCloseBracket.isPair('(', ')'))
        assertTrue(AutoCloseBracket.isPair('（', '）'))
        assertTrue(AutoCloseBracket.isPair('【', '】'))
        assertTrue(AutoCloseBracket.isPair('“', '”'))
        assertTrue(AutoCloseBracket.isPair('"', '"'))
        assertTrue(AutoCloseBracket.isPair('«', '»'))
        assertTrue(AutoCloseBracket.isPair('❨', '❩'))
    }

    @Test
    fun isPairRejectsMismatchedOrReversedCharacters() {
        assertFalse(AutoCloseBracket.isPair('(', ']'))
        assertFalse(AutoCloseBracket.isPair(')', '('))
        assertFalse(AutoCloseBracket.isPair('【', '》'))
        assertFalse(AutoCloseBracket.isPair('a', 'b'))
    }
}
