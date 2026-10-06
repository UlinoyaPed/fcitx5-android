/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionSpaceBehaviorDeciderTest {

    @Test
    fun commitSpaceDisablesPredictionCommitForNativePredictionCandidates() {
        assertFalse(
            shouldCommitPredictionOnSpace(
                hasVisibleCandidates = true,
                hasNativePredictionCandidatesVisible = true,
                hasAiPredictionCandidatesVisible = false,
                predictionSpaceBehavior = PredictionSpaceBehavior.CommitSpace,
            )
        )
    }

    @Test
    fun commitPredictionKeepsExistingPredictionCommitBehavior() {
        assertTrue(
            shouldCommitPredictionOnSpace(
                hasVisibleCandidates = true,
                hasNativePredictionCandidatesVisible = true,
                hasAiPredictionCandidatesVisible = false,
                predictionSpaceBehavior = PredictionSpaceBehavior.CommitPrediction,
            )
        )
    }

    @Test
    fun nonPredictionCandidatesStillCommitOnSpace() {
        assertTrue(
            shouldCommitPredictionOnSpace(
                hasVisibleCandidates = true,
                hasNativePredictionCandidatesVisible = false,
                hasAiPredictionCandidatesVisible = false,
                predictionSpaceBehavior = PredictionSpaceBehavior.CommitSpace,
            )
        )
    }

    @Test
    fun noCandidatesMeansNoCommit() {
        assertFalse(
            shouldCommitPredictionOnSpace(
                hasVisibleCandidates = false,
                hasNativePredictionCandidatesVisible = true,
                hasAiPredictionCandidatesVisible = false,
                predictionSpaceBehavior = PredictionSpaceBehavior.CommitPrediction,
            )
        )
    }

    @Test
    fun commitSpaceDisablesAiPredictionCommitInEveryPresentation() {
        for (hasVisibleCandidates in listOf(false, true)) {
            for (hasNativePredictionCandidatesVisible in listOf(false, true)) {
                assertFalse(
                    shouldCommitPredictionOnSpace(
                        hasVisibleCandidates = hasVisibleCandidates,
                        hasNativePredictionCandidatesVisible = hasNativePredictionCandidatesVisible,
                        hasAiPredictionCandidatesVisible = true,
                        predictionSpaceBehavior = PredictionSpaceBehavior.CommitSpace,
                    )
                )
            }
        }
    }

    @Test
    fun commitPredictionEnablesAiPredictionCommitInEveryPresentation() {
        for (hasVisibleCandidates in listOf(false, true)) {
            for (hasNativePredictionCandidatesVisible in listOf(false, true)) {
                assertTrue(
                    shouldCommitPredictionOnSpace(
                        hasVisibleCandidates = hasVisibleCandidates,
                        hasNativePredictionCandidatesVisible = hasNativePredictionCandidatesVisible,
                        hasAiPredictionCandidatesVisible = true,
                        predictionSpaceBehavior = PredictionSpaceBehavior.CommitPrediction,
                    )
                )
            }
        }
    }
}
