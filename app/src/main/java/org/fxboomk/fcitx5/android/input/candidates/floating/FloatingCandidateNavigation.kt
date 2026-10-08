/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.candidates.floating

/** [delta] follows screen direction: negative moves up, positive moves down. */
internal fun nextFloatingCandidateIndex(
    currentIndex: Int,
    delta: Int,
    candidateCount: Int,
    reversed: Boolean,
): Int? {
    if (delta == 0 || candidateCount == 0) return null
    val indexDelta = if (reversed) -delta else delta
    val next = (currentIndex + indexDelta).coerceIn(0, candidateCount - 1)
    return next.takeUnless { it == currentIndex }
}
