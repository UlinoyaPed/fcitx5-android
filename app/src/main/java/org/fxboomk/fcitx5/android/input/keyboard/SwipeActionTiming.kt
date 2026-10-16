/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

/** Input state sampled when a swipe is released, independent of compose-key overrides. */
data class SwipeActionState(val hasCode: Boolean, val hasCandidates: Boolean)

enum class SwipeActionTiming(val id: String) {
    NoCodeNoCandidates("no_code_no_candidates"),
    CodeAndCandidates("code_and_candidates"),
    NoCodeCandidates("no_code_candidates"),
    All("all");

    fun matches(state: SwipeActionState): Boolean = when (this) {
        NoCodeNoCandidates -> !state.hasCode && !state.hasCandidates
        CodeAndCandidates -> state.hasCode && state.hasCandidates
        NoCodeCandidates -> !state.hasCode && state.hasCandidates
        All -> true
    }

    companion object {
        fun fromId(id: String): SwipeActionTiming? = entries.firstOrNull { it.id == id }
    }
}

/** A specific timing replaces the all-state action; it is never run in addition to it. */
fun MacroAction.resolveSwipeAction(state: SwipeActionState): MacroAction? {
    val timing = SwipeActionTiming.entries.firstOrNull {
        it != SwipeActionTiming.All && it.matches(state)
    }
    val selected = timing?.let { timingActions[it] }
    if (selected != null) return selected.takeIf { it.isNotEmpty() }?.let { MacroAction(it) }
    if (timingActions.isEmpty()) return this // Preserve legacy macros, including empty ones.
    return steps.takeIf { it.isNotEmpty() }?.let { MacroAction(it) }
}
