/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

internal enum class PredictionBackspaceAction {
    SendToFcitx,
    DeleteText,
    ClearPredictionCandidates,
    ClearPredictionCandidatesAndDeleteText
}

/**
 * Decide what an on-screen backspace press should do while prediction candidates are shown.
 *
 * Priority, from highest to lowest:
 * 1. An active composition is always handled by the engine.
 * 2. The expanded AI window is app UI, so the first press only closes it.
 * 3. A built-in engine that owns a "backspace behavior on prediction" setting (Pinyin)
 *    decides by itself; the global [predictionBackspaceBehavior] is only a fallback.
 */
internal fun predictionBackspaceAction(
    hasPreedit: Boolean,
    hasNativePredictionCandidatesVisible: Boolean,
    hasAiPredictionCandidatesVisible: Boolean,
    hasAiExpandedCandidateWindowVisible: Boolean = false,
    hasEngineBackspaceBehaviorSetting: Boolean = false,
    predictionBackspaceBehavior: PredictionBackspaceBehavior,
): PredictionBackspaceAction = when {
    hasPreedit -> PredictionBackspaceAction.SendToFcitx
    hasAiExpandedCandidateWindowVisible -> PredictionBackspaceAction.ClearPredictionCandidates
    !hasNativePredictionCandidatesVisible && !hasAiPredictionCandidatesVisible ->
        PredictionBackspaceAction.SendToFcitx
    hasEngineBackspaceBehaviorSetting && hasNativePredictionCandidatesVisible ->
        PredictionBackspaceAction.SendToFcitx
    predictionBackspaceBehavior == PredictionBackspaceBehavior.DeleteText ->
        PredictionBackspaceAction.DeleteText
    predictionBackspaceBehavior == PredictionBackspaceBehavior.ClearPredictionCandidatesAndDeleteText ->
        PredictionBackspaceAction.ClearPredictionCandidatesAndDeleteText
    else -> PredictionBackspaceAction.ClearPredictionCandidates
}
