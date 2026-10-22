/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SwipeActionTimingTest {
    private val idle = SwipeActionState(hasCode = false, hasCandidates = false)
    private val composing = SwipeActionState(hasCode = true, hasCandidates = true)
    private val predicting = SwipeActionState(hasCode = false, hasCandidates = true)
    private val codeOnly = SwipeActionState(hasCode = true, hasCandidates = false)
    private val allStates = listOf(idle, composing, predicting, codeOnly)

    private val all = listOf(MacroStep.Text("all"))
    private val idleSteps = listOf(MacroStep.Text("idle"))
    private val composingSteps = listOf(MacroStep.Text("composing"))

    private val codeOnlySteps = listOf(MacroStep.Text("code"))

    @Test
    fun eachTimingMatchesOnlyItsState() {
        assertEquals(listOf(idle), allStates.filter { SwipeActionTiming.NoCodeNoCandidates.matches(it) })
        assertEquals(listOf(predicting), allStates.filter { SwipeActionTiming.NoCodeCandidates.matches(it) })
        assertEquals(listOf(codeOnly), allStates.filter { SwipeActionTiming.CodeNoCandidates.matches(it) })
        assertEquals(listOf(composing), allStates.filter { SwipeActionTiming.CodeAndCandidates.matches(it) })
        assertEquals(listOf(composing, codeOnly), allStates.filter { SwipeActionTiming.CodeAll.matches(it) })
        assertEquals(allStates, allStates.filter { SwipeActionTiming.All.matches(it) })
    }

    @Test
    fun exactTimingBeatsCodeAll() {
        val action = MacroAction(
            all,
            mapOf(
                SwipeActionTiming.CodeAll to codeOnlySteps,
                SwipeActionTiming.CodeNoCandidates to listOf(MacroStep.Text("exact"))
            )
        )
        assertEquals(MacroAction(listOf(MacroStep.Text("exact"))), action.resolveSwipeAction(codeOnly))
        assertEquals(MacroAction(codeOnlySteps), action.resolveSwipeAction(composing))
        assertEquals(MacroAction(all), action.resolveSwipeAction(idle))
    }

    @Test
    fun codeWithoutCandidatesNoLongerFallsStraightToAll() {
        // A configured code timing covers every code state, so All is never reached.
        val action = MacroAction(all, mapOf(SwipeActionTiming.CodeAll to codeOnlySteps))
        assertEquals(MacroAction(codeOnlySteps), action.resolveSwipeAction(codeOnly))
        assertEquals(MacroAction(codeOnlySteps), action.resolveSwipeAction(composing))
        assertEquals(MacroAction(all), action.resolveSwipeAction(predicting))
    }

    @Test
    fun legacyMacroRunsUnchangedInEveryState() {
        val legacy = MacroAction(all)
        val emptyLegacy = MacroAction(emptyList())
        for (state in allStates) {
            assertSame(legacy, legacy.resolveSwipeAction(state))
            assertSame(emptyLegacy, emptyLegacy.resolveSwipeAction(state))
        }
    }

    @Test
    fun matchingTimingReplacesAllInsteadOfRunningWithIt() {
        val action = MacroAction(
            all,
            mapOf(
                SwipeActionTiming.NoCodeNoCandidates to idleSteps,
                SwipeActionTiming.CodeAndCandidates to composingSteps
            )
        )
        assertEquals(MacroAction(idleSteps), action.resolveSwipeAction(idle))
        assertEquals(MacroAction(composingSteps), action.resolveSwipeAction(composing))
        assertEquals(MacroAction(all), action.resolveSwipeAction(predicting))
        assertEquals(MacroAction(all), action.resolveSwipeAction(codeOnly))
    }

    @Test
    fun timingOnlyActionDoesNothingInOtherStates() {
        val action = MacroAction(emptyList(), mapOf(SwipeActionTiming.CodeAndCandidates to composingSteps))
        assertEquals(MacroAction(composingSteps), action.resolveSwipeAction(composing))
        assertNull(action.resolveSwipeAction(idle))
        assertNull(action.resolveSwipeAction(predicting))
        assertNull(action.resolveSwipeAction(codeOnly))
    }

    @Test
    fun explicitEmptyTimingBlocksAll() {
        val action = MacroAction(all, mapOf(SwipeActionTiming.CodeAndCandidates to emptyList()))
        assertNull(action.resolveSwipeAction(composing))
        assertEquals(MacroAction(all), action.resolveSwipeAction(idle))
    }
}
