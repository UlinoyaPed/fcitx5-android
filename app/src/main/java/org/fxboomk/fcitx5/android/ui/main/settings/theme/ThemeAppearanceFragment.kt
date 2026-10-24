/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.ui.main.settings.SettingsRoute
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.FontsetEditorActivity
import org.fxboomk.fcitx5.android.utils.addPreference
import org.fxboomk.fcitx5.android.utils.navigateWithAnim

class ThemeAppearanceFragment : ManagedPreferenceFragment(ThemeManager.prefs) {

    companion object {
        private const val THEME_ENTRY_KEY = "theme_list_entry"
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        screen.removeAll()
        val ctx = requireContext()
        screen.addPreference(
            Preference(ctx).apply {
                key = THEME_ENTRY_KEY
                setTitle(R.string.theme)
                setSummary(ThemeManager.activeTheme.name)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceClickListener {
                    navigateWithAnim(SettingsRoute.ThemeList)
                    true
                }
            }
        )
        screen.addPreference(
            R.string.theme_category_theme_mode
        ) {
            navigateWithAnim(SettingsRoute.ThemeMode)
        }
        screen.addPreference(
            R.string.edit_fontset,
            R.string.edit_fontset_summary
        ) {
            startActivity(Intent(requireContext(), FontsetEditorActivity::class.java))
        }
        screen.addPreference(
            R.string.theme_category_key_style
        ) {
            navigateWithAnim(SettingsRoute.ThemeKeyStyle)
        }
        screen.addPreference(
            R.string.theme_category_toolbar_style
        ) {
            navigateWithAnim(SettingsRoute.ThemeToolbarStyle)
        }
        screen.addPreference(
            R.string.theme_category_candidate_style
        ) {
            navigateWithAnim(SettingsRoute.ThemeCandidateStyle)
        }
        screen.addPreference(
            R.string.theme_category_animation
        ) {
            navigateWithAnim(SettingsRoute.ThemeAnimation)
        }
        screen.addPreference(
            R.string.theme_category_other_style
        ) {
            navigateWithAnim(SettingsRoute.ThemeOtherStyle)
        }
    }

    override fun onResume() {
        super.onResume()
        findPreference<Preference>(THEME_ENTRY_KEY)?.summary = ThemeManager.activeTheme.name
    }
}
