/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HardwarePredictionKeyDeciderTest {

    @Test
    fun `maps top row and numpad digit keys to their index label`() {
        assertEquals(1, resolveHardwarePredictionDigit(KeyEvent.KEYCODE_1))
        assertEquals(9, resolveHardwarePredictionDigit(KeyEvent.KEYCODE_9))
        assertEquals(1, resolveHardwarePredictionDigit(KeyEvent.KEYCODE_NUMPAD_1))
        assertEquals(9, resolveHardwarePredictionDigit(KeyEvent.KEYCODE_NUMPAD_9))
    }

    @Test
    fun `does not map zero or non digit keys`() {
        assertNull(resolveHardwarePredictionDigit(KeyEvent.KEYCODE_0))
        assertNull(resolveHardwarePredictionDigit(KeyEvent.KEYCODE_NUMPAD_0))
        assertNull(resolveHardwarePredictionDigit(KeyEvent.KEYCODE_SPACE))
        assertNull(resolveHardwarePredictionDigit(KeyEvent.KEYCODE_A))
    }

    @Test
    fun `hardware space commits the first prediction in prediction state`() {
        assertEquals(
            true,
            shouldHardwareSpaceCommitPrediction(
                hasFloatingCandidates = true,
                hasCandidateBarItems = false,
                hasAiPredictionCandidatesVisible = false,
                hasPreedit = false,
            )
        )
        assertEquals(
            true,
            shouldHardwareSpaceCommitPrediction(
                hasFloatingCandidates = false,
                hasCandidateBarItems = true,
                hasAiPredictionCandidatesVisible = false,
                hasPreedit = false,
            )
        )
        assertEquals(
            true,
            shouldHardwareSpaceCommitPrediction(
                hasFloatingCandidates = false,
                hasCandidateBarItems = false,
                hasAiPredictionCandidatesVisible = true,
                hasPreedit = false,
            )
        )
    }

    @Test
    fun `hardware space keeps engine behavior while composing or without predictions`() {
        assertEquals(
            false,
            shouldHardwareSpaceCommitPrediction(
                hasFloatingCandidates = true,
                hasCandidateBarItems = true,
                hasAiPredictionCandidatesVisible = true,
                hasPreedit = true,
            )
        )
        assertEquals(
            false,
            shouldHardwareSpaceCommitPrediction(
                hasFloatingCandidates = false,
                hasCandidateBarItems = false,
                hasAiPredictionCandidatesVisible = false,
                hasPreedit = false,
            )
        )
    }
}
