/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fxboomk.fcitx5.android.utils.addCategory

internal object ThemeSettingsSupport {

    val keyStyleKeys = listOf(
        "key_border",
        "key_border_stroke",
        "key_ripple_effect",
        "key_radius",
        "key_horizontal_margin",
        "key_vertical_margin",
        "gboard_style_side_keys",
        "punctuation_position",
        "uppercase_position"
    )

    val toolbarStyleKeys = listOf(
        "navbar_border",
        "toolbar_height",
        "navbar_radius",
        "text_editing_button_radius",
        "clipboard_entry_radius"
    )

    val candidateStyleKeys = listOf(
        "hide_unsupported_emojis",
        "default_emoji_skin_tone"
    )

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
     * but are presented by the appearance configuration page.
     */
    val advancedMovedKeys = candidateStyleKeys + animationKeys

    fun ManagedPreferenceFragment.addThemeSection(
        screen: PreferenceScreen,
        @StringRes title: Int,
        provider: ManagedPreferenceProvider,
        keys: List<String>
    ) {
        screen.addCategory(title) {
            keys.forEach { key ->
                addManagedPreference(this, provider, key)
            }
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
