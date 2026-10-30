/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import androidx.annotation.StringRes
import org.fxboomk.fcitx5.android.R

/**
 * The app-level preedit styles shown for the engines' `PreeditMode` option.
 *
 * The two "no preview" styles are stored in the app preference because the
 * engines only know whether the preedit should be sent to the editor. The two
 * preview styles map directly to the engine enum values.
 */
internal object PreeditStyleOption {
    const val STANDALONE = "Standalone"
    const val EMBEDDED = "Embedded"
    const val COMPOSING = "Composing"
    const val COMMIT_PREVIEW = "CommitPreview"

    /**
     * Raw values of the engine's `PreeditMode` enum.
     *
     * fcitx stores the enum labels themselves, not the C++ identifiers, so the
     * values are `"Do not show"`, `"Composing pinyin"` (pinyin) or
     * `"Composing text"` (rime), and `"Commit preview"`.
     */
    data class EngineValues(
        val none: String,
        val composing: String,
        val commitPreview: String
    ) {
        companion object {
            /** `PreeditMode` is declared in the order none, composing, commit preview. */
            fun fromEntries(entries: List<String>): EngineValues? =
                if (entries.size >= 3) {
                    EngineValues(entries[0], entries[1], entries[2])
                } else {
                    null
                }
        }
    }

    data class Group(@StringRes val title: Int, val values: List<String>)

    val groups = listOf(
        Group(
            R.string.preedit_style_group_no_preview,
            listOf(STANDALONE, EMBEDDED)
        ),
        Group(
            R.string.preedit_style_group_preview,
            listOf(COMPOSING, COMMIT_PREVIEW)
        )
    )

    val values = groups.flatMap { it.values }

    @StringRes
    fun labelRes(value: String): Int = when (value) {
        EMBEDDED -> R.string.preedit_style_inline_candidate_bar
        COMPOSING -> R.string.preedit_style_composing
        COMMIT_PREVIEW -> R.string.preedit_style_commit_preview
        else -> R.string.preedit_style_default
    }

    fun displayValue(
        engineValue: String?,
        inlineInCandidateBar: Boolean,
        engineValues: EngineValues
    ): String = when (engineValue) {
        engineValues.composing -> COMPOSING
        engineValues.commitPreview -> COMMIT_PREVIEW
        else -> if (inlineInCandidateBar) EMBEDDED else STANDALONE
    }
}
