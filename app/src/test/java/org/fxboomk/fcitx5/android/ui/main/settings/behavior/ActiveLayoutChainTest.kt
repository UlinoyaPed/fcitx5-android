/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import org.fxboomk.fcitx5.android.core.Action
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.core.InputMethodSubMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveLayoutChainTest {
    private val shuangpin = InputMethodEntry("shuangpin", "双拼", "", "", "", "zh", "pinyin", false)
    private val rime = InputMethodEntry("rime", "中州韵", "", "", "", "zh", "rime", false,
        "朙月拼音", "朙", "")
    private val schemaActions = arrayOf(schemaAction("朙月拼音"))

    private fun schemaAction(schema: String, name: String = "fcitx-rime-im") =
        Action(1, false, false, false, name, "", "A", schema, null)

    @Test fun missingImeOmitsChain() {
        assertNull(formatActiveLayoutChain("配置", null, schemaActions))
    }

    @Test fun shuangpinUsesActiveNameInsteadOfDefaultLayoutName() {
        assertEquals("配置-双拼", formatActiveLayoutChain("配置", shuangpin, emptyArray()))
    }

    @Test fun englishRemainsEnglish() {
        val english = shuangpin.copy(uniqueName = "keyboard-us", name = "English", addon = "keyboard")
        assertEquals("配置-English", formatActiveLayoutChain("配置", english, emptyArray()))
    }

    @Test fun missingDisplayNameFallsBackToImeId() {
        assertEquals("配置-shuangpin", formatActiveLayoutChain("配置", shuangpin.copy(name = ""), emptyArray()))
    }

    @Test fun rimeIncludesSchemaWithoutLayoutOrPluginLookup() {
        assertEquals("配置-中州韵-朙月拼音", formatActiveLayoutChain("配置", rime, schemaActions))
    }

    @Test fun rimeRecognizedByAddonRatherThanTranslatedImeName() {
        val renamed = rime.copy(uniqueName = "custom-ime", name = "中文输入法")
        assertEquals("配置-中文输入法-朙月拼音", formatActiveLayoutChain("配置", renamed, schemaActions))
    }

    @Test fun asciiModeStillShowsFullSchemaName() {
        val ime = rime.copy(subMode = InputMethodSubMode("Latin Mode", "abc", ""))
        assertEquals("配置-中州韵-朙月拼音", formatActiveLayoutChain("配置", ime, schemaActions))
    }

    @Test fun unavailableSchemaDoesNotUseModeIndicatorOrAppendEmptySegment() {
        val ime = rime.copy(subMode = InputMethodSubMode("Latin Mode", "ABC", ""))
        assertEquals("配置-中州韵", formatActiveLayoutChain("配置", ime, emptyArray()))
        assertEquals("配置-中州韵", formatActiveLayoutChain("配置", ime, arrayOf(schemaAction("  "))))
    }

    @Test fun unrelatedActionsAreNotMistakenForCurrentSchema() {
        val actions = arrayOf(schemaAction("Other action", "other")) + schemaActions
        assertEquals("配置-中州韵-朙月拼音", formatActiveLayoutChain("配置", rime, actions))
        assertEquals("配置-中州韵", formatActiveLayoutChain("配置", rime, actions.take(1).toTypedArray()))
    }

    @Test fun schemaWhitespaceIsTrimmed() {
        assertEquals("配置-中州韵-朙月拼音", formatActiveLayoutChain("配置", rime,
            arrayOf(schemaAction("  朙月拼音  "))))
    }

    @Test fun nonRimeSubModeAndStaleRimeActionAreNotDisplayedAsSchema() {
        val ime = shuangpin.copy(subMode = InputMethodSubMode("custom", "Custom", ""))
        assertEquals("配置-双拼", formatActiveLayoutChain("配置", ime, schemaActions))
    }

    @Test fun switchingSnapshotUpdatesProfileImeAndSchema() {
        assertEquals("配置-双拼", formatActiveLayoutChain("配置", shuangpin, emptyArray()))
        assertEquals("新配置-中州韵-朙月拼音", formatActiveLayoutChain("新配置", rime, schemaActions))
        val switched = rime.copy(subMode = InputMethodSubMode("double_pinyin", "双拼方案", ""))
        assertEquals("新配置-中州韵-双拼方案", formatActiveLayoutChain("新配置", switched, arrayOf(schemaAction("双拼方案"))))
    }
}
