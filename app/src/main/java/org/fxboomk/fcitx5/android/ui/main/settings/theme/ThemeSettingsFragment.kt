/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.os.Bundle
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import androidx.preference.children
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fxboomk.fcitx5.android.data.theme.ThemeManager

class ThemeSettingsFragment : ManagedPreferenceFragment(ThemeManager.prefs) {

    private val followSystemDayNightTheme = ThemeManager.prefs.followSystemDayNightTheme

    private lateinit var switchPreference: SwitchPreference

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val candidates = AppPrefs.getInstance().candidates
        val theme = ThemeManager.prefs
        val preferences = screen.children.toMutableList()
        listOf(
            candidates.itemPaddingVertical.key to theme.keyVerticalMargin.key,
            candidates.candidateHighlightRadius.key to theme.navbarRadius.key
        ).forEach { (key, precedingKey) ->
            val ui = candidates.managedPreferencesUi.first { it.key == key }
            val preference = ui.createUi(screen.context).apply {
                isEnabled = ui.isEnabled()
            }
            val precedingIndex = preferences.indexOfFirst { it.key == precedingKey }
            preferences.add(precedingIndex + 1, preference)
            screen.addPreference(preference)
        }
        preferences.forEachIndexed { index, preference -> preference.order = index }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        switchPreference = findPreference(followSystemDayNightTheme.key)!!
    }

    override fun onResume() {
        super.onResume()
        switchPreference.isChecked = followSystemDayNightTheme.getValue()
    }
}
