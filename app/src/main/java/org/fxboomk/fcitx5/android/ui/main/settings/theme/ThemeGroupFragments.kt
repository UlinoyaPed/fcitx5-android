/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.os.Build
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.ui.main.settings.theme.ThemeSettingsSupport.addManagedPreference

abstract class ThemeGroupFragment(
    private val groupProvider: ManagedPreferenceProvider,
    private val keys: List<String>
) : ManagedPreferenceFragment(groupProvider) {

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        screen.removeAll()
        keys.forEach { key ->
            addManagedPreference(screen, groupProvider, key)
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ThemeManager.syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }
}

class ThemeKeyStyleFragment : ThemeGroupFragment(
    ThemeManager.prefs,
    ThemeSettingsSupport.keyStyleKeys
)

class ThemeToolbarStyleFragment : ThemeGroupFragment(
    ThemeManager.prefs,
    ThemeSettingsSupport.toolbarStyleKeys
)

class ThemeCandidateStyleFragment : ThemeGroupFragment(
    AppPrefs.getInstance().advanced,
    ThemeSettingsSupport.candidateStyleKeys
)

class ThemeAnimationFragment : ThemeGroupFragment(
    AppPrefs.getInstance().advanced,
    ThemeSettingsSupport.animationKeys
)

class ThemeModeFragment : ThemeGroupFragment(
    ThemeManager.prefs,
    ThemeSettingsSupport.themeModeKeys
) {

    private val followSystemDayNightTheme = ThemeManager.prefs.followSystemDayNightTheme

    override fun onResume() {
        super.onResume()
        findPreference<SwitchPreference>(followSystemDayNightTheme.key)?.isChecked =
            followSystemDayNightTheme.getValue()
    }
}

class ThemeOtherStyleFragment : ThemeGroupFragment(
    ThemeManager.prefs,
    ThemeSettingsSupport.otherStyleKeys
)
