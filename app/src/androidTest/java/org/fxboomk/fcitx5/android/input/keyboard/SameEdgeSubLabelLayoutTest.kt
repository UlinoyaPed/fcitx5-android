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
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import androidx.core.view.children
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
import org.junit.Before
import org.junit.After
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import kotlin.math.roundToInt

class SameEdgeSubLabelLayoutTest {
    private var oldCenter = true
    @Before fun useRemainingSpaceMode() {
        oldCenter = AppPrefs.getInstance().keyboard.centerMainLabels.getValue()
        AppPrefs.getInstance().keyboard.centerMainLabels.setValue(false)
    }
    @After fun restoreCenterMode() { AppPrefs.getInstance().keyboard.centerMainLabels.setValue(oldCenter) }


    @Test
    fun visibleGapsAdaptToDensityFontsTextAndShortRows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val prefs = ThemeManager.prefs
            val oldUppercase = prefs.uppercasePosition.getValue()
            try {
                prefs.uppercasePosition.setValue(UppercasePosition.None)
                for (density in listOf(240, 560)) {
                    val context = instrumentation.targetContext.createConfigurationContext(
                        Configuration(instrumentation.targetContext.resources.configuration).apply {
                            densityDpi = density
                            fontScale = if (density == 240) 1.3f else 1f
                        }
                    )
                    fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
                    for (font in listOf(Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE)) {
                        for (text in listOf("Q", "g", "É", "主", "Shift")) {
                            val appearance = KeyDef.Appearance.AltText(text, "1", "q", "Q", textSize = 24f).apply {
                                altTextPositionOverride = KeyDef.Appearance.AltTextPosition.Top
                                altText1PositionOverride = KeyDef.Appearance.AltTextPosition.TopRight
                            }
                            val key = AltTextKeyView(context, ThemePreset.MaterialLight, appearance)
                            for (scale in listOf(1f, 2.4f)) {
                                key.setTextScale(scale)
                                key.mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * scale)
                                key.mainText.typeface = font
                                for (height in listOf(32, 52, 80)) {
                                    repeat(3) {
                                        key.refreshLayout()
                                        key.measure(
                                            View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY),
                                            View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY)
                                        )
                                        key.layout(0, 0, dp(48), dp(height))
                                    }
                                    val case = "$text density=$density height=$height scale=$scale font=$font"
                                    val main = inkBounds(key.mainText)
                                    assertTrue(case, key.mainText.textScaleX.isFinite() && key.mainText.textScaleX > 0f)
                                    if (height / scale <= 32) {
                                        // 塞不下就隐藏：极短行上副标签按策略退场（隐藏或
                                        // 压缩到不可辨识），只要求主标签保持可读且在边距内
                                        assertTrue(case, main.top >= key.vMargin - 1)
                                        assertTrue(
                                            case,
                                            main.bottom <= key.getChildAt(0).height - key.vMargin + 1
                                        )
                                    } else {
                                        val subBottom = listOfNotNull(
                                            inkBoundsOrNull(key.altText)?.bottom,
                                            inkBoundsOrNull(key.altText1)?.bottom
                                        ).maxOrNull() ?: key.vMargin
                                        val above = main.top - subBottom
                                        val below = key.getChildAt(0).height - key.vMargin - main.bottom
                                        // Check the placement geometry tightly, separately from rasterized ink.
                                        val mainGeometry = key.mainText.renderedGlyphBounds().apply {
                                            offset(key.mainText.left.toFloat(), key.mainText.top.toFloat())
                                        }
                                        val subGeometryBottom = listOf(key.altText, key.altText1)
                                            .filter { it.visibility == View.VISIBLE }
                                            .maxOfOrNull { it.top + it.renderedGlyphBounds().bottom }
                                            ?: key.vMargin.toFloat()
                                        assertEquals("Placement geometry: $case",
                                            mainGeometry.top - subGeometryBottom,
                                            key.getChildAt(0).height - key.vMargin - mainGeometry.bottom, 0.01f)
                                        // Match RemainingSpaceSubLabelLayoutTest: fallback fonts and
                                        // alpha-threshold rasterization vary with density, unlike geometry.
                                        val inkTolerance = maxOf(2f, context.resources.displayMetrics.density * 1.5f)
                                        assertEquals(case, above.toFloat(), below.toFloat(), inkTolerance)
                                        assertTrue("$case gaps=$above/$below minimum=${dp(2) - 1}",
                                            above >= dp(2) - 1 && below >= dp(2) - 1)
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                prefs.uppercasePosition.setValue(oldUppercase)
            }
        }
    }

    @Test
    fun keyboardRowUsesOneBaselineAndRecomputesAfterTextAndFontChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
        instrumentation.runOnMainSync {
            val definitions = listOf("Q", "W", "g", "É").map { text ->
                AlphabetKey("q", "1", "!", displayText = text).apply {
                    appearance.altTextPositionOverride = KeyDef.Appearance.AltTextPosition.Top
                    appearance.altText1PositionOverride = KeyDef.Appearance.AltTextPosition.TopRight
                }
            }
            val keyboard = object : BaseKeyboard(context, ThemePreset.MaterialLight, { listOf(definitions) }) {}
            keyboard.setTextScale(1f)
            val keys = (keyboard.getChildAt(0) as ViewGroup).children.filterIsInstance<AltTextKeyView>().toList()
            assertEquals(4, keys.size)
            for (font in listOf(Typeface.SANS_SERIF, Typeface.SERIF)) {
                keys.forEach {
                    it.mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    it.mainText.typeface = font
                }
                for (text in listOf("Q", "g", "Shift")) {
                    keys.first().mainText.text = text
                    repeat(3) {
                        keyboard.requestLayout()
                        keyboard.measure(
                            View.MeasureSpec.makeMeasureSpec(dp(192), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(dp(52), View.MeasureSpec.EXACTLY)
                        )
                        keyboard.layout(0, 0, dp(192), dp(52))
                    }
                    val mainBounds = keys.map { inkBounds(it.mainText) }
                    val subBottom = keys.maxOf { maxOf(inkBounds(it.altText).bottom, inkBounds(it.altText1).bottom) }
                    val keyBottom = keys.minOf { it.getChildAt(0).height - it.vMargin }
                    val above = mainBounds.minOf { it.top } - subBottom
                    val below = keyBottom - mainBounds.maxOf { it.bottom }
                    assertEquals("Shared row glyph band: $text / $font", above.toFloat(), below.toFloat(), 2f)
                    val baselines = keys.map { it.mainText.top + it.mainText.baseline }
                    assertTrue("Letters must share a baseline", baselines.max() - baselines.min() <= 1)
                }
            }
        }
    }

    @Test
    fun topCornersBalanceVisibleGapsAndResetWhenHidden() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        instrumentation.runOnMainSync {
            val prefs = ThemeManager.prefs
            val oldPunctuation = prefs.punctuationPosition.getValue()
            val oldUppercase = prefs.uppercasePosition.getValue()
            try {
                for (independent in listOf(false, true)) {
                    for (uppercase in listOf(false, true)) {
                        for (right in listOf(false, true)) {
                            prefs.punctuationPosition.setValue(if (right) PunctuationPosition.TopRight else PunctuationPosition.Top)
                            prefs.uppercasePosition.setValue(if (uppercase) UppercasePosition.Top else UppercasePosition.None)
                            val appearance = KeyDef.Appearance.AltText(
                                displayText = "Q",
                                character = "q",
                                altText = "1",
                                altText1 = if (uppercase) null else "!",
                                supportsUppercaseHint = uppercase,
                                textSize = 24f
                            ).apply {
                                if (independent) {
                                    altTextPositionOverride = if (right) KeyDef.Appearance.AltTextPosition.TopRight else KeyDef.Appearance.AltTextPosition.Top
                                    altText1PositionOverride = KeyDef.Appearance.AltTextPosition.Top
                                }
                            }
                            val key = AltTextKeyView(context, ThemePreset.MaterialLight, appearance)
                            key.mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                            fun layout() {
                                repeat(2) {
                                    key.refreshLayout()
                                    key.measure(
                                        View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(dp(64), View.MeasureSpec.EXACTLY)
                                    )
                                    key.layout(0, 0, dp(48), dp(64))
                                }
                            }
                            fun centerOffset() = (key.mainText.top + key.mainText.bottom - key.getChildAt(0).height) / 2f
                            layout()
                            val case = "independent=$independent, uppercase=$uppercase, right=$right"
                            val subBottom = maxOf(inkBounds(key.altText).bottom,
                                inkBounds(if (uppercase) key.upperText else key.altText1).bottom)
                            val main = inkBounds(key.mainText)
                            val gapAbove = main.top - subBottom
                            val gapBelow = key.getChildAt(0).height - key.vMargin - main.bottom
                            assertEquals(case, gapAbove.toFloat(), gapBelow.toFloat(), 2f)
                            assertTrue(case, gapAbove >= dp(2) - 1)

                            appearance.altTextPositionOverride = null
                            appearance.altText1PositionOverride = null
                            prefs.punctuationPosition.setValue(PunctuationPosition.None)
                            prefs.uppercasePosition.setValue(UppercasePosition.None)
                            layout()
                            assertEquals("Hidden sublabels must clear the offset: $case", 0f, centerOffset(), 1f)
                        }
                    }
                }
            } finally {
                prefs.punctuationPosition.setValue(oldPunctuation)
                prefs.uppercasePosition.setValue(oldUppercase)
            }
        }
    }

    /**
     * 塞不下就隐藏：过短行上副标签可能被压缩到不可辨识（无 ≥128 alpha 的墨迹），
     * 此时视同隐藏返回 null；主标签的墨迹断言由调用处单独要求。
     */
    private fun inkBoundsOrNull(view: AutoScaleTextView): Rect? {
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.setTextColor(Color.BLACK)
        view.draw(Canvas(bitmap))
        val bounds = Rect(view.width, view.height, 0, 0)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) >= 128) {
                    bounds.left = minOf(bounds.left, x)
                    bounds.top = minOf(bounds.top, y)
                    bounds.right = maxOf(bounds.right, x + 1)
                    bounds.bottom = maxOf(bounds.bottom, y + 1)
                }
            }
        }
        bitmap.recycle()
        bounds.offset(view.left, view.top)
        return bounds.takeIf { !it.isEmpty }
    }

    private fun inkBounds(view: AutoScaleTextView): Rect =
        requireNotNull(inkBoundsOrNull(view)) { "No visible ink for '${view.text}'" }

    @Test
    fun independentSameEdgeLabelsKeepLegacyMainLabelPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val density = context.resources.displayMetrics.density
        instrumentation.runOnMainSync {
            val prefs = ThemeManager.prefs
            val oldPunctuation = prefs.punctuationPosition.getValue()
            val oldUppercase = prefs.uppercasePosition.getValue()
            try {
                for (top in listOf(true, false)) {
                    prefs.punctuationPosition.setValue(if (top) PunctuationPosition.Top else PunctuationPosition.Bottom)
                    for (uppercase in listOf(true, false)) {
                        prefs.uppercasePosition.setValue(when {
                            !uppercase -> UppercasePosition.None
                            top -> UppercasePosition.Top
                            else -> UppercasePosition.Bottom
                        })
                        val positions = if (top) {
                            listOf(
                                KeyDef.Appearance.AltTextPosition.Top to KeyDef.Appearance.AltTextPosition.Top,
                                KeyDef.Appearance.AltTextPosition.Top to KeyDef.Appearance.AltTextPosition.TopRight,
                                KeyDef.Appearance.AltTextPosition.TopRight to KeyDef.Appearance.AltTextPosition.Top
                            )
                        } else {
                            listOf(KeyDef.Appearance.AltTextPosition.Bottom to KeyDef.Appearance.AltTextPosition.Bottom)
                        }
                        for ((primary, secondary) in positions) {
                            fun createKey(independent: Boolean): AltTextKeyView {
                                val appearance = KeyDef.Appearance.AltText(
                                    displayText = "g",
                                    character = "g",
                                    altText = "?",
                                    altText1 = if (uppercase) null else "!",
                                    supportsUppercaseHint = uppercase,
                                    textSize = 24f
                                ).apply {
                                    if (independent) {
                                        altTextPositionOverride = primary
                                        altText1PositionOverride = secondary
                                    }
                                }
                                return AltTextKeyView(context, ThemePreset.MaterialLight, appearance)
                            }
                            val legacy = createKey(false)
                            val independent = createKey(true)
                            for (scale in listOf(1f, 2.4f)) {
                                legacy.setTextScale(scale)
                                independent.setTextScale(scale)
                                for (heightDp in listOf(52, 32, 60)) {
                                    for (key in listOf(legacy, independent)) {
                                        val width = (48 * density).roundToInt()
                                        val height = (heightDp * density).roundToInt()
                                        repeat(2) {
                                            key.refreshLayout()
                                            key.measure(
                                                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                                            )
                                            key.layout(0, 0, width, height)
                                        }
                                    }
                                    val case = "$primary / $secondary, uppercase=$uppercase, scale=$scale, height=$heightDp"
                                    assertEquals(case, legacy.mainText.top, independent.mainText.top)
                                    assertEquals(case, legacy.mainText.bottom, independent.mainText.bottom)
                                    assertEquals(case, legacy.mainText.left, independent.mainText.left)
                                    assertEquals(case, legacy.mainText.right, independent.mainText.right)
                                }
                            }
                        }
                    }
                }
            } finally {
                prefs.punctuationPosition.setValue(oldPunctuation)
                prefs.uppercasePosition.setValue(oldUppercase)
            }
        }
    }
}
