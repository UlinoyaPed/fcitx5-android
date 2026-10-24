/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.ui.main.settings.theme.ThemeSettingsSupport.addThemeSection

class ThemeSettingsFragment : ManagedPreferenceFragment(ThemeManager.prefs) {

    private val followSystemDayNightTheme = ThemeManager.prefs.followSystemDayNightTheme

    private lateinit var switchPreference: SwitchPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        switchPreference = findPreference(followSystemDayNightTheme.key)!!
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        screen.removeAll()
        val advanced = AppPrefs.getInstance().advanced
        addThemeSection(
            screen,
            R.string.theme_category_key_style,
            ThemeManager.prefs,
            ThemeSettingsSupport.keyStyleKeys
        )
        addThemeSection(
            screen,
            R.string.theme_category_toolbar_style,
            ThemeManager.prefs,
            ThemeSettingsSupport.toolbarStyleKeys
        )
        addThemeSection(
            screen,
            R.string.theme_category_candidate_style,
            advanced,
            ThemeSettingsSupport.candidateStyleKeys
        )
        addThemeSection(
            screen,
            R.string.theme_category_animation,
            advanced,
            ThemeSettingsSupport.animationKeys
        )
        addThemeSection(
            screen,
            R.string.theme_category_theme_mode,
            ThemeManager.prefs,
            ThemeSettingsSupport.themeModeKeys
        )
    }

    override fun onResume() {
        super.onResume()
        switchPreference.isChecked = followSystemDayNightTheme.getValue()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ThemeManager.syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }
}
