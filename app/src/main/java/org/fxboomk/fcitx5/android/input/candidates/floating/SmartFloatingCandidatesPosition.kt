/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.candidates.floating

internal data class SmartFloatingCandidatesPosition(
    val x: Float,
    val y: Float,
    val isAboveCursor: Boolean,
)

internal fun calculateSmartFloatingCandidatesPosition(
    parentWidth: Float,
    bottomLimit: Float,
    selfWidth: Float,
    selfHeight: Float,
    cursorX: Float,
    cursorTop: Float,
    cursorBottom: Float,
    gap: Float,
    isRtl: Boolean,
): SmartFloatingCandidatesPosition {
    fun validSize(value: Float) = if (value.isFinite()) value.coerceAtLeast(0f) else 0f

    val width = validSize(selfWidth)
    val height = validSize(selfHeight)
    val availableHeight = validSize(bottomLimit)
    val spacing = validSize(gap)
    val maxX = (validSize(parentWidth) - width).coerceAtLeast(0f)
    val maxY = (availableHeight - height).coerceAtLeast(0f)
    if (!cursorX.isFinite() || !cursorTop.isFinite() || !cursorBottom.isFinite()) {
        return SmartFloatingCandidatesPosition(0f, maxY, isAboveCursor = false)
    }

    val spaceAbove = (cursorTop - spacing).coerceIn(0f, availableHeight)
    val spaceBelow = (availableHeight - cursorBottom - spacing).coerceIn(0f, availableHeight)
    // Prefer below whenever it fits; otherwise use the side with more room.
    val isAboveCursor = height > spaceBelow && spaceAbove > spaceBelow
    val x = if (isRtl) cursorX - width else cursorX
    val y = if (isAboveCursor) cursorTop - spacing - height else cursorBottom + spacing
    return SmartFloatingCandidatesPosition(
        x = x.coerceIn(0f, maxX),
        y = y.coerceIn(0f, maxY),
        isAboveCursor = isAboveCursor,
    )
}
