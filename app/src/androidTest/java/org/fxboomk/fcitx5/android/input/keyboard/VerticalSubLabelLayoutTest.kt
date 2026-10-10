/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class VerticalSubLabelLayoutTest {
    @Test
    fun bothOrdersStayAtEdgesWithSeparateMainLabel() = withPreferences {
        for (punctuationOnTop in listOf(true, false)) {
            ThemeManager.prefs.punctuationPosition.setValue(
                if (punctuationOnTop) PunctuationPosition.Top else PunctuationPosition.Bottom
            )
            ThemeManager.prefs.uppercasePosition.setValue(
                if (punctuationOnTop) UppercasePosition.Bottom else UppercasePosition.Top
            )
            for (punctuation in listOf(",", "！", "…")) {
                val key = createKey(punctuation)
                layoutKey(key, heightDp = 52)
                assertSeparatedAtEdges(key, punctuationOnTop)
            }
        }
    }

    @Test
    fun shortRowsAndLargeFontsKeepAllThreeLabelsApartAfterResize() = withPreferences {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Top)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Bottom)
        val key = createKey("?")
        key.setTextScale(2.4f)
        for (height in listOf(60, 32, 48)) {
            layoutKey(key, heightDp = height)
            if (key.altText.visibility == View.VISIBLE && key.upperText.visibility == View.VISIBLE) {
                assertSeparatedAtEdges(key, punctuationOnTop = true)
            }
        }
    }

    @Test
    fun changingToCornerOrSingleLabelRestoresNormalMainLabelLayout() = withPreferences {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Top)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Bottom)
        val key = createKey("?")
        layoutKey(key, heightDp = 52)
        assertSeparatedAtEdges(key, punctuationOnTop = true)

        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Top)
        layoutKey(key, heightDp = 52)
        assertTrue(key.mainText.useGlyphBounds)
        assertEquals(0, (key.mainText.layoutParams as ConstraintLayout.LayoutParams).height)
        assertEquals(View.VISIBLE, key.upperText.visibility)

        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.None)
        layoutKey(key, heightDp = 52)
        assertTrue(key.mainText.useGlyphBounds)
        assertEquals(0, (key.mainText.layoutParams as ConstraintLayout.LayoutParams).height)
        assertEquals(View.GONE, key.upperText.visibility)
        val mainInk = inkBounds(key.mainText)
        assertTrue(mainInk.top - inkBounds(key.altText).bottom >= 1)
        assertTrue(key.getChildAt(0).height - key.vMargin - mainInk.bottom >= 1)

        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        layoutKey(key, heightDp = 52)
        assertNormalMainLabelLayout(key)
    }

    private fun assertSeparatedAtEdges(key: AltTextKeyView, punctuationOnTop: Boolean) {
        val topLabel = if (punctuationOnTop) key.altText else key.upperText
        val bottomLabel = if (punctuationOnTop) key.upperText else key.altText
        assertEquals(View.VISIBLE, topLabel.visibility)
        assertEquals(View.VISIBLE, bottomLabel.visibility)
        val topInk = inkBounds(topLabel)
        val mainInk = inkBounds(key.mainText)
        val bottomInk = inkBounds(bottomLabel)
        val appearanceHeight = key.getChildAt(0).height
        val edgeInset = key.vMargin

        assertEquals(edgeInset.toFloat(), topLabel.top + topLabel.renderedReferenceBounds().top, 0.01f)
        assertEquals((appearanceHeight - edgeInset).toFloat(), bottomLabel.top + bottomLabel.renderedReferenceBounds().bottom, 0.01f)
        assertTrue(topInk.top >= edgeInset - 1)
        assertTrue(bottomInk.bottom <= appearanceHeight - edgeInset + 1)
        // 本测试的行高在 2.4x 字号下全部等效短行（60/32/48dp → ~25/13/20dp），
        // 要求三个标签互不贴住（≥1px 墨迹间隙），主字使用固定字体框居中。
        assertTrue("Top label must leave room for main text: $mainInk/$topInk", mainInk.top - topInk.bottom >= 1)
        assertTrue("Bottom label must leave room for main text: $mainInk/$bottomInk", bottomInk.top - mainInk.bottom >= 1)
        assertEquals("Main font frame stays centered on the key", appearanceHeight / 2f,
            key.mainText.top + key.mainText.renderedReferenceBounds().centerY(), 0.01f)
    }

    private fun assertNormalMainLabelLayout(key: AltTextKeyView) {
        assertFalse(key.altText.useGlyphBounds)
        assertFalse(key.upperText.useGlyphBounds)
        assertTrue("The hint keeps its own bounded edge region",
            key.altText.maxHeight in 1 until key.getChildAt(0).height)
        val params = key.mainText.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(0, params.height)
        assertEquals(ConstraintLayout.LayoutParams.UNSET, params.topToBottom)
        assertEquals(0, params.bottomMargin)
    }

    /** Inspect rendered pixels, so font leading cannot masquerade as edge alignment. */
    private fun inkBounds(view: AutoScaleTextView): Rect {
        assertTrue("Label needs a nonempty drawing region", view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.setTextColor(Color.BLACK)
        view.draw(Canvas(bitmap))
        val bounds = Rect(view.width, view.height, 0, 0)
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
        bitmap.recycle()
        assertFalse("Label must remain visible", bounds.isEmpty)
        bounds.offset(view.left, view.top)
        return bounds
    }

    private fun createKey(punctuation: String) = AltTextKeyView(
        targetContext,
        ThemePreset.MaterialLight,
        KeyDef.Appearance.AltText(
            displayText = "g",
            character = "g",
            altText = punctuation,
            supportsUppercaseHint = true,
            textSize = 24f,
        ),
    )

    private fun layoutKey(key: AltTextKeyView, heightDp: Int) {
        repeat(2) {
            key.refreshLayout()
            key.measure(
                View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY),
            )
            key.layout(0, 0, dp(48), dp(heightDp))
        }
    }

    private fun withPreferences(block: () -> Unit) {
        instrumentation.runOnMainSync {
            val prefs = ThemeManager.prefs
            val punctuation = prefs.punctuationPosition.getValue()
            val uppercase = prefs.uppercasePosition.getValue()
            val margin = prefs.keyVerticalMargin.getValue()
            val landscapeMargin = prefs.keyVerticalMarginLandscape.getValue()
            try {
                prefs.keyVerticalMargin.setValue(2)
                prefs.keyVerticalMarginLandscape.setValue(2)
                block()
            } finally {
                prefs.punctuationPosition.setValue(punctuation)
                prefs.uppercasePosition.setValue(uppercase)
                prefs.keyVerticalMargin.setValue(margin)
                prefs.keyVerticalMarginLandscape.setValue(landscapeMargin)
            }
        }
    }

    private fun dp(value: Int) = (value * targetContext.resources.displayMetrics.density).roundToInt()

    companion object {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val targetContext = instrumentation.targetContext
    }
}
