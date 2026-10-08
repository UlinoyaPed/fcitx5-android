/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.candidates.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingCandidateNavigationTest {

    @Test
    fun upMovesFromBottomFirstCandidateToTopLastCandidateInReversedList() {
        // Screen order is candidate 3, 2, 1; candidate 1 starts highlighted.
        val second = nextFloatingCandidateIndex(0, -1, 3, reversed = true)
        assertEquals(1, second)
        val third = nextFloatingCandidateIndex(second!!, -1, 3, reversed = true)
        assertEquals(2, third)
        assertNull(nextFloatingCandidateIndex(third!!, -1, 3, reversed = true))
    }

    @Test
    fun downMovesFromTopToBottomAndStopsAtFirstCandidateInReversedList() {
        val second = nextFloatingCandidateIndex(2, 1, 3, reversed = true)
        assertEquals(1, second)
        val first = nextFloatingCandidateIndex(second!!, 1, 3, reversed = true)
        assertEquals(0, first)
        assertNull(nextFloatingCandidateIndex(first!!, 1, 3, reversed = true))
    }

    @Test
    fun normalAndHorizontalListsKeepTheirOriginalNavigation() {
        assertEquals(0, nextFloatingCandidateIndex(1, -1, 3, reversed = false))
        assertEquals(2, nextFloatingCandidateIndex(1, 1, 3, reversed = false))
        assertNull(nextFloatingCandidateIndex(0, -1, 3, reversed = false))
        assertNull(nextFloatingCandidateIndex(2, 1, 3, reversed = false))
    }

    @Test
    fun movingWindowBelowCursorRestoresDirectionForTheSameHighlightedCandidate() {
        assertEquals(2, nextFloatingCandidateIndex(1, -1, 3, reversed = true))
        assertEquals(0, nextFloatingCandidateIndex(1, -1, 3, reversed = false))
    }

    @Test
    fun multiStepVerticalSwipeClampsAtVisualEnds() {
        assertEquals(4, nextFloatingCandidateIndex(1, -10, 5, reversed = true))
        assertEquals(0, nextFloatingCandidateIndex(3, 10, 5, reversed = true))
    }

    @Test
    fun emptyOrSingleCandidateAndZeroMovementDoNotChangeHighlight() {
        for (reversed in listOf(false, true)) {
            assertNull(nextFloatingCandidateIndex(-1, -1, 0, reversed))
            assertNull(nextFloatingCandidateIndex(0, -1, 1, reversed))
            assertNull(nextFloatingCandidateIndex(0, 1, 1, reversed))
            assertNull(nextFloatingCandidateIndex(1, 0, 3, reversed))
        }
    }

    @Test
    fun unsetHighlightStillSelectsAValidCandidate() {
        assertEquals(0, nextFloatingCandidateIndex(-1, -1, 3, reversed = true))
        assertEquals(0, nextFloatingCandidateIndex(-1, 1, 3, reversed = false))
    }
}
