/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import kotlin.math.min

internal data class MainLabelGeometry(val baseline: Float, val scale: Float)

/**
 * Place a row of main labels without letting ascenders or descenders decide the
 * optical position of the whole row.
 *
 * For a label on one key edge only, the shared x-height is centred on the key
 * face and then clamped so the full ink cannot collide with the sublabel. With
 * labels on both edges the free band is balanced instead, as there is no single
 * edge that should win.
 *
 * All band/face values share one coordinate space; ink and x-height bounds are
 * relative to a zero baseline.
 */
internal fun computeMainLabelGeometry(
    bandTop: Float,
    bandBottom: Float,
    faceCenter: Float,
    inkTop: Float,
    inkBottom: Float,
    xHeightTop: Float,
    xHeightBottom: Float,
    minGap: Float,
    centerOnFace: Boolean,
    scaleOverride: Float? = null,
    centerInkOnFace: Boolean = false,
): MainLabelGeometry {
    val inkHeight = (inkBottom - inkTop).coerceAtLeast(0.001f)
    val reserved = bandBottom - bandTop - minGap * 2f
    val availableHeight =
        if (reserved > 0f) reserved else (bandBottom - bandTop).coerceAtLeast(0f) - 2f
    val scale = (scaleOverride ?: min(1f, availableHeight / inkHeight)).coerceIn(0.001f, 1f)

    val scaledTop = inkTop * scale
    val scaledBottom = inkBottom * scale
    val balanced = (bandTop + bandBottom - scaledTop - scaledBottom) / 2f
    val centerTop = if (centerInkOnFace) inkTop else xHeightTop
    val centerBottom = if (centerInkOnFace) inkBottom else xHeightBottom
    val centered = faceCenter - (centerTop + centerBottom) * scale / 2f
    val lowerBound = bandTop + minGap - scaledTop
    val upperBound = bandBottom - minGap - scaledBottom
    val desired = if (centerOnFace) centered else balanced

    return MainLabelGeometry(
        baseline = if (lowerBound <= upperBound) desired.coerceIn(lowerBound, upperBound) else desired,
        scale = scale,
    )
}
