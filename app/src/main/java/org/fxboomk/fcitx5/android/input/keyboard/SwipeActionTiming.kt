/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

/** Input state sampled when a swipe is released, independent of compose-key overrides. */
data class SwipeActionState(val hasCode: Boolean, val hasCandidates: Boolean)

/**
 * When a swipe action runs. The four exact timings each pin down one (hasCode, hasCandidates)
 * combination; [CodeAll] covers both has-code states and [All] covers everything else.
 */
enum class SwipeActionTiming(val id: String) {
    NoCodeNoCandidates("no_code_no_candidates"),
    NoCodeCandidates("no_code_candidates"),
    CodeNoCandidates("code_no_candidates"),
    CodeAndCandidates("code_and_candidates"),
    CodeAll("code_all"),
    All("all");

    /** More specific timings win over broader ones; the editor never makes two winners tie. */
    val specificity: Int
        get() = when (this) {
            NoCodeNoCandidates, NoCodeCandidates, CodeNoCandidates, CodeAndCandidates -> 2
            CodeAll -> 1
            All -> 0
        }

    fun matches(state: SwipeActionState): Boolean = when (this) {
        NoCodeNoCandidates -> !state.hasCode && !state.hasCandidates
        NoCodeCandidates -> !state.hasCode && state.hasCandidates
        CodeNoCandidates -> state.hasCode && !state.hasCandidates
        CodeAndCandidates -> state.hasCode && state.hasCandidates
        CodeAll -> state.hasCode
        All -> true
    }

    companion object {
        /** Timings a user can pick; [All] is the fallback and is stored outside the map. */
        val selectable: List<SwipeActionTiming> = entries.filter { it != All }

        fun fromId(id: String): SwipeActionTiming? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Pick the most specific configured timing for [state]. A configured timing replaces the broader
 * ones instead of running in addition to them, and an explicitly empty list blocks the fallback.
 */
fun MacroAction.resolveSwipeAction(state: SwipeActionState): MacroAction? {
    val timing = timingActions.keys
        .filter { it != SwipeActionTiming.All && it.matches(state) }
        .maxByOrNull { it.specificity }
    val selected = timing?.let { timingActions[it] }
    if (selected != null) return selected.takeIf { it.isNotEmpty() }?.let { MacroAction(it) }
    if (timingActions.isEmpty()) return this // Preserve legacy macros, including empty ones.
    return steps.takeIf { it.isNotEmpty() }?.let { MacroAction(it) }
}
