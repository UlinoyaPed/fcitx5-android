/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictionBackspaceBehaviorDeciderTest {

    private fun decide(
        hasPreedit: Boolean = false,
        hasNativePredictionCandidatesVisible: Boolean = false,
        hasAiPredictionCandidatesVisible: Boolean = false,
        hasAiExpandedCandidateWindowVisible: Boolean = false,
        hasEngineBackspaceBehaviorSetting: Boolean = false,
        predictionBackspaceBehavior: PredictionBackspaceBehavior =
            PredictionBackspaceBehavior.ClearPredictionCandidates,
    ) = predictionBackspaceAction(
        hasPreedit = hasPreedit,
        hasNativePredictionCandidatesVisible = hasNativePredictionCandidatesVisible,
        hasAiPredictionCandidatesVisible = hasAiPredictionCandidatesVisible,
        hasAiExpandedCandidateWindowVisible = hasAiExpandedCandidateWindowVisible,
        hasEngineBackspaceBehaviorSetting = hasEngineBackspaceBehaviorSetting,
        predictionBackspaceBehavior = predictionBackspaceBehavior,
    )

    @Test
    fun explicitAppChoiceRetainsItsMeaningForBuiltInEngine() {
        val expected = mapOf(
            PredictionBackspaceBehavior.DeleteText to PredictionBackspaceAction.DeleteText,
            PredictionBackspaceBehavior.ClearPredictionCandidates to PredictionBackspaceAction.ClearPredictionCandidates,
            PredictionBackspaceBehavior.ClearPredictionCandidatesAndDeleteText to PredictionBackspaceAction.ClearPredictionCandidatesAndDeleteText,
        )
        expected.forEach { (choice, action) ->
            assertEquals(action, predictionBackspaceAction(
                hasPreedit = false, hasNativePredictionCandidatesVisible = true,
                hasAiPredictionCandidatesVisible = false, hasEngineBackspaceBehaviorSetting = true,
                hasExplicitAppBackspaceBehavior = true, predictionBackspaceBehavior = choice,
            ))
        }
    }

    @Test
    fun preeditAlwaysGoesToFcitx() {
        assertEquals(
            PredictionBackspaceAction.SendToFcitx,
            decide(
                hasPreedit = true,
                hasNativePredictionCandidatesVisible = true,
                hasAiPredictionCandidatesVisible = true,
                hasEngineBackspaceBehaviorSetting = true,
                predictionBackspaceBehavior =
                    PredictionBackspaceBehavior.ClearPredictionCandidatesAndDeleteText,
            )
        )
    }

    @Test
    fun noPredictionCandidatesGoesToFcitx() {
        assertEquals(PredictionBackspaceAction.SendToFcitx, decide())
    }

    @Test
    fun defaultClearsNativePredictionCandidates() {
        assertEquals(
            PredictionBackspaceAction.ClearPredictionCandidates,
            decide(hasNativePredictionCandidatesVisible = true)
        )
    }

    @Test
    fun defaultClearsAiPredictionCandidates() {
        assertEquals(
            PredictionBackspaceAction.ClearPredictionCandidates,
            decide(hasAiPredictionCandidatesVisible = true)
        )
    }

    @Test
    fun clearPredictionAndDeleteTextClearsThenDeletes() {
        assertEquals(
            PredictionBackspaceAction.ClearPredictionCandidatesAndDeleteText,
            decide(
                hasNativePredictionCandidatesVisible = true,
                predictionBackspaceBehavior =
                    PredictionBackspaceBehavior.ClearPredictionCandidatesAndDeleteText,
            )
        )
    }

    @Test
    fun deleteTextSkipsPredictionClearing() {
        assertEquals(
            PredictionBackspaceAction.DeleteText,
            decide(
                hasNativePredictionCandidatesVisible = true,
                predictionBackspaceBehavior = PredictionBackspaceBehavior.DeleteText,
            )
        )
    }

    @Test
    fun builtInEngineSettingWinsOverGlobalBehavior() {
        for (behavior in PredictionBackspaceBehavior.entries) {
            assertEquals(
                behavior.name,
                PredictionBackspaceAction.SendToFcitx,
                decide(
                    hasNativePredictionCandidatesVisible = true,
                    hasEngineBackspaceBehaviorSetting = true,
                    predictionBackspaceBehavior = behavior,
                )
            )
        }
    }

    @Test
    fun engineSettingOnlyAppliesToEnginePredictionCandidates() {
        // AI predictions come from the app, so the global behavior still decides.
        assertEquals(
            PredictionBackspaceAction.ClearPredictionCandidatesAndDeleteText,
            decide(
                hasAiPredictionCandidatesVisible = true,
                hasEngineBackspaceBehaviorSetting = true,
                predictionBackspaceBehavior =
                    PredictionBackspaceBehavior.ClearPredictionCandidatesAndDeleteText,
            )
        )
    }

    @Test
    fun expandedAiCandidateWindowAlwaysClearsBeforeDeletingText() {
        for (behavior in PredictionBackspaceBehavior.entries) {
            for (engineSetting in listOf(false, true)) {
                assertEquals(
                    "${behavior.name}/$engineSetting",
                    PredictionBackspaceAction.ClearPredictionCandidates,
                    decide(
                        hasNativePredictionCandidatesVisible = false,
                        hasAiPredictionCandidatesVisible = true,
                        hasAiExpandedCandidateWindowVisible = true,
                        hasEngineBackspaceBehaviorSetting = engineSetting,
                        predictionBackspaceBehavior = behavior,
                    )
                )
            }
        }
    }
}
