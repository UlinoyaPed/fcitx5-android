/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class TextKeyMainLabelLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun lowercaseLabelsCenterTheirXHeightAndKeepOneBaseline() {
        instrumentation.runOnMainSync {
            for (font in listOf(Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE)) {
                val keys = mapOf("x" to createKey("x", font), "q" to createKey("q", font))
                keys.values.forEach(::layoutKey)

                val appearance = keys.getValue("x").getChildAt(0)
                val xInk = inkBounds(keys.getValue("x").mainText)
                assertEquals(
                    "x-height should be centered for $font",
                    (appearance.height / 2f).toDouble(),
                    ((xInk.top + xInk.bottom) / 2f).toDouble(),
                    dp(1.5f).toDouble()
                )

                val baselines = keys.mapValues { (_, key) ->
                    key.mainText.top + key.mainText.baseline
                }
                assertEquals(
                    "Lowercase labels must share a baseline for $font",
                    baselines.getValue("x").toDouble(),
                    baselines.getValue("q").toDouble(),
                    1.0
                )
                assertTrue(
                    "A descender must remain below the centered x-height for $font",
                    inkBounds(keys.getValue("q").mainText).bottom >
                        inkBounds(keys.getValue("x").mainText).bottom
                )
            }
        }
    }

    private fun createKey(text: String, font: Typeface): TextKeyView {
        val key = TextKeyView(
            context,
            ThemePreset.MaterialLight,
            KeyDef.Appearance.Text(displayText = text, textSize = 24f)
        )
        key.mainText.typeface = font
        return key
    }

    private fun layoutKey(key: TextKeyView) {
        repeat(3) {
            key.measure(
                View.MeasureSpec.makeMeasureSpec(dp(48f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(64f), View.MeasureSpec.EXACTLY)
            )
            key.layout(0, 0, dp(48f), dp(64f))
        }
    }

    private fun inkBounds(view: AutoScaleTextView): Rect {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val originalColor = view.currentTextColor
        try {
            view.setTextColor(Color.BLACK)
            view.draw(Canvas(bitmap))
            val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                        bounds.left = minOf(bounds.left, x)
                        bounds.top = minOf(bounds.top, y)
                        bounds.right = maxOf(bounds.right, x + 1)
                        bounds.bottom = maxOf(bounds.bottom, y + 1)
                    }
                }
            }
            assertTrue("No ink for '${view.text}'", !bounds.isEmpty)
            bounds.offset(view.left, view.top)
            return bounds
        } finally {
            view.setTextColor(originalColor)
            bitmap.recycle()
        }
    }

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
