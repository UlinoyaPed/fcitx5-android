/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

internal fun remainingSpaceMainGlyphScale(bandHeight: Float, minGap: Float, glyphHeight: Float): Float {
    if (!bandHeight.isFinite() || !minGap.isFinite() || !glyphHeight.isFinite() || glyphHeight <= 0f) return 0f
    val reserved = bandHeight - minGap * 2
    val available = if (reserved > 0f) reserved else (bandHeight - 2f).coerceAtLeast(0f)
    return (available / glyphHeight).coerceIn(0f, 1f)
}
