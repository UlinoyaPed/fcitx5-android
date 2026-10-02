/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.candidates

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.FrameLayout
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.core.CandidateWord
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreference
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.candidates.horizontal.HorizontalCandidateViewAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateHighlightRadiusTest {

    @Test
    fun allCandidatePositionsUseSavedRadiusForActiveAndPressedBackgrounds() = withRadiusPreference { radius ->
        val adapter = HorizontalCandidateViewAdapter(theme)
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        val candidates = Array(3) { CandidateWord("", "candidate $it", "", false) }
        for (value in listOf(0, 12, 48, 0)) {
            radius.setValue(value)
            for (activeIndex in candidates.indices) {
                adapter.updateCandidates(candidates, candidates.size, activeIndex)
                for (position in candidates.indices) {
                    adapter.onBindViewHolder(holder, position)
                    holder.itemView.background.state = intArrayOf(android.R.attr.state_pressed)
                    val background = holder.itemView.background.current as GradientDrawable
                    assertEquals(expectedRadius(value), background.cornerRadius, 0.01f)
                    assertEquals(
                        if (position == activeIndex) theme.genericActiveBackgroundColor
                        else theme.keyPressHighlightColor,
                        background.color!!.defaultColor,
                    )
                    holder.itemView.background.state = intArrayOf()
                }
            }
        }
    }

    @Test
    fun firstCandidateFillAndBorderUseSavedRadiusWhenCandidateViewIsReused() =
        withRadiusPreference { radius ->
            val ui = CandidateItemUi(context, theme, Typeface.MONOSPACE)
            for (value in listOf(0, 12, 48, 0)) {
                radius.setValue(value)
                ui.applyFirstCandidateStyle(
                    bgColor = theme.genericActiveBackgroundColor,
                    strokeColor = theme.dividerColor,
                    pressColor = theme.keyPressHighlightColor,
                )
                for (state in listOf(intArrayOf(), intArrayOf(android.R.attr.state_pressed))) {
                    val background = ui.root.getChildAt(0).background.apply {
                        this.state = state
                    }.current as LayerDrawable
                    for (index in 0 until background.numberOfLayers) {
                        val layer = background.getDrawable(index) as GradientDrawable
                        assertEquals(expectedRadius(value), layer.cornerRadius, 0.01f)
                    }
                    val fill = background.getDrawable(0) as GradientDrawable
                    assertEquals(theme.genericActiveBackgroundColor, fill.color!!.defaultColor)
                }
                ui.resetToDefaultBackground(theme.keyPressHighlightColor)
            }
        }

    private fun expectedRadius(value: Int) = value * context.resources.displayMetrics.density

    private fun withRadiusPreference(block: (ManagedPreference.PInt) -> Unit) =
        instrumentation.runOnMainSync {
            val radius = AppPrefs.getInstance().candidates.candidateHighlightRadius
            val wasStored = radius.sharedPreferences.contains(radius.key)
            val original = radius.getValue()
            try {
                block(radius)
            } finally {
                if (wasStored) {
                    radius.setValue(original)
                } else {
                    radius.sharedPreferences.edit { remove(radius.key) }
                }
            }
        }

    companion object {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext
        private val theme = ThemePreset.MaterialLight
    }
}
