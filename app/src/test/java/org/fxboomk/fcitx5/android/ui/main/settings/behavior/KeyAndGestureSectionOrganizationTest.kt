/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import org.fxboomk.fcitx5.android.ui.main.settings.theme.ThemeSettingsSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyAndGestureSectionOrganizationTest {

    @Test
    fun keyAppearanceSettingsMovedOutOfBehaviorSections() {
        val basic = KeyboardSettingsSupport.basicBehaviorKeys
        val gesture = KeyboardSettingsSupport.keyAndGestureKeys
        assertTrue("expand_keypress_area" in basic)
        assertFalse("popup_on_key_press" in basic)
        assertFalse("popup_on_key_press" in gesture)
        assertTrue("popup_on_key_press" in ThemeSettingsSupport.keyStyleKeyboardKeys)
        assertFalse("space_key_label_mode" in gesture)
        assertTrue("space_key_label_mode" in ThemeSettingsSupport.keyStyleKeyboardKeys)
    }

    @Test
    fun remainingKeyAndGestureKeysKeepTheirOrder() {
        assertEquals(
            listOf(
                "punctuation_swipe_strategy",
                "swipe_symbol_behavior",
                "caps_key_behavior",
                "lang_switch_key_behavior",
                "lang_switch_key_long_press_behavior",
                "space_long_press_behavior",
                "space_swipe_vertical_behavior",
                "prediction_space_behavior",
                "prediction_backspace_behavior"
            ),
            KeyboardSettingsSupport.keyAndGestureKeys
        )
    }
}
