/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.preview

import kotlinx.serialization.json.JsonArray
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutHeightPercentOverrides
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.LayoutJsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class KeyboardPreviewManagerTest {
    private val defaultRows = listOf(listOf(mapOf<String, Any?>("main" to "a")))
    private val subModeRows = listOf(listOf(mapOf<String, Any?>("main" to "A")))

    @Test
    fun missingBaseUsesDefaultRowsAsPreviewDefault() {
        val entries = mapOf(LayoutJsonUtils.DEFAULT_BASE_LAYOUT_KEY to defaultRows)
        val resolvedRows = resolvePreviewRows(entries, "virtual-ime", null)

        val result = buildPreviewSubModeMap(
            entries = entries,
            layoutName = "virtual-ime",
            subModeKey = null,
            currentRows = requireNotNull(resolvedRows),
            previewSubModeLabel = null
        )

        assertSame(defaultRows, resolvedRows)
        assertEquals(setOf("default"), result.keys)
        assertEquals(rowsJson(defaultRows), result.getValue("default"))
    }

    @Test
    fun missingBaseSubModeUsesDefaultEntryAsFallback() {
        val subModeKey = "virtual-ime:uppercase"
        val entries = mapOf(
            LayoutJsonUtils.DEFAULT_BASE_LAYOUT_KEY to defaultRows,
            subModeKey to subModeRows
        )

        val result = buildPreviewSubModeMap(
            entries = entries,
            layoutName = "virtual-ime",
            subModeKey = subModeKey,
            currentRows = subModeRows,
            previewSubModeLabel = "uppercase"
        )

        assertEquals(rowsJson(defaultRows), result.getValue("default"))
        assertEquals(rowsJson(subModeRows), result.getValue("uppercase"))
    }

    private fun rowsJson(rows: List<List<Map<String, Any?>>>): JsonArray =
        JsonArray(rows.map(LayoutJsonUtils::rowToJsonElement))

    @Test
    fun inheritedChildUsesBaseRowsAndOrientationSpecificHeights() {
        val entries = mapOf("default" to defaultRows, "ime" to subModeRows)
        val heights = mapOf(
            "default" to LayoutHeightPercentOverrides(80, 81),
            "ime" to LayoutHeightPercentOverrides(portrait = 42)
        )
        assertSame(subModeRows, resolvePreviewRows(entries, "ime", "ime:child"))
        assertEquals(
            LayoutHeightPercentOverrides(42, 31),
            resolvePreviewHeightOverrides(entries, "ime", "child", heights::get,
                LayoutHeightPercentOverrides(30, 31))
        )
        assertEquals(setOf("default", "ime"), entries.keys)
    }

    @Test
    fun childAndBaseHeightsFallBackIndependentlyInBothOrientations() {
        val entries = mapOf("ime" to defaultRows, "ime:child" to subModeRows)
        for (child in listOf(
            LayoutHeightPercentOverrides(portrait = 51),
            LayoutHeightPercentOverrides(landscape = 52)
        )) {
            val heights = mapOf("ime" to LayoutHeightPercentOverrides(41, 42), "ime:child" to child)
            assertEquals(
                LayoutHeightPercentOverrides(child.portrait ?: 41, child.landscape ?: 42),
                resolvePreviewHeightOverrides(entries, "ime", "child", heights::get,
                    LayoutHeightPercentOverrides(30, 31))
            )
        }
    }

    @Test
    fun explicitBaseWithoutHeightSkipsGlobalDefaultMetadata() {
        val entries = mapOf("default" to defaultRows, "ime" to subModeRows)
        val heights = mapOf("default" to LayoutHeightPercentOverrides(80, 81))
        assertEquals(
            LayoutHeightPercentOverrides(30, 31),
            resolvePreviewHeightOverrides(entries, "ime", "child", heights::get,
                LayoutHeightPercentOverrides(30, 31))
        )
        assertEquals(
            LayoutHeightPercentOverrides(),
            resolvePreviewHeightOverrides(entries, "ime", null, heights::get)
        )
    }

    @Test
    fun virtualBaseAndInheritedChildUseDefaultThenProfilePerOrientation() {
        val entries = mapOf("default" to defaultRows)
        for (default in listOf(
            LayoutHeightPercentOverrides(portrait = 41),
            LayoutHeightPercentOverrides(landscape = 42)
        )) {
            val heights = mapOf("default" to default)
            for (subMode in listOf(null, "", "child")) {
                assertEquals(
                    LayoutHeightPercentOverrides(default.portrait ?: 30, default.landscape ?: 31),
                    resolvePreviewHeightOverrides(entries, "ime", subMode, heights::get,
                        LayoutHeightPercentOverrides(30, 31))
                )
            }
        }
    }

    @Test
    fun childOnlyLayoutUsesChildThenDefaultPerOrientation() {
        val entries = mapOf("default" to defaultRows, "ime:child" to subModeRows)
        for (child in listOf(
            LayoutHeightPercentOverrides(portrait = 51),
            LayoutHeightPercentOverrides(landscape = 52)
        )) {
            val heights = mapOf("default" to LayoutHeightPercentOverrides(41, 42), "ime:child" to child)
            assertEquals(
                LayoutHeightPercentOverrides(child.portrait ?: 41, child.landscape ?: 42),
                resolvePreviewHeightOverrides(entries, "ime", "child", heights::get,
                    LayoutHeightPercentOverrides(30, 31))
            )
        }
    }

    @Test
    fun missingOrientationWithoutProfileRemainsUnsetForGlobalPreferenceFallback() {
        val entries = mapOf("default" to defaultRows, "ime:child" to subModeRows)
        val heights = mapOf("ime:child" to LayoutHeightPercentOverrides(portrait = 51))
        assertEquals(
            LayoutHeightPercentOverrides(portrait = 51),
            resolvePreviewHeightOverrides(entries, "ime", "child", heights::get)
        )
    }
}
