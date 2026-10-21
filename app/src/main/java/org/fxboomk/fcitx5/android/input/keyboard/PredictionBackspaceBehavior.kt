/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceEnum

/**
 * Global fallback for backspace while prediction candidates are visible.
 *
 * This preference has the lowest priority: an engine that exposes its own
 * "backspace behavior on prediction" setting (the built-in Pinyin engine) always
 * decides on its own first.
 */
enum class PredictionBackspaceBehavior(override val stringRes: Int) : ManagedPreferenceEnum {
    ClearPredictionCandidates(R.string.prediction_backspace_behavior_clear_prediction_candidates),
    ClearPredictionCandidatesAndDeleteText(
        R.string.prediction_backspace_behavior_clear_prediction_and_delete_text
    ),
    DeleteText(R.string.prediction_backspace_behavior_delete_text);
}
