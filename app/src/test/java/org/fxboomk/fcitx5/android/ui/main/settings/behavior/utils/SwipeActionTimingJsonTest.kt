/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.fxboomk.fcitx5.android.input.keyboard.MacroAction
import org.fxboomk.fcitx5.android.input.keyboard.MacroStep
import org.fxboomk.fcitx5.android.input.keyboard.SwipeActionTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeActionTimingJsonTest {
    private val timed = MacroAction(
        listOf(MacroStep.Text("all")),
        mapOf(
            SwipeActionTiming.NoCodeNoCandidates to listOf(MacroStep.Text("idle")),
            SwipeActionTiming.CodeAndCandidates to listOf(MacroStep.Text("a"), MacroStep.Text("b")),
            SwipeActionTiming.NoCodeCandidates to listOf(MacroStep.Text("predict"))
        )
    )

    @Test
    fun timingActionsRoundTrip() {
        val json = LayoutJsonUtils.macroActionToJson(timed)
        assertEquals(
            setOf("no_code_no_candidates", "code_and_candidates", "no_code_candidates"),
            json["timingActions"]!!.jsonObject.keys
        )
        assertEquals(timed, LayoutJsonUtils.parseMacroAction(json))
    }

    @Test
    fun legacyMacroKeepsItsJsonShape() {
        val legacy = MacroAction(listOf(MacroStep.Text("x")))
        val json = LayoutJsonUtils.macroActionToJson(legacy)
        assertEquals(setOf("macro"), json.keys)
        assertEquals(legacy, LayoutJsonUtils.parseMacroAction(json))
    }

    @Test
    fun explicitEmptyTimingSurvivesRoundTrip() {
        // Hand-written JSON may use an empty list to block the fallback to All.
        val action = MacroAction(listOf(MacroStep.Text("all")), mapOf(SwipeActionTiming.CodeAndCandidates to emptyList()))
        val json = LayoutJsonUtils.macroActionToJson(action)
        assertEquals(
            setOf("code_and_candidates"),
            json["timingActions"]!!.jsonObject.keys
        )
        assertEquals(action, LayoutJsonUtils.parseMacroAction(json))
    }

    @Test
    fun allAndUnknownTimingIdsAreIgnored() {
        val step = LayoutJsonUtils.macroActionToJson(MacroAction(listOf(MacroStep.Text("t"))))
        val json = JsonObject(
            mapOf(
                "macro" to JsonArray(emptyList()),
                "timingActions" to JsonObject(
                    mapOf("all" to step, "someday" to step, "code_and_candidates" to step)
                )
            )
        )
        assertEquals(
            MacroAction(
                emptyList(),
                mapOf(SwipeActionTiming.CodeAndCandidates to listOf(MacroStep.Text("t")))
            ),
            LayoutJsonUtils.parseMacroAction(json)
        )
    }
}
