/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */

package org.fxboomk.fcitx5.android.data.prefs

import org.fxboomk.fcitx5.android.input.candidates.floating.FloatingCandidatesMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateDisplayModeMigrationTest {

    @Test
    fun alwaysModeRemainsFloatingWindow() {
        assertEquals(
            FloatingCandidatesMode.Always,
            normalizeCandidateDisplayMode(FloatingCandidatesMode.Always)
        )
    }

    @Test
    fun legacyModesBecomeHorizontalCandidateBar() {
        listOf(
            null,
            FloatingCandidatesMode.InputDevice
        ).forEach { mode ->
            assertEquals(
                FloatingCandidatesMode.InputDevice,
                normalizeCandidateDisplayMode(mode)
            )
        }
    }

    @Test
    fun systemDefaultModeRemainsSystemDefault() {
        assertEquals(
            FloatingCandidatesMode.SystemDefault,
            normalizeCandidateDisplayMode(FloatingCandidatesMode.SystemDefault)
        )
    }
}
