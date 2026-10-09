/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.input.candidates.floating.FloatingCandidatesMode
import org.fxboomk.fcitx5.android.input.predict.LlmPrefs
import org.junit.Assert.*
import org.junit.Test

class PersonalFeatureControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun newControlsDefaultOffAndCanBeChangedIndependently() {
        val storage = context.getSharedPreferences("personal_feature_controls_test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val prefs = AppPrefs(storage)
        val controls = listOf(
            prefs.keyboard.candidateIndexLabels, prefs.keyboard.hardwareDigitSelection,
            prefs.keyboard.hardwareSpaceSelection, prefs.keyboard.hardwarePredictionDismiss,
            prefs.keyboard.digitSwipeSelection, prefs.keyboard.dockToolbarWithFloatingCandidates,
            prefs.keyboard.punctuationPositionLettersOnly, prefs.candidates.reverseAboveCursor,
        )
        controls.forEach { assertFalse(it.getValue()) }
        controls.forEach { selected ->
            selected.setValue(true)
            controls.forEach { assertEquals(it === selected, it.getValue()) }
            selected.setValue(false)
        }
        storage.edit().clear().commit()
    }

    @Test
    fun storedAiSpacePreferenceSurvivesTheUpdate() {
        val storage = context.getSharedPreferences("personal_ai_space_test", Context.MODE_PRIVATE)
        storage.edit().clear().putBoolean(LlmPrefs.KEY_SPACE_COMMIT_PREDICTION, true).commit()
        assertTrue(LlmPrefs.read(storage).spaceCommitPrediction)
        assertTrue(storage.getBoolean(LlmPrefs.KEY_SPACE_COMMIT_PREDICTION, false))
        storage.edit().putBoolean(LlmPrefs.KEY_SPACE_COMMIT_PREDICTION, false).commit()
        assertFalse(LlmPrefs.read(storage).spaceCommitPrediction)
        storage.edit().clear().commit()
    }

    @Test
    fun everyControlIsDiscoverableInSettingsSearch() {
        instrumentation.runOnMainSync {
            val pref = AppPrefs.getInstance().candidates.mode
            val original = pref.getValue()
            try {
                pref.setValue(FloatingCandidatesMode.Always)
                val keys = SettingsSearchIndex.androidItems(context).mapNotNull { it.preferenceKey }.toSet()
                listOf(
                    "personal_candidate_index_labels", "personal_hardware_digit_selection",
                    "personal_hardware_space_selection", "personal_hardware_prediction_dismiss",
                    "personal_digit_swipe_selection", "personal_dock_toolbar",
                    "personal_punctuation_letters_only", "personal_reverse_above_cursor",
                    LlmPrefs.KEY_SPACE_COMMIT_PREDICTION,
                ).forEach { assertTrue("Missing control: $it", it in keys) }
            } finally {
                pref.setValue(original)
            }
        }
    }
}
