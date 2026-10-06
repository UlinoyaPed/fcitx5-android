/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.bar

import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateBarHeightTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun fixedHeightDoesNotNotifyAndRetainsRequirementForDynamicHeight() = withPreferences(false) { bar ->
        var notifications = 0
        bar.onBarHeightChanged = { notifications++ }
        bar.setCandidateRequiredHeight(60)
        assertEquals(40, bar.candidateRowHeightDp)
        assertEquals(0, notifications)

        AppPrefs.getInstance().keyboard.toolbarDynamicHeight.setValue(true)
        assertEquals("Enabling dynamic height must use the latest requirement", 60, bar.candidateRowHeightDp)
        AppPrefs.getInstance().keyboard.toolbarDynamicHeight.setValue(false)
        bar.setCandidateRequiredHeight(0)
        assertEquals(40, bar.candidateRowHeightDp)
        assertEquals(0, notifications)
    }

    @Test
    fun candidatesWithinConfiguredHeightDoNotNotifyOnEntryUpdateOrExit() = withPreferences(true) { bar ->
        var notifications = 0
        bar.onBarHeightChanged = { notifications++ }
        for (required in listOf(0, 30, 30, 40, 25, 0)) {
            bar.setCandidateRequiredHeight(required)
            assertEquals(40, bar.candidateRowHeightDp)
        }
        assertEquals(0, notifications)
    }

    @Test
    fun oversizedCandidatesNotifyOnlyForActualGrowthAndShrink() = withPreferences(true) { bar ->
        val heights = mutableListOf<Int>()
        bar.onBarHeightChanged = { heights += bar.candidateRowHeightDp }
        for (required in listOf(0, 60, 60, 65, 30, 0)) {
            bar.setCandidateRequiredHeight(required)
        }
        assertEquals(listOf(60, 65, 40), heights)
        assertEquals(40, bar.candidateRowHeightDp)
    }

    private fun withPreferences(dynamic: Boolean, block: (KawaiiBarComponent) -> Unit) {
        instrumentation.runOnMainSync {
            val height = ThemeManager.prefs.toolbarHeight
            val dynamicHeight = AppPrefs.getInstance().keyboard.toolbarDynamicHeight
            val originalHeight = height.getValue()
            val originalDynamicHeight = dynamicHeight.getValue()
            try {
                height.setValue(40)
                dynamicHeight.setValue(dynamic)
                block(KawaiiBarComponent())
            } finally {
                height.setValue(originalHeight)
                dynamicHeight.setValue(originalDynamicHeight)
            }
        }
    }
}
