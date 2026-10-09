/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.junit.Assert.*
import org.junit.Test

class RemainingSpaceGlyphScaleTest {
    @Test
    fun collapsedOrOverlappingBandsNeverInvertTheGlyph() {
        listOf(-10f, 0f, 0.5f, 1f, 2f).forEach {
            assertEquals(0f, remainingSpaceMainGlyphScale(it, 4f, 12f), 0f)
        }
    }

    @Test
    fun thinBandRetainsVisibleInkWithoutReservedGaps() {
        assertEquals(0.25f, remainingSpaceMainGlyphScale(5f, 4f, 12f), 0f)
    }

    @Test
    fun normalBandReservesGapsAndNeverEnlargesTheFont() {
        assertEquals(0.5f, remainingSpaceMainGlyphScale(14f, 4f, 12f), 0f)
        assertEquals(1f, remainingSpaceMainGlyphScale(50f, 4f, 12f), 0f)
    }

    @Test
    fun invalidMetricsProduceAFiniteScale() {
        assertEquals(0f, remainingSpaceMainGlyphScale(Float.NaN, 4f, 12f), 0f)
        assertEquals(0f, remainingSpaceMainGlyphScale(20f, Float.POSITIVE_INFINITY, 12f), 0f)
        assertEquals(0f, remainingSpaceMainGlyphScale(20f, 4f, 0f), 0f)
    }
}
