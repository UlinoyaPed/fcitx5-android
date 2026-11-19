/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceLongPressBehaviorTest {

    @Test
    fun holdToTalkIsTheSecondSettingsOptionAfterNone() {
        assertEquals(
            listOf(
                SpaceLongPressBehavior.None,
                SpaceLongPressBehavior.VoiceHold,
                SpaceLongPressBehavior.Enumerate,
                SpaceLongPressBehavior.ShowPicker,
                SpaceLongPressBehavior.RepeatSpace,
                SpaceLongPressBehavior.ToggleActivate,
                SpaceLongPressBehavior.SwitchToEnglish
            ),
            SpaceLongPressBehavior.settingsEntries
        )
        assertEquals(
            SpaceLongPressBehavior.None,
            SpaceLongPressBehavior.settingsEntries.first()
        )
        assertEquals(
            SpaceLongPressBehavior.VoiceHold,
            SpaceLongPressBehavior.settingsEntries[1]
        )
    }

    @Test
    fun oldVoiceInputValueStillMeansHoldToTalk() {
        assertEquals(
            SpaceLongPressBehavior.VoiceHold,
            SpaceLongPressBehavior.fromPersistedName("VoiceInput")
        )
    }

    @Test
    fun pickerRemainsDistinctWhileLegacyActivationStillMigrates() {
        assertEquals(
            SpaceLongPressBehavior.ShowPicker,
            SpaceLongPressBehavior.fromPersistedName("ShowPicker")
        )
        assertEquals(
            SpaceLongPressBehavior.ToggleActivate,
            SpaceLongPressBehavior.fromPersistedName("ToggleActivate")
        )
    }

    @Test
    fun savedActivationDoesNotBecomeInputMethodEnumeration() {
        val saved = SpaceLongPressBehavior.fromPersistedName("ToggleActivate")
        org.junit.Assert.assertNotEquals(SpaceLongPressBehavior.Enumerate, saved)
        org.junit.Assert.assertTrue(saved in SpaceLongPressBehavior.settingsEntries)
    }
}
