/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.allViews
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class ComposeOverrideTextMetricsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun layoutSwitchMacroOverrideKeepsBaseFontSizeAndWeight() {
        val density = context.resources.displayMetrics.density
        val width = (400 * density).roundToInt()
        val height = (64 * density).roundToInt()
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity

        try {
            lateinit var keyboard: BaseKeyboard
            instrumentation.runOnMainSync {
                val definition = LayoutSwitchKey("?123").apply {
                    composeOverride = MacroKey(
                        label = "?123",
                        character = "?123",
                        tap = MacroAction(emptyList()),
                    )
                }
                keyboard = object : BaseKeyboard(
                    activity,
                    ThemePreset.MaterialLight,
                    { listOf(listOf(definition)) },
                ) {}
                activity.setContentView(FrameLayout(activity).apply {
                    addView(keyboard, FrameLayout.LayoutParams(width, height))
                })
            }
            instrumentation.waitForIdleSync()

            fun layout() {
                instrumentation.runOnMainSync {
                    keyboard.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                    )
                    keyboard.layout(0, 0, width, height)
                }
                instrumentation.waitForIdleSync()
            }

            fun layoutSwitchView(): TextKeyView {
                lateinit var view: TextKeyView
                instrumentation.runOnMainSync {
                    view = keyboard.allViews
                        .filterIsInstance<TextKeyView>()
                        .single { it.mainText.text.toString() == "?123" }
                }
                return view
            }

            layout()
            val baseView = layoutSwitchView()
            val baseAppearance = baseView.def as KeyDef.Appearance.Text
            val baseSize = baseAppearance.textSize
            val baseStyle = baseAppearance.textStyle
            assertEquals("?123", baseView.mainText.text.toString())

            instrumentation.runOnMainSync { keyboard.onCompositionStateChanged(true) }
            layout()
            val composeView = layoutSwitchView()
            assertEquals(R.id.button_layout_switch, composeView.def.viewId)
            assertEquals("?123", composeView.mainText.text.toString())
            val composeAppearance = composeView.def as KeyDef.Appearance.Text
            assertEquals(baseSize, composeAppearance.textSize, 0.01f)
            assertEquals(baseStyle, composeAppearance.textStyle)
            assertEquals(baseStyle, composeView.mainText.typeface.style)

            instrumentation.runOnMainSync { keyboard.onCompositionStateChanged(false) }
            layout()
            val restoredView = layoutSwitchView()
            val restoredAppearance = restoredView.def as KeyDef.Appearance.Text
            assertEquals(baseSize, restoredAppearance.textSize, 0.01f)
            assertEquals(baseStyle, restoredAppearance.textStyle)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
