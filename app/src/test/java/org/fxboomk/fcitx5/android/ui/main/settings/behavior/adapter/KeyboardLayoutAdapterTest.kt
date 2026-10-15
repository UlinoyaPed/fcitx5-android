/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.adapter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutAdapterTest {
    @Test
    fun `only single character labels use the fixed letter key width`() {
        assertTrue(KeyboardLayoutAdapter.isSingleCharacterKeyLabel("I"))
        assertTrue(KeyboardLayoutAdapter.isSingleCharacterKeyLabel("小"))
        assertFalse(KeyboardLayoutAdapter.isSingleCharacterKeyLabel("Ii"))
        assertFalse(KeyboardLayoutAdapter.isSingleCharacterKeyLabel("Aa"))
        assertFalse(KeyboardLayoutAdapter.isSingleCharacterKeyLabel(""))
    }

    @Test
    fun `required and serializer default fields are not treated as customized`() {
        assertFalse(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf(
                    "type" to "AlphabetKey",
                    "main" to "i",
                    "alt" to "8",
                    "displayText" to "i",
                    "weight" to 0.1f
                )
            )
        )
        assertFalse(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "CapsKey", "weight" to 0.15f)
            )
        )
        assertFalse(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "SpaceKey", "weight" to 0f)
            )
        )
        assertFalse(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "SymbolKey", "label" to ".", "weight" to 0.1f)
            )
        )
    }

    @Test
    fun `empty optional fields are not treated as customized`() {
        assertFalse(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf(
                    "type" to "AlphabetKey",
                    "main" to "i",
                    "alt" to "8",
                    "displayText" to null,
                    "weight" to null,
                    "alt1" to ""
                )
            )
        )
        assertFalse(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "CapsKey", "swipe" to emptyMap<String, Any?>())
            )
        )
    }

    @Test
    fun `non-default optional fields are treated as customized`() {
        assertTrue(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "AlphabetKey", "main" to "i", "alt" to "8", "weight" to 0.08f)
            )
        )
        assertTrue(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "AlphabetKey", "main" to "i", "alt" to "8", "displayText" to "I")
            )
        )
        // 副字符一（alt1）不参与定制判定
        assertFalse(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "AlphabetKey", "main" to "i", "alt" to "8", "alt1" to "I")
            )
        )
        assertTrue(
            KeyboardLayoutAdapter.hasCustomizedProperties(
                mapOf("type" to "CapsKey", "backgroundColor" to 0xFF00FF00)
            )
        )
    }

    @Test
    fun `changed alt or alt1 character is treated as customized against the default preset`() {
        val defaults = mapOf("l" to ")", "i" to "8")
        // 副字符从默认 ")" 改成 "ü" → 已定制（绿框）
        assertTrue(
            KeyboardLayoutAdapter.isAltCustomized(
                mapOf("type" to "AlphabetKey", "main" to "l", "alt" to "ü"), defaults, true
            )
        )
        // 副字符一改成非"自动大写"的字符 → 已定制
        assertTrue(
            KeyboardLayoutAdapter.isAltCustomized(
                mapOf("type" to "AlphabetKey", "main" to "l", "alt" to ")", "alt1" to "Ü"), defaults, true
            )
        )
        // 大写提示关闭时填入任意 alt1 → 已定制
        assertTrue(
            KeyboardLayoutAdapter.isAltCustomized(
                mapOf("type" to "AlphabetKey", "main" to "l", "alt" to ")", "alt1" to "X"), defaults, false
            )
        )
        // 副字符与默认一致、alt1 等于自动大写("L") → 未定制（仅物化自动提示）
        assertFalse(
            KeyboardLayoutAdapter.isAltCustomized(
                mapOf("type" to "AlphabetKey", "main" to "l", "alt" to ")", "alt1" to "L"), defaults, true
            )
        )
        // 未打开过的按键：alt 默认、alt1 缺省 → 未定制（避免整排误标绿）
        assertFalse(
            KeyboardLayoutAdapter.isAltCustomized(
                mapOf("type" to "AlphabetKey", "main" to "l", "alt" to ")"), defaults, true
            )
        )
        // 无 alt 基准 + alt1 等于自动大写 → 未定制
        assertFalse(
            KeyboardLayoutAdapter.isAltCustomized(
                mapOf("type" to "AlphabetKey", "main" to "l", "alt" to "ü", "alt1" to "L"), emptyMap(), true
            )
        )
        // 非字母键 → 不适用
        assertFalse(
            KeyboardLayoutAdapter.isAltCustomized(
                mapOf("type" to "SymbolKey", "label" to "."), defaults, true
            )
        )
    }
}
