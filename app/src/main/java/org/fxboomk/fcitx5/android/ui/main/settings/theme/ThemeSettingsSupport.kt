/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceProvider

internal object ThemeSettingsSupport {

    val keyStyleThemeKeys = listOf(
        "key_border",
        "key_border_stroke",
        "key_ripple_effect",
        "gboard_style_side_keys",
        "key_radius",
        "key_horizontal_margin",
        "key_vertical_margin",
        "punctuation_position",
        "uppercase_position"
    )

    val keyStyleKeyboardKeys = listOf(
        "keep_keyboard_letters_uppercase",
        "show_lang_switch_key",
        "popup_on_key_press",
        "space_key_label_mode",
        "keyboard_height_percent",
        "keyboard_side_padding",
        "keyboard_bottom_padding"
    )

    val keyStyleKeys = keyStyleThemeKeys + keyStyleKeyboardKeys

    val toolbarStyleThemeKeys = listOf(
        "navbar_border",
        "toolbar_height",
        "navbar_radius"
    )

    val toolbarStyleKeyboardKeys = listOf(
        "toolbar_dynamic_height",
        "toolbar_num_row_on_password",
        "physical_keyboard_horizontal_candidate_bar",
        "show_voice_input_button"
    )

    val toolbarStyleKeys = toolbarStyleThemeKeys + toolbarStyleKeyboardKeys

    val otherStyleKeys = listOf(
        "text_editing_button_radius",
        "clipboard_entry_radius"
    )

    val candidateEmojiKeys = listOf(
        "hide_unsupported_emojis",
        "default_emoji_skin_tone"
    )

    val candidateHorizontalKeysBeforeAppearance = listOf(
        "preedit_style",
        "horizontal_candidate_style",
        "expanded_candidate_style"
    )

    val candidateHorizontalAppearanceKeys = listOf(
        "candidate_highlight_radius"
    )

    val candidateHorizontalKeysAfterAppearance = listOf(
        "expanded_candidate_grid_span_count_portrait"
    )

    val candidateHorizontalKeys = candidateHorizontalKeysBeforeAppearance +
        candidateHorizontalAppearanceKeys +
        candidateHorizontalKeysAfterAppearance

    val candidateWindowKeys = listOf(
        "candidates_window_orientation",
        "virtual_keyboard_candidates_position",
        "candidates_window_padding",
        "candidates_window_radius",
        "candidates_window_min_width"
    )

    val candidateItemKeys = listOf(
        "candidates_item_padding_vertical"
    )

    val candidateStyleKeys = candidateEmojiKeys +
        candidateHorizontalKeys +
        candidateWindowKeys +
        candidateItemKeys

    val animationKeys = listOf(
        "disable_animation"
    )

    val themeModeKeys = listOf(
        "follow_system_dark_mode",
        "light_mode_themes",
        "dark_mode_themes",
        "gboard_light_main_key_tone",
        "gboard_light_other_key_tone",
        "wallpaper_blend_percent"
    )

    /**
     * Preferences that keep living in [org.fxboomk.fcitx5.android.data.prefs.AppPrefs.Advanced]
     * but are presented by the appearance pages.
     */
    val advancedMovedKeys = candidateEmojiKeys + animationKeys

    fun ManagedPreferenceFragment.addThemePreferences(
        parent: PreferenceGroup,
        provider: ManagedPreferenceProvider,
        keys: List<String>
    ) {
        keys.forEach { key ->
            addManagedPreference(parent, provider, key)
        }
    }

    fun ManagedPreferenceFragment.addManagedPreference(
        parent: PreferenceGroup,
        provider: ManagedPreferenceProvider,
        key: String
    ): Preference? {
        val ui = provider.managedPreferencesUi.firstOrNull { it.key == key } ?: return null
        val preference = ui.createUi(parent.context).apply {
            isEnabled = ui.isEnabled()
        }
        parent.addPreference(preference)
        return preference
    }
}
