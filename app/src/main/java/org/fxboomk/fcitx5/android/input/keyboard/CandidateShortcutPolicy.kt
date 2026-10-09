/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.KeyEvent

internal fun isHardwareCandidateShortcutEnabled(
    keyCode: Int,
    digitSelectionEnabled: Boolean,
    spaceSelectionEnabled: Boolean,
): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_SPACE -> spaceSelectionEnabled
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> digitSelectionEnabled
    else -> false
}

internal fun digitSwipeCandidateIndex(text: String, enabled: Boolean, hasNativeCandidates: Boolean): Int? {
    if (!enabled || !hasNativeCandidates) return null
    val digit = text.singleOrNull()?.takeIf { it in '0'..'9' } ?: return null
    return if (digit == '0') 9 else digit - '1'
}

internal fun shouldCommitAiPredictionOnSpace(hasPreedit: Boolean, visible: Boolean, enabled: Boolean): Boolean =
    !hasPreedit && visible && enabled
