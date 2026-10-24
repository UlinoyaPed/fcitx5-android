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
import org.fxboomk.fcitx5.android.ui.main.settings.SettingsRoute

class KeyboardSettingsHomeFragment : ManagedPreferenceFragment(AppPrefs.getInstance().keyboard) {

    companion object {
        private const val BASIC_BEHAVIOR_ENTRY_KEY = "virtual_keyboard_basic_entry"
        private const val TOUCH_AND_SOUND_ENTRY_KEY = "virtual_keyboard_touch_and_sound_entry"
        private const val KEY_AND_GESTURE_ENTRY_KEY = "virtual_keyboard_key_and_gesture_entry"
        private const val LAYOUT_AND_SPLIT_ENTRY_KEY = "virtual_keyboard_layout_and_split_entry"
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        screen.removeAll()
        buildScreen(screen)
    }

    private fun buildScreen(screen: PreferenceScreen) {
        val keyboardPrefs = AppPrefs.getInstance().keyboard
        KeyboardSettingsSupport.run {
            screen.addDestinationPreference(
                this@KeyboardSettingsHomeFragment,
                R.string.keyboard_settings_basic_behavior,
                buildBasicBehaviorSummary(keyboardPrefs),
                SettingsRoute.VirtualKeyboardBasic,
                BASIC_BEHAVIOR_ENTRY_KEY
            )
            screen.addDestinationPreference(
                this@KeyboardSettingsHomeFragment,
                R.string.keyboard_settings_touch_and_sound,
                buildTouchAndSoundSummary(keyboardPrefs),
                SettingsRoute.VirtualKeyboardTouchAndSound,
                TOUCH_AND_SOUND_ENTRY_KEY
            )
            screen.addDestinationPreference(
                this@KeyboardSettingsHomeFragment,
                R.string.keyboard_settings_key_and_gesture,
                buildKeyAndGestureSummary(keyboardPrefs),
                SettingsRoute.VirtualKeyboardKeyAndGesture,
                KEY_AND_GESTURE_ENTRY_KEY
            )
            screen.addDestinationPreference(
                this@KeyboardSettingsHomeFragment,
                R.string.keyboard_settings_layout_and_split,
                buildLayoutSummary(keyboardPrefs),
                SettingsRoute.VirtualKeyboardLayoutAndSplit,
                LAYOUT_AND_SPLIT_ENTRY_KEY
            )
            screen.addDestinationPreference(
                this@KeyboardSettingsHomeFragment,
                R.string.keyboard_settings_advanced_customization,
                getString(R.string.keyboard_advanced_customization_summary),
                SettingsRoute.VirtualKeyboardAdvancedCustomization
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSummaries()
    }

    private fun refreshSummaries() {
        val keyboardPrefs = AppPrefs.getInstance().keyboard
        findPreference<Preference>(BASIC_BEHAVIOR_ENTRY_KEY)?.summary =
            buildBasicBehaviorSummary(keyboardPrefs)
        findPreference<Preference>(TOUCH_AND_SOUND_ENTRY_KEY)?.summary =
            buildTouchAndSoundSummary(keyboardPrefs)
        findPreference<Preference>(KEY_AND_GESTURE_ENTRY_KEY)?.summary =
            buildKeyAndGestureSummary(keyboardPrefs)
        findPreference<Preference>(LAYOUT_AND_SPLIT_ENTRY_KEY)?.summary =
            buildLayoutSummary(keyboardPrefs)
    }

    private fun buildBasicBehaviorSummary(keyboardPrefs: AppPrefs.Keyboard): String {
        val focusSummary = if (keyboardPrefs.focusChangeResetKeyboard.getValue()) {
            getString(R.string.focus_change_reset_enabled_summary)
        } else {
            getString(R.string.focus_change_reset_disabled_summary)
        }
        val inlineSummary = if (keyboardPrefs.inlineSuggestions.getValue()) {
            getString(R.string.inline_suggestions_enabled_summary)
        } else {
            getString(R.string.inline_suggestions_disabled_summary)
        }
        return "$focusSummary，$inlineSummary"
    }

    private fun buildTouchAndSoundSummary(keyboardPrefs: AppPrefs.Keyboard): String {
        val haptic = getString(keyboardPrefs.hapticOnKeyPress.getValue().stringRes)
        val sound = getString(keyboardPrefs.soundOnKeyPress.getValue().stringRes)
        return "$haptic，$sound"
    }

    private fun buildKeyAndGestureSummary(keyboardPrefs: AppPrefs.Keyboard): String {
        return getString(keyboardPrefs.langSwitchKeyBehavior.getValue().stringRes)
    }

    private fun buildLayoutSummary(keyboardPrefs: AppPrefs.Keyboard): String {
        return if (keyboardPrefs.splitKeyboardEnabled.getValue()) {
            getString(R.string.split_keyboard_enabled_summary_short)
        } else {
            getString(R.string.split_keyboard_disabled_summary)
        }
    }
}
