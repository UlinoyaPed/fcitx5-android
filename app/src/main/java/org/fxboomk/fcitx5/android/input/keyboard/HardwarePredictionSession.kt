/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.KeyEvent

/** Cancellation belongs to the input session, not to the lifetime of a candidate view. */
internal class HardwarePredictionSession {
    var isSuppressed = false
        private set

    fun cancel() {
        isSuppressed = true
    }

    fun onTextCommitted(text: String): Boolean {
        if (!isSuppressed || text.isEmpty()) return false
        isSuppressed = false
        return true
    }

    fun reset() {
        isSuppressed = false
    }

    fun blocksCandidates(hasPreedit: Boolean): Boolean = isSuppressed && !hasPreedit
}

internal fun isHardwarePredictionDismissKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_ESCAPE

internal fun isHardwarePredictionSelectionKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_SPACE ||
        keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
        keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9

internal fun shouldConsumeHardwarePredictionDismiss(
    hasPreedit: Boolean,
    hasNativeCandidates: Boolean,
    hasAiRequestOrCandidates: Boolean,
    hasModifiers: Boolean,
): Boolean = !hasPreedit && !hasModifiers && (hasNativeCandidates || hasAiRequestOrCandidates)
