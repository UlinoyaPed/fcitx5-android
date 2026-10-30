/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreeditStyleOptionTest {

    private val pinyinValues = PreeditStyleOption.EngineValues(
        none = "Do not show",
        composing = "Composing pinyin",
        commitPreview = "Commit preview"
    )

    @Test
    fun engineValuesFollowThePreeditModeEnumOrder() {
        assertEquals(
            pinyinValues,
            PreeditStyleOption.EngineValues.fromEntries(
                listOf("Do not show", "Composing pinyin", "Commit preview")
            )
        )
        assertEquals(
            PreeditStyleOption.EngineValues(
                none = "Do not show",
                composing = "Composing text",
                commitPreview = "Commit preview"
            ),
            PreeditStyleOption.EngineValues.fromEntries(
                listOf("Do not show", "Composing text", "Commit preview")
            )
        )
        assertNull(PreeditStyleOption.EngineValues.fromEntries(listOf("Do not show")))
    }

    @Test
    fun noPreviewUsesTheAppCompositionAreaPreference() {
        assertEquals(
            PreeditStyleOption.STANDALONE,
            PreeditStyleOption.displayValue(
                "Do not show",
                inlineInCandidateBar = false,
                pinyinValues
            )
        )
        assertEquals(
            PreeditStyleOption.EMBEDDED,
            PreeditStyleOption.displayValue(
                "Do not show",
                inlineInCandidateBar = true,
                pinyinValues
            )
        )
    }

    @Test
    fun previewModesMapToTheirEngineValues() {
        assertEquals(
            PreeditStyleOption.COMPOSING,
            PreeditStyleOption.displayValue(
                "Composing pinyin",
                inlineInCandidateBar = true,
                pinyinValues
            )
        )
        assertEquals(
            PreeditStyleOption.COMPOSING,
            PreeditStyleOption.displayValue(
                "Composing text",
                inlineInCandidateBar = false,
                PreeditStyleOption.EngineValues(
                    none = "Do not show",
                    composing = "Composing text",
                    commitPreview = "Commit preview"
                )
            )
        )
        assertEquals(
            PreeditStyleOption.COMMIT_PREVIEW,
            PreeditStyleOption.displayValue(
                "Commit preview",
                inlineInCandidateBar = false,
                pinyinValues
            )
        )
    }

    @Test
    fun optionsAreGroupedByEditorPreviewBehavior() {
        assertEquals(
            listOf(
                listOf(PreeditStyleOption.STANDALONE, PreeditStyleOption.EMBEDDED),
                listOf(PreeditStyleOption.COMPOSING, PreeditStyleOption.COMMIT_PREVIEW)
            ),
            PreeditStyleOption.groups.map { it.values }
        )
        assertEquals(
            PreeditStyleOption.values,
            PreeditStyleOption.groups.flatMap { it.values }
        )
    }
}
