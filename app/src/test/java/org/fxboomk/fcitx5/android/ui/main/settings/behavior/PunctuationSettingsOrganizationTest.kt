/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationSettingsOrganizationTest {
    @Test
    fun punctuationStrategyImmediatelyPrecedesSwipeDirection() {
        val keys = KeyboardSettingsSupport.keyAndGestureKeys
        val index = keys.indexOf("punctuation_swipe_strategy")
        assertTrue(index >= 0)
        assertEquals("swipe_symbol_behavior", keys[index + 1])
        assertEquals(1, keys.count { it == "punctuation_swipe_strategy" })
    }
}
