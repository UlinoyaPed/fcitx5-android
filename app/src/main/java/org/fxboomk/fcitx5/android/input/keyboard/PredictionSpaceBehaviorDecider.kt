/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

internal fun shouldCommitPredictionOnSpace(
    hasVisibleCandidates: Boolean,
    hasNativePredictionCandidatesVisible: Boolean,
    hasAiPredictionCandidatesVisible: Boolean,
    predictionSpaceBehavior: PredictionSpaceBehavior,
): Boolean = (hasVisibleCandidates || hasAiPredictionCandidatesVisible) &&
    ((!hasNativePredictionCandidatesVisible && !hasAiPredictionCandidatesVisible) ||
        predictionSpaceBehavior == PredictionSpaceBehavior.CommitPrediction)
