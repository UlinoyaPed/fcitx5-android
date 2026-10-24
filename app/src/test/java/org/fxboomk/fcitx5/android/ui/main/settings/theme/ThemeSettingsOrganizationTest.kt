/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */

package org.fxboomk.fcitx5.android.ui.main.settings.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsOrganizationTest {

    @Test
    fun appearanceSettingsAreGroupedByConfiguredObject() {
        assertEquals(
            listOf(
                "key_border",
                "key_border_stroke",
                "key_ripple_effect",
                "gboard_style_side_keys",
                "key_radius",
                "key_horizontal_margin",
                "key_vertical_margin",
                "punctuation_position",
                "uppercase_position"
            ),
            ThemeSettingsSupport.keyStyleKeys
        )
        assertEquals(
            listOf(
                "navbar_border",
                "toolbar_height",
                "navbar_radius"
            ),
            ThemeSettingsSupport.toolbarStyleKeys
        )
        assertEquals(
            listOf(
                "text_editing_button_radius",
                "clipboard_entry_radius"
            ),
            ThemeSettingsSupport.otherStyleKeys
        )
        assertEquals(
            listOf("hide_unsupported_emojis", "default_emoji_skin_tone"),
            ThemeSettingsSupport.candidateStyleKeys
        )
        assertEquals(
            listOf("disable_animation"),
            ThemeSettingsSupport.animationKeys
        )
        assertEquals(
            listOf(
                "follow_system_dark_mode",
                "light_mode_themes",
                "dark_mode_themes",
                "gboard_light_main_key_tone",
                "gboard_light_other_key_tone",
                "wallpaper_blend_percent"
            ),
            ThemeSettingsSupport.themeModeKeys
        )
    }

    @Test
    fun candidateAndAnimationSettingsMoveOutOfAdvanced() {
        assertEquals(
            listOf(
                "hide_unsupported_emojis",
                "default_emoji_skin_tone",
                "disable_animation"
            ),
            ThemeSettingsSupport.advancedMovedKeys
        )
    }

    @Test
    fun everyThemePreferenceAppearsInExactlyOneGroup() {
        val themeGroups = listOf(
            ThemeSettingsSupport.keyStyleKeys,
            ThemeSettingsSupport.toolbarStyleKeys,
            ThemeSettingsSupport.themeModeKeys,
            ThemeSettingsSupport.otherStyleKeys
        )
        val themeKeys = themeGroups.flatten()
        assertEquals(themeKeys.size, themeKeys.toSet().size)
        assertEquals(
            setOf(
                "key_border",
                "key_border_stroke",
                "key_ripple_effect",
                "gboard_style_side_keys",
                "key_radius",
                "key_horizontal_margin",
                "key_vertical_margin",
                "punctuation_position",
                "uppercase_position",
                "navbar_border",
                "toolbar_height",
                "navbar_radius",
                "text_editing_button_radius",
                "clipboard_entry_radius",
                "follow_system_dark_mode",
                "light_mode_themes",
                "dark_mode_themes",
                "gboard_light_main_key_tone",
                "gboard_light_other_key_tone",
                "wallpaper_blend_percent"
            ),
            themeKeys.toSet()
        )
        assertTrue(ThemeSettingsSupport.advancedMovedKeys.none { it in themeKeys })
        assertFalse(
            ThemeSettingsSupport.candidateStyleKeys.any {
                it in ThemeSettingsSupport.keyStyleKeys ||
                    it in ThemeSettingsSupport.toolbarStyleKeys ||
                    it in ThemeSettingsSupport.themeModeKeys ||
                    it in ThemeSettingsSupport.otherStyleKeys
            }
        )
    }
}
