/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyAndGestureSectionOrganizationTest {

    @Test
    fun keyPressBehaviorsMovedFromKeyAndGestureToBasicBehavior() {
        val basic = KeyboardSettingsSupport.basicBehaviorKeys
        val gesture = KeyboardSettingsSupport.keyAndGestureKeys
        for (key in listOf("popup_on_key_press", "expand_keypress_area")) {
            assertTrue("$key should be in basic behavior", key in basic)
            assertFalse("$key should no longer be in key and gesture", key in gesture)
            assertEquals("$key should be listed exactly once", 1, basic.count { it == key })
        }
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
                "space_key_label_mode",
                "space_long_press_behavior",
                "space_swipe_vertical_behavior",
                "prediction_space_behavior",
                "prediction_backspace_behavior"
            ),
            KeyboardSettingsSupport.keyAndGestureKeys
        )
    }
}
