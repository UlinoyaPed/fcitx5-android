/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fxboomk.fcitx5.android.utils.addCategory
import org.fxboomk.fcitx5.android.utils.addPreference

abstract class KeyboardSectionFragment : ManagedPreferenceFragment(AppPrefs.getInstance().keyboard) {
    final override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        screen.removeAll()
        onBuildPreferenceScreen(screen)
    }

    protected abstract fun onBuildPreferenceScreen(screen: PreferenceScreen)
}

class KeyboardBasicSettingsFragment : KeyboardSectionFragment() {
    override fun onBuildPreferenceScreen(screen: PreferenceScreen) {
        KeyboardSettingsSupport.run {
            addKeyboardSection(screen, R.string.keyboard_settings_basic_behavior, basicBehaviorKeys)
        }
    }
}

class KeyboardTouchAndSoundSettingsFragment : KeyboardSectionFragment() {
    override fun onBuildPreferenceScreen(screen: PreferenceScreen) {
        KeyboardSettingsSupport.run {
            addKeyboardSection(screen, R.string.keyboard_settings_touch_and_sound, touchAndSoundKeys)
        }
    }
}

class KeyboardKeyAndGestureSettingsFragment : KeyboardSectionFragment() {
    override fun onBuildPreferenceScreen(screen: PreferenceScreen) {
        KeyboardSettingsSupport.run {
            addKeyboardSection(screen, R.string.keyboard_settings_key_and_gesture, keyAndGestureKeys)
        }
    }
}

class KeyboardLayoutAndSplitSettingsFragment : KeyboardSectionFragment() {

    private var calibrationPreference: Preference? = null
    private var useLandscapePreference: Preference? = null

    private val onSplitEnabledChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (key == KeyboardSettingsSupport.SPLIT_ENABLED_KEY) {
            val enabled = AppPrefs.getInstance().keyboard.splitKeyboardEnabled.getValue()
            calibrationPreference?.isEnabled = enabled
            useLandscapePreference?.isEnabled = enabled
        }
    }

    override fun onBuildPreferenceScreen(screen: PreferenceScreen) {
        KeyboardSettingsSupport.run {
            addKeyboardSection(screen, R.string.keyboard_settings_layout_and_split, layoutKeys)
        }
        calibrationPreference = KeyboardSettingsSupport.createSplitKeyboardCalibrationPreference(this)
        screen.addCategory(R.string.keyboard_settings_split_tools_section) {
            calibrationPreference?.let(::addPreference)
        }
        useLandscapePreference = screen.findPreference("split_keyboard_use_landscape_layout")
        useLandscapePreference?.isEnabled = AppPrefs.getInstance().keyboard.splitKeyboardEnabled.getValue()
        AppPrefs.getInstance().keyboard.registerOnChangeListener(onSplitEnabledChangeListener)
    }

    override fun onDestroy() {
        AppPrefs.getInstance().keyboard.unregisterOnChangeListener(onSplitEnabledChangeListener)
        super.onDestroy()
    }
}
