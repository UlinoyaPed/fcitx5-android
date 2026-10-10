/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.content.res.Configuration
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.AltTextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Compare rendered sizes and positions after changing only the other font setting. */
class KeyLabelFontIndependenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun normalKeysRenderConfiguredMain23AndSecondary20WithoutShrinking() = withPreferences {
        for (density in listOf(240, 520)) {
            for (position in listOf(AltTextPosition.TopRight)) {
                for (mainLabel in listOf("g", "Q", "W"))
                for (hint in listOf("!", "@", "#", "β", "ü", "（", "！")) {
                    val key = key(density, position, secondary = false).apply {
                        mainText.text = mainLabel
                        mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
                        altText.text = hint
                        altText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    }
                    layout(key, 62, widthDp = 39)
                    val main = bounds(key.mainText)
                    val secondary = bounds(key.altText)
                    val case = "$density/$position/$hint main=$main secondary=$secondary"
                    assertEquals("$case main font", 1f, key.mainText.textScaleX, 0.01f)
                    assertEquals("$case secondary font", 1f, key.altText.textScaleX, 0.01f)
                    assertEquals("$case main stays centered", key.getChildAt(0).height / 2f,
                        key.mainText.top + key.mainText.renderedReferenceBounds().centerY(), 0.01f)
                    // These are independent overlays, so no vertical gap is reserved from the main label.
                    assertTrue("$case visible ink", main.width() > 0 && secondary.width() > 0)
                    assertTrue("$case horizontal clipping", secondary.left >= 0 &&
                        secondary.right <= key.getChildAt(0).width)
                    assertTrue(case, secondary.top >= key.vMargin - 1 &&
                        secondary.bottom <= key.getChildAt(0).height - key.vMargin + 1)
                }
            }
        }
    }

    @Test
    fun secondaryFontChangesKeepTheMainSizeAndPosition() = withPreferences {
        for (density in listOf(240, 520)) for (height in listOf(32, 62, 80)) {
            for (position in AltTextPosition.entries) {
                val key = key(density, position)
                layout(key, height)
                val before = bounds(key.mainText)
                val fontSize = key.mainText.paint.textSize * key.mainText.textScaleX
                for (size in listOf(4f, 10.666667f, 24f, 64f)) {
                    listOf(key.altText, key.altText1).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                    }
                    layout(key, height)
                    val case = "$density/$height/$position secondary=$size"
                    assertBounds(case, before, bounds(key.mainText))
                    assertEquals(case, fontSize,
                        key.mainText.paint.textSize * key.mainText.textScaleX, 0.01f)
                }
            }
        }
    }

    @Test
    fun mainFontChangesKeepSecondarySizesPositionsAndVisibility() = withPreferences {
        for (density in listOf(240, 520)) for (height in listOf(32, 62, 80)) {
            for (position in AltTextPosition.entries) {
                val key = key(density, position)
                layout(key, height)
                val labels = listOf(key.altText, key.altText1)
                val before = labels.map(::bounds)
                val sizes = labels.map { it.paint.textSize * it.textScaleX }
                for (size in listOf(4f, 24f, 48f, 96f)) {
                    key.mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                    layout(key, height)
                    labels.forEachIndexed { i, label ->
                        val case = "$density/$height/$position main=$size hint=$i"
                        assertBounds(case, before[i], bounds(label))
                        assertEquals(case, sizes[i], label.paint.textSize * label.textScaleX, 0.01f)
                    }
                }
            }
        }
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Bottom)
        val key = key(520, null, uppercase = true)
        layout(key, 62)
        val before = bounds(key.upperText)
        key.mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 96f)
        layout(key, 62)
        assertBounds("Uppercase-only hint after main font growth", before, bounds(key.upperText))
    }

    private fun key(density: Int, position: AltTextPosition?, uppercase: Boolean = false,
                    secondary: Boolean = true): AltTextKeyView {
        val context = instrumentation.targetContext.createConfigurationContext(
            Configuration(instrumentation.targetContext.resources.configuration).apply {
                densityDpi = density
                fontScale = 1f
            }
        )
        val definition = KeyDef.Appearance.AltText("g", "β", "g",
            altText1 = if (uppercase || !secondary) null else "ü", supportsUppercaseHint = uppercase, textSize = 24f).apply {
            altTextPositionOverride = position
            altText1PositionOverride = when (position) {
                AltTextPosition.TopBottom -> AltTextPosition.Bottom
                AltTextPosition.Bottom -> AltTextPosition.Bottom
                else -> AltTextPosition.TopRight
            }.takeUnless { uppercase || !secondary }
        }
        return AltTextKeyView(context, ThemePreset.MaterialLight, definition)
    }

    private fun layout(key: AltTextKeyView, heightDp: Int, widthDp: Int = 48) {
        val density = key.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        repeat(3) {
            key.refreshLayout()
            key.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            key.layout(0, 0, width, height)
        }
    }

    private fun bounds(view: AutoScaleTextView): RectF {
        assertEquals("Hint '${view.text}' must remain visible", View.VISIBLE, view.visibility)
        return view.renderedGlyphBounds().apply { offset(view.left.toFloat(), view.top.toFloat()) }
    }

    private fun assertBounds(case: String, expected: RectF, actual: RectF) {
        assertEquals("$case left", expected.left, actual.left, 0.01f)
        assertEquals("$case top", expected.top, actual.top, 0.01f)
        assertEquals("$case right", expected.right, actual.right, 0.01f)
        assertEquals("$case bottom", expected.bottom, actual.bottom, 0.01f)
    }

    private fun withPreferences(block: () -> Unit) = instrumentation.runOnMainSync {
        val prefs = ThemeManager.prefs
        val punctuation = prefs.punctuationPosition.getValue()
        val uppercase = prefs.uppercasePosition.getValue()
        try {
            prefs.punctuationPosition.setValue(PunctuationPosition.Top)
            prefs.uppercasePosition.setValue(UppercasePosition.None)
            block()
        } finally {
            prefs.punctuationPosition.setValue(punctuation)
            prefs.uppercasePosition.setValue(uppercase)
        }
    }
}
