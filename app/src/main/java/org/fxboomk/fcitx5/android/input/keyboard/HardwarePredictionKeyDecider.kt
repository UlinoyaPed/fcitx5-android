/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.KeyEvent

/**
 * Digit (1-9) carried by a hardware number-key event, matching the index labels drawn on
 * candidate bar items; `null` when the key carries no selectable digit.
 */
internal fun resolveHardwarePredictionDigit(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
    else -> null
}

/**
 * On a physical keyboard, space always commits the item carrying index label 1 while the
 * prediction state is showing (no preedit), regardless of the prediction space behavior
 * setting that governs the virtual keyboard. The label-1 item is the first prediction
 * shown: the floating candidates window's highlighted item when it is up, otherwise the
 * candidate bar's first displayed item, otherwise the primary AI suggestion from the
 * floating bubble.
 */
internal fun shouldHardwareSpaceCommitPrediction(
    hasFloatingCandidates: Boolean,
    hasCandidateBarItems: Boolean,
    hasAiPredictionCandidatesVisible: Boolean,
    hasPreedit: Boolean,
): Boolean = (hasFloatingCandidates || hasCandidateBarItems || hasAiPredictionCandidatesVisible) &&
    !hasPreedit
