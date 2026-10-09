/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class CandidateShortcutPolicyTest {
    @Test
    fun disabledShortcutsLeaveEveryHardwareKeyToTheEngine() {
        (0..KeyEvent.KEYCODE_NUMPAD_9).forEach {
            assertFalse(isHardwareCandidateShortcutEnabled(it, false, false))
        }
    }

    @Test
    fun digitAndSpaceSelectionAreIndependent() {
        listOf(KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_NUMPAD_9).forEach {
            assertTrue(isHardwareCandidateShortcutEnabled(it, true, false))
            assertFalse(isHardwareCandidateShortcutEnabled(it, false, true))
        }
        assertFalse(isHardwareCandidateShortcutEnabled(KeyEvent.KEYCODE_SPACE, true, false))
        assertTrue(isHardwareCandidateShortcutEnabled(KeyEvent.KEYCODE_SPACE, false, true))
        assertFalse(isHardwareCandidateShortcutEnabled(KeyEvent.KEYCODE_A, true, true))
    }

    @Test
    fun digitSwipesNeedBothOptInAndNativeCandidates() {
        ('0'..'9').forEach {
            assertNull(digitSwipeCandidateIndex(it.toString(), false, true))
            assertNull(digitSwipeCandidateIndex(it.toString(), true, false))
        }
        assertEquals(9, digitSwipeCandidateIndex("0", true, true))
        assertEquals(0, digitSwipeCandidateIndex("1", true, true))
        assertEquals(8, digitSwipeCandidateIndex("9", true, true))
    }

    @Test
    fun customTextAndUnicodeDigitsStayLiteral() {
        listOf("", "12", "１", "١", "a", "\u00001").forEach {
            assertNull(digitSwipeCandidateIndex(it, true, true))
        }
    }

    @Test
    fun aiSpaceRequiresItsOwnSettingAndNeverStealsComposition() {
        assertFalse(shouldCommitAiPredictionOnSpace(false, true, false))
        assertFalse(shouldCommitAiPredictionOnSpace(true, true, true))
        assertFalse(shouldCommitAiPredictionOnSpace(false, false, true))
        assertTrue(shouldCommitAiPredictionOnSpace(false, true, true))
    }
}
