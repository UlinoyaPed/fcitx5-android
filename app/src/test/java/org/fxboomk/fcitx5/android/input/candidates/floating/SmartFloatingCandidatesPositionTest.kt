/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.candidates.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFloatingCandidatesPositionTest {

    @Test
    fun belowCursorKeepsTheSameGapWithOrWithoutPreedit() {
        for (height in listOf(180f, 220f)) {
            val result = position(height = height)

            assertEquals(124f, result.y, 0f)
            assertFalse(result.isAboveCursor)
        }
    }

    @Test
    fun aboveCursorKeepsTheBottomEdgeCloseWhenPreeditChangesHeight() {
        for (height in listOf(180f, 220f)) {
            val result = position(height = height, cursorTop = 820f, cursorBottom = 840f)

            assertEquals(812f, result.y + height, 0f)
            assertTrue(result.isAboveCursor)
        }
    }

    @Test
    fun movingCursorNearTheBottomSwitchesToAbove() {
        val below = position(height = 200f, cursorTop = 650f, cursorBottom = 670f)
        val above = position(height = 200f, cursorTop = 700f, cursorBottom = 720f)

        assertEquals(678f, below.y, 0f)
        assertFalse(below.isAboveCursor)
        assertEquals(492f, above.y, 0f)
        assertTrue(above.isAboveCursor)
    }

    @Test
    fun exactFitBelowRemainsBelowEvenWithMoreRoomAbove() {
        val result = position(height = 200f, cursorTop = 672f, cursorBottom = 692f)

        assertEquals(700f, result.y, 0f)
        assertFalse(result.isAboveCursor)
    }

    @Test
    fun leftAndRightEdgesStayInsideTheViewport() {
        assertEquals(0f, position(cursorX = -15f).x, 0f)
        assertEquals(400f, position(cursorX = 400f).x, 0f)
        assertEquals(700f, position(cursorX = 990f).x, 0f)
    }

    @Test
    fun rtlAlignsTheRightEdgeToTheCursorAndClampsBothEdges() {
        assertEquals(300f, position(cursorX = 600f, isRtl = true).x, 0f)
        assertEquals(0f, position(cursorX = 50f, isRtl = true).x, 0f)
        assertEquals(700f, position(cursorX = 1200f, isRtl = true).x, 0f)
    }

    @Test
    fun neitherSideFitsUsesTheLargerSideAndClampsVertically() {
        val above = position(bottomLimit = 300f, height = 230f, cursorTop = 180f, cursorBottom = 200f)
        val below = position(bottomLimit = 300f, height = 230f, cursorTop = 80f, cursorBottom = 100f)

        assertEquals(0f, above.y, 0f)
        assertTrue(above.isAboveCursor)
        assertEquals(70f, below.y, 0f)
        assertFalse(below.isAboveCursor)
    }

    @Test
    fun equalInsufficientSpacePrefersBelow() {
        val result = position(bottomLimit = 300f, height = 200f, cursorTop = 140f, cursorBottom = 160f)

        assertEquals(100f, result.y, 0f)
        assertFalse(result.isAboveCursor)
    }

    @Test
    fun oversizedWindowAndEmptyViewportHaveValidCoordinates() {
        val oversized = position(parentWidth = 100f, bottomLimit = 90f, height = 400f)
        val empty = position(parentWidth = 0f, bottomLimit = 0f)
        val negative = position(parentWidth = -1f, bottomLimit = -1f)

        for (result in listOf(oversized, empty, negative)) {
            assertEquals(0f, result.x, 0f)
            assertEquals(0f, result.y, 0f)
        }
    }

    @Test
    fun cursorOutsideTheVisibleAreaClampsToTheNearestEdge() {
        val aboveScreen = position(cursorTop = -100f, cursorBottom = -80f)
        val belowScreen = position(cursorTop = 1100f, cursorBottom = 1120f)

        assertEquals(0f, aboveScreen.y, 0f)
        assertFalse(aboveScreen.isAboveCursor)
        assertEquals(720f, belowScreen.y, 0f)
        assertTrue(belowScreen.isAboveCursor)
    }

    @Test
    fun nonFiniteCursorCoordinatesFallBackToTheBottomLeft() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val positions = listOf(
                position(cursorX = invalid),
                position(cursorTop = invalid),
                position(cursorBottom = invalid, isRtl = true),
            )
            for (result in positions) {
                assertEquals(SmartFloatingCandidatesPosition(0f, 720f, false), result)
            }
        }
    }

    private fun position(
        parentWidth: Float = 1000f,
        bottomLimit: Float = 900f,
        height: Float = 180f,
        cursorX: Float = 200f,
        cursorTop: Float = 96f,
        cursorBottom: Float = 116f,
        isRtl: Boolean = false,
    ) = calculateSmartFloatingCandidatesPosition(
        parentWidth = parentWidth,
        bottomLimit = bottomLimit,
        selfWidth = 300f,
        selfHeight = height,
        cursorX = cursorX,
        cursorTop = cursorTop,
        cursorBottom = cursorBottom,
        gap = 8f,
        isRtl = isRtl,
    )
}
