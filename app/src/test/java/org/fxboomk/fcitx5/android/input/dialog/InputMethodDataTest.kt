/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.dialog

import org.fxboomk.fcitx5.android.core.Action
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.input.keyboard.LangSwitchLongPressBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMethodDataTest {
    private val enabled = arrayOf(
        InputMethodEntry("keyboard-us", "English", "", "", "", "en", "androidkeyboard", false),
        InputMethodEntry("rime", "Rime", "", "", "", "zh", "rime", false),
        InputMethodEntry("pinyin", "Pinyin", "", "", "", "zh", "pinyin", false)
    )
    private val schemas = arrayOf(schema(10, "朙月拼音"), schema(20, "小鹤双拼"))

    @Test
    fun defaultKeepsEnabledEngineOrderWithoutExpandingRime() {
        val entries = entries(LangSwitchLongPressBehavior.Default)
        assertEquals(enabled.map { it.uniqueName }, entries.map { it.uniqueName })
        assertTrue(entries.all { it.rimeSchemaActionId == null })
        assertFalse(LangSwitchLongPressBehavior.Default.includesRimeSchemas)
    }

    @Test
    fun builtInOnlyRetainsFcitxManagedRimeEngineWithoutItsSchemas() {
        val entries = entries(LangSwitchLongPressBehavior.BuiltInOnly)
        assertEquals(listOf("keyboard-us", "rime", "pinyin"), entries.map { it.uniqueName })
        assertTrue(entries.all { !it.ime && it.rimeSchemaActionId == null })
        assertFalse(LangSwitchLongPressBehavior.BuiltInOnly.includesRimeSchemas)
    }

    @Test
    fun builtInAndRimeAppendsSelectableSchemasToEnabledEngines() {
        val entries = entries(LangSwitchLongPressBehavior.BuiltInAndRime)
        assertEquals(listOf("keyboard-us", "rime", "rime-schema:10", "rime-schema:20", "pinyin"),
            entries.map { it.uniqueName })
        assertEquals(listOf("朙月拼音", "小鹤双拼"), entries.subList(2, 4).map { it.name })
        assertEquals(listOf(10, 20), entries.subList(2, 4).map { it.rimeSchemaActionId })
        assertTrue(entries.none { it.isGroupHeader })
        assertTrue(entries.none { it.ime })
    }

    @Test
    fun rimeOnlyExcludesEveryFcitxEngine() {
        val entries = entries(LangSwitchLongPressBehavior.RimeOnly)
        assertEquals("Rime", entries.first().name)
        assertTrue(entries.first().isGroupHeader)
        assertEquals(listOf(10, 20), entries.drop(1).map { it.rimeSchemaActionId })
        assertTrue(entries.drop(1).none { it.isGroupHeader })
        assertTrue(entries.none { it.ime })
    }

    @Test
    fun missingPluginOrUnavailableSchemasNeverFallsBackToOtherEnginesInRimeOnly() {
        assertTrue(InputMethodData.internalEntries(enabled, emptyArray(), LangSwitchLongPressBehavior.RimeOnly).isEmpty())
        assertEquals(enabled.size,
            InputMethodData.internalEntries(enabled, emptyArray(), LangSwitchLongPressBehavior.BuiltInAndRime).size)
    }

    @Test
    fun schemasWithIdenticalLabelsKeepDistinctActions() {
        val entries = InputMethodData.internalEntries(enabled,
            arrayOf(schema(10, "拼音"), schema(20, "拼音")), LangSwitchLongPressBehavior.RimeOnly)
        assertEquals(2, entries.drop(1).map { it.uniqueName }.distinct().size)
        assertEquals(listOf(10, 20), entries.drop(1).map { it.rimeSchemaActionId })
    }

    @Test
    fun unavailableSchemasDoNotCreateEmptyGroups() {
        val entries = InputMethodData.internalEntries(enabled, emptyArray(), LangSwitchLongPressBehavior.BuiltInAndRime)
        assertEquals(listOf("keyboard-us", "rime", "pinyin"), entries.map { it.uniqueName })
        assertTrue(entries.none { it.isGroupHeader })
    }

    private fun entries(behavior: LangSwitchLongPressBehavior) =
        InputMethodData.internalEntries(enabled, schemas, behavior)

    private fun schema(id: Int, label: String) =
        Action(id, false, false, false, "", "", label, "", null)
}
