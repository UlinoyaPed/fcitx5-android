/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.fxboomk.fcitx5.android.input.predict.LlmPredictor

class HardwarePredictionSessionTest {
    @Test
    fun cancellationBlocksNativeAndRimeCandidatesWithoutBlockingComposition() {
        val session = HardwarePredictionSession()
        session.cancel()

        repeat(3) {
            assertTrue(session.blocksCandidates(hasPreedit = false))
            assertFalse(session.blocksCandidates(hasPreedit = true))
        }
        assertTrue(session.isSuppressed)
    }

    @Test
    fun emptyCommitAndRepeatedCancellationDoNotStartAnotherRound() {
        val session = HardwarePredictionSession()
        session.cancel()
        assertFalse(session.onTextCommitted(""))
        session.cancel()
        assertTrue(session.isSuppressed)
        assertTrue(session.onTextCommitted("新提交"))
        assertFalse(session.isSuppressed)
        assertFalse(session.onTextCommitted("下一次提交"))
    }

    @Test
    fun literalSpaceOrDigitSubmissionCanStartTheNextRound() {
        for (text in listOf(" ", "1", "0")) {
            val session = HardwarePredictionSession()
            session.cancel()
            assertTrue(session.onTextCommitted(text))
            assertFalse(session.blocksCandidates(hasPreedit = false))
        }
    }

    @Test
    fun cancelledAsyncResultStaysInvalidAfterNewCommit() {
        val session = HardwarePredictionSession()
        val requests = LlmPredictor.RequestTracker()
        val cancelledRequest = requests.replaceActiveRequest()
        session.cancel()
        requests.invalidate()

        assertTrue(session.onTextCommitted("你好"))
        val nextRequest = requests.replaceActiveRequest()
        assertFalse(requests.isActive(cancelledRequest))
        assertTrue(requests.isActive(nextRequest))
    }

    @Test
    fun newInputSessionDoesNotInheritCancellation() {
        val session = HardwarePredictionSession()
        session.cancel()
        session.reset()
        assertFalse(session.isSuppressed)
    }

    @Test
    fun dismissalCoversBackspaceAndEscapeOnly() {
        assertTrue(isHardwarePredictionDismissKey(KeyEvent.KEYCODE_DEL))
        assertTrue(isHardwarePredictionDismissKey(KeyEvent.KEYCODE_ESCAPE))
        assertFalse(isHardwarePredictionDismissKey(KeyEvent.KEYCODE_FORWARD_DEL))
        assertFalse(isHardwarePredictionDismissKey(KeyEvent.KEYCODE_A))
    }

    @Test
    fun hiddenPredictionProtectionIncludesZeroAndNumpadDigits() {
        for (key in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            assertTrue(isHardwarePredictionSelectionKey(key))
        }
        for (key in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            assertTrue(isHardwarePredictionSelectionKey(key))
        }
        assertTrue(isHardwarePredictionSelectionKey(KeyEvent.KEYCODE_SPACE))
        assertFalse(isHardwarePredictionSelectionKey(KeyEvent.KEYCODE_A))
    }

    @Test
    fun dismissalConsumesPendingRequestsAndNativePredictionsButPreservesOrdinaryKeys() {
        for (native in listOf(false, true)) {
            for (ai in listOf(false, true)) {
                val consume = shouldConsumeHardwarePredictionDismiss(false, native, ai, false)
                if (native || ai) assertTrue(consume) else assertFalse(consume)
                assertFalse(shouldConsumeHardwarePredictionDismiss(true, native, ai, false))
                assertFalse(shouldConsumeHardwarePredictionDismiss(false, native, ai, true))
            }
        }
    }
}
