/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.core.InputMethodSubMode
import org.fxboomk.fcitx5.android.core.KeyState
import org.fxboomk.fcitx5.android.core.KeyStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipePunctuationTest {
    @Test
    fun defaultRoutesLiteralPunctuationThroughTheEngine() {
        val punctuation = ('!'..'/') + (':'..'@') + ('['..'`') + ('{'..'~')
        punctuation.forEach { char ->
            val text = char.toString()
            assertEquals(
                KeyAction.FcitxKeyAction(text),
                punctuationSwipeAction(KeyAction.CommitAction(text), PunctuationSwipeStrategy.Default)
            )
        }
    }

    @Test
    fun defaultPreservesExistingEngineActions() {
        val action = KeyAction.FcitxKeyAction(",", code = 123)
        assertSame(action, punctuationSwipeAction(action, PunctuationSwipeStrategy.Default))
    }

    @Test
    fun defaultKeepsLiteralMultiCharacterLabelsAsCommits() {
        // Default never routes multi-character / non-punctuation labels through the engine;
        // they stay literal commits (now flagged swipeSymbol so Caps can remap them).
        for (text in listOf("?!", "...", "——", "……", "你好", "abc?")) {
            val result = punctuationSwipeAction(KeyAction.CommitAction(text), PunctuationSwipeStrategy.Default)
            assertTrue(result is KeyAction.CommitAction)
            assertEquals(text, (result as KeyAction.CommitAction).text)
            assertFalse(result.followPunctuationMode)
        }
    }

    @Test
    fun defaultKeepsBuiltInSwipePunctuationOnTheUpstreamEnginePath() {
        val action = AlphabetKey("V", "?").behaviors
            .filterIsInstance<KeyDef.Behavior.Swipe>()
            .single().action!!
        assertEquals(KeyAction.FcitxKeyAction("?"), action)
        assertSame(action, punctuationSwipeAction(action, PunctuationSwipeStrategy.Default))
    }

    @Test
    fun rawBypassesEnginePunctuationProcessingWithoutConversion() {
        for (text in listOf(",", "?!", "\"", "\\", "^", "$")) {
            for (action in listOf(KeyAction.CommitAction(text), KeyAction.FcitxKeyAction(text))) {
                assertEquals(
                    KeyAction.CommitAction(text, swipeSymbol = true),
                    punctuationSwipeAction(action, PunctuationSwipeStrategy.Raw)
                )
            }
        }
    }

    @Test
    fun strategiesRemainDistinctForEnginePunctuation() {
        val action = KeyAction.FcitxKeyAction(",")
        assertSame(action, punctuationSwipeAction(action, PunctuationSwipeStrategy.Default))
        assertEquals(
            KeyAction.CommitAction(",", followPunctuationMode = true, swipeSymbol = true),
            punctuationSwipeAction(action, PunctuationSwipeStrategy.FollowInputMode)
        )
        assertEquals(
            KeyAction.CommitAction(",", swipeSymbol = true),
            punctuationSwipeAction(action, PunctuationSwipeStrategy.Raw)
        )
    }

    @Test
    fun ordinaryTextCommitsDoNotOptIntoPunctuationConversion() {
        assertFalse(KeyAction.CommitAction(",").followPunctuationMode)
        assertFalse(KeyAction.CommitAction(",").swipeSymbol)
    }

    @Test
    fun followInputModeMarksPunctuationFromDirectAndEngineActions() {
        val actions = listOf(KeyAction.CommitAction("?!"), KeyAction.FcitxKeyAction(","))
        actions.forEach {
            val result = punctuationSwipeAction(it, strategy = PunctuationSwipeStrategy.FollowInputMode)
            assertTrue(result is KeyAction.CommitAction)
            assertTrue((result as KeyAction.CommitAction).followPunctuationMode)
            assertTrue(result.swipeSymbol)
        }
        assertEquals(
            KeyAction.CommitAction(",", followPunctuationMode = true, swipeSymbol = true),
            punctuationSwipeAction(actions[1], PunctuationSwipeStrategy.FollowInputMode)
        )
    }

    @Test
    fun nonPunctuationSymbolsAreNeverFullWidthConverted() {
        // Non-punctuation swipe commits (letters like "ü", digits, emoji, CJK) keep their
        // text and never opt into full-width conversion, but do become swipe symbols so a
        // user-defined uppercase mapping can apply under Caps (需求 3/5).
        for (text in listOf("A", "9", "你好", "😄", "abc?", " ", "，", "ü")) {
            PunctuationSwipeStrategy.entries.forEach { strategy ->
                val result = punctuationSwipeAction(KeyAction.CommitAction(text), strategy)
                assertTrue(result is KeyAction.CommitAction)
                result as KeyAction.CommitAction
                assertEquals(text, result.text)
                assertFalse(result.followPunctuationMode)
            }
        }
        // An empty swipe stays a no-op for every strategy.
        PunctuationSwipeStrategy.entries.forEach { strategy ->
            val empty = KeyAction.CommitAction("")
            assertSame(empty, punctuationSwipeAction(empty, strategy))
        }
    }

    @Test
    fun macrosSpecialKeysModifiersAndReleasesArePreserved() {
        val actions = listOf(
            MacroAction(listOf(MacroStep.Text(","))),
            KeyAction.FcitxKeyAction("Return"),
            KeyAction.FcitxKeyAction(",", states = KeyStates(KeyState.Virtual, KeyState.Ctrl)),
            KeyAction.FcitxKeyAction(",", up = true),
            KeyAction.CapsAction(false)
        )
        PunctuationSwipeStrategy.entries.forEach { strategy ->
            actions.forEach { assertSame(it, punctuationSwipeAction(it, strategy)) }
        }
    }

    @Test
    fun chineseModeUsesChinesePunctuation() {
        for (language in listOf("zh", "zh_CN", "zh-TW", "zh_HK")) {
            assertEquals(
                "，。？！：；（）【】《》、……——￥",
                resolveSwipePunctuation(",.?!:;()[]<>\\^_$", inputMethod(language = language))
            )
        }
    }

    @Test
    fun remainingAsciiSymbolsBecomeFullWidth() {
        assertEquals(
            "＂＇＃％＆＊＋－／＝＠｛｜｝～｀",
            resolveSwipePunctuation("\"'#%&*+-/=@{|}~`", inputMethod())
        )
    }

    @Test
    fun englishAndOtherLanguagesCommitUnchanged() {
        for (language in listOf("en", "en_US", "ja", "ko", "")) {
            assertEquals(",.?!，", resolveSwipePunctuation(",.?!，", inputMethod(language = language)))
        }
    }

    @Test
    fun rimeAsciiAndCapsLockModesCommitUnchangedDespiteChineseLanguage() {
        for (icon in listOf("fcitx_rime_latin", "fcitx_rime_latin_upper", "fcitx_rime_disable")) {
            assertEquals(",.?!", resolveSwipePunctuation(",.?!", inputMethod(icon = icon)))
        }
        assertEquals("，。？！", resolveSwipePunctuation(",.?!", inputMethod(icon = "fcitx-rime")))
    }

    @Test
    fun switchingModesDoesNotReusePreviousConversionState() {
        val chinese = inputMethod(icon = "fcitx-rime")
        val english = inputMethod(icon = "fcitx_rime_latin")
        assertEquals("，", resolveSwipePunctuation(",", chinese))
        assertEquals(",", resolveSwipePunctuation(",", english))
        assertEquals("，", resolveSwipePunctuation(",", chinese))
    }

    @Test
    fun passwordFieldsAndExistingFullWidthSymbolsStayUnchanged() {
        assertEquals(",.?", resolveSwipePunctuation(",.?", inputMethod(), isPassword = true))
        assertEquals("，。？！“”", resolveSwipePunctuation("，。？！“”", inputMethod()))
    }

    @Test
    fun customFullWidthMappingOverridesTheDefault() {
        val mapping = mapOf(
            "," to SwipeSymbolEntry(fullWidth = "،"),
            "." to SwipeSymbolEntry(fullWidth = "۔"),
        )
        assertEquals("،۔", resolveSwipePunctuation(",.", inputMethod(), mapping = mapping))
    }

    @Test
    fun unmappedPunctuationStillFallsBackToFullWidth() {
        // '?' absent from the custom map falls back to the half-width + 0xFEE0 rule.
        val mapping = mapOf("," to SwipeSymbolEntry(fullWidth = "，"))
        assertEquals("，？", resolveSwipePunctuation(",?", inputMethod(), mapping = mapping))
    }

    @Test
    fun emptyFullWidthColumnFallsBackToFullWidthRule() {
        val mapping = mapOf("," to SwipeSymbolEntry(fullWidth = ""))
        assertEquals("，", resolveSwipePunctuation(",", inputMethod(), mapping = mapping))
    }

    @Test
    fun defaultMappingReproducesLegacyFullWidthConversion() {
        assertEquals(
            "，。？！：；（）【】《》、……——￥",
            resolveSwipePunctuation(",.?!:;()[]<>\\^_$", inputMethod(), mapping = DefaultSwipeSymbolMapping)
        )
    }

    @Test
    fun capsUppercaseUsesUpperColumnWithRawLowercaseFallback() {
        val mapping = mapOf(
            "," to SwipeSymbolEntry(fullWidth = "，", upper = "<"),
            "." to SwipeSymbolEntry(fullWidth = "。", upper = ""),
        )
        // ',' has an uppercase symbol; '.' leaves it blank → raw lowercase; '?' is unmapped → raw.
        assertEquals("<.?", resolveSwipeUppercase(",.?", mapping))
    }

    @Test
    fun capsUppercaseWithDefaultMappingKeepsRawLowercase() {
        assertEquals(",.?!", resolveSwipeUppercase(",.?!"))
    }

    @Test
    fun customAltSymbolSwipeIsEligibleForCapsUppercase() {
        // 需求 3: a custom non-punctuation alt such as "ü" becomes a swipe symbol under every
        // strategy, so Caps can remap it through the mapping's uppercase column.
        PunctuationSwipeStrategy.entries.forEach { strategy ->
            val result = punctuationSwipeAction(KeyAction.CommitAction("ü"), strategy)
            assertTrue(result is KeyAction.CommitAction)
            assertTrue((result as KeyAction.CommitAction).swipeSymbol)
        }
        val mapping = mapOf("ü" to SwipeSymbolEntry(fullWidth = "", upper = "Ü"))
        assertEquals("Ü", resolveSwipeUppercase("ü", mapping))
    }

    private fun inputMethod(language: String = "zh_CN", icon: String = "") = InputMethodEntry(
        uniqueName = "test",
        name = "Test",
        icon = "",
        nativeName = "",
        label = "",
        languageCode = language,
        addon = "rime",
        isConfigurable = false,
        subMode = InputMethodSubMode("自定义名称", "自定义标签", icon)
    )
}
