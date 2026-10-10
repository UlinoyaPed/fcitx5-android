/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.AltTextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Main-label bounds in these tests come from Canvas pixels, not TextView boxes. */
class RemainingSpaceSubLabelLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.createConfigurationContext(
        Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = 1f }
    )
    private val positions = listOf(AltTextPosition.Top, AltTextPosition.Bottom, AltTextPosition.TopRight)
    private val glyphs = listOf("g", "É", "主", "Shift")

    @Test
    fun wholeKeyCenterDoesNotMoveWithTopOrBottomSublabels() = withPreferences {
        for (position in positions) for (secondary in listOf(null, "Q")) {
            val key = createKey(appearance(main = "g", secondary = secondary, primaryPosition = position,
                secondaryPosition = AltTextPosition.Bottom.takeIf { secondary != null }))
            layoutKey(key)
            val main = key.mainText.renderedReferenceBounds()
            assertEquals("center $position", key.getChildAt(0).height / 2f,
                key.mainText.top + main.centerY(), 0.01f)
            assertTrue("visible main $position", main.height() > 0f)
            assertBalanced("centered frame $position", key, Edges(primary = position.edge(), secondary = Edge.Bottom.takeIf { secondary != null }))
        }
    }

    @Test
    fun defaultThemePairsAndMixedEdgesBalanceVisibleInk() = withPreferences {
        positions.forEachIndexed { index, position ->
            ThemeManager.prefs.punctuationPosition.setValue(position.punctuation())
            val key = createKey(appearance(main = glyphs[index]))
            layoutKey(key)
            assertBalanced("theme pair $position", key, Edges(position.edge(), position.edge()))
        }
        // The default theme also has a genuine one-top/one-bottom case: the
        // punctuation owns TopRight while the uppercase hint owns Bottom.
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.TopRight)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Bottom)
        val mixed = createKey(appearance(secondary = null, uppercase = true))
        layoutKey(mixed)
        assertBalanced("theme TopRight + UpperBottom", mixed, Edges(Edge.Top, uppercase = Edge.Bottom))

        // TopBottom is the legacy combined override, not an independent pair.
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.None)
        val key = createKey(appearance(primaryPosition = AltTextPosition.TopBottom))
        layoutKey(key)
        assertBalanced("legacy TopBottom", key, Edges(Edge.Top, Edge.Bottom))
        val appearanceHeight = key.getChildAt(0).height
        val labelInset = key.vMargin + dp(key.context, ThemeManager.prefs.subLabelPadding.getValue())
        assertEquals("Top font frame stays at the top key inset", labelInset.toFloat(),
            key.altText.top + key.altText.renderedReferenceBounds().top, 0.01f)
        assertEquals("Bottom font frame stays at the bottom key inset", (appearanceHeight - labelInset).toFloat(),
            key.altText1.top + key.altText1.renderedReferenceBounds().bottom, 0.01f)
    }

    @Test
    fun independentPairsCoverBothOrdersAndTopRightCollision() = withPreferences {
        // Three by three placements only; fonts and sizes are sampled separately.
        // A hidden theme ensures this cannot pass by accidentally using its defaults.
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        positions.forEach { primary ->
            positions.forEachIndexed { index, secondary ->
                val key = createKey(appearance(
                    main = glyphs[index], primaryPosition = primary, secondaryPosition = secondary
                ))
                layoutKey(key)
                assertBalanced("independent $primary/$secondary", key, Edges(primary.edge(), secondary.edge()))
            }
        }
    }

    @Test
    fun singlePrimaryUsesThemeOrExplicitTopBottomAndTopRight() = withPreferences {
        positions.forEach { position ->
            for (override in listOf(false, true)) {
                ThemeManager.prefs.punctuationPosition.setValue(
                    if (override) PunctuationPosition.None else position.punctuation()
                )
                val key = createKey(appearance(
                    secondary = null, primaryPosition = position.takeIf { override }
                ))
                layoutKey(key)
                assertBalanced("single primary $position override=$override", key, Edges(primary = position.edge()))
            }
        }
    }

    @Test
    fun onlySecondaryRemainsCenteredWithEmptyOrThemeHiddenPrimary() = withPreferences {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        positions.forEach { position ->
            for (primary in listOf("")) {
                val key = createKey(appearance(primary = primary, secondaryPosition = position))
                layoutKey(key)
                assertBalanced(
                    "secondary only $position primary='$primary'", key, Edges(secondary = position.edge())
                )
            }
        }
    }

    @Test
    fun themeUppercaseCoversCornerMixedAndUppercaseOnlyLayouts() = withPreferences {
        for (uppercase in listOf(UppercasePosition.Top, UppercasePosition.Bottom)) {
            ThemeManager.prefs.uppercasePosition.setValue(uppercase)
            val upperEdge = if (uppercase == UppercasePosition.Top) Edge.Top else Edge.Bottom
            positions.forEach { position ->
                ThemeManager.prefs.punctuationPosition.setValue(position.punctuation())
                val key = createKey(appearance(secondary = null, uppercase = true))
                layoutKey(key)
                assertBalanced(
                    "theme punctuation=$position uppercase=$uppercase", key,
                    Edges(primary = position.edge(), uppercase = upperEdge)
                )
            }
            ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
            val key = createKey(appearance(secondary = null, uppercase = true))
            layoutKey(key)
            assertBalanced("theme uppercase only $uppercase", key, Edges(uppercase = upperEdge))
        }
    }

    @Test
    fun independentUppercaseCoversBothEdgesAndUppercaseOnlyTopRight() = withPreferences {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        // The preference enables the hint; its independent override selects the edge.
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Top)
        positions.forEach { primary ->
            positions.forEach { upper ->
                val key = createKey(appearance(
                    secondary = null, uppercase = true,
                    primaryPosition = primary, secondaryPosition = upper
                ))
                layoutKey(key)
                assertBalanced(
                    "independent punctuation=$primary uppercase=$upper", key,
                    Edges(primary = primary.edge(), uppercase = upper.edge())
                )
            }
        }
        positions.forEach { upper ->
            val key = createKey(appearance(
                primary = "", secondary = null, uppercase = true, secondaryPosition = upper
            ))
            layoutKey(key)
            assertBalanced("independent uppercase only $upper", key, Edges(uppercase = upper.edge()))
        }
    }

    @Test
    fun switchingEdgesAndHidingLabelsClearsPlacementBeforeRestoringIt() = withPreferences {
        val definition = appearance()
        val key = createKey(definition)
        fun check(label: String, edges: Edges) {
            layoutKey(key)
            assertBalanced(label, key, edges)
        }
        check("initial theme top corners", Edges(Edge.Top, Edge.Top))
        val initialInk = inkBounds(key.mainText)
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Bottom)
        check("theme bottom corners", Edges(Edge.Bottom, Edge.Bottom))

        definition.altTextPositionOverride = AltTextPosition.TopRight
        definition.altText1PositionOverride = AltTextPosition.Bottom
        check("independent right-top/bottom", Edges(Edge.Top, Edge.Bottom))
        definition.altTextPositionOverride = AltTextPosition.Bottom
        definition.altText1PositionOverride = AltTextPosition.TopRight
        check("independent bottom/right-top", Edges(Edge.Bottom, Edge.Top))

        key.altText1.text = ""
        definition.altTextPositionOverride = AltTextPosition.TopRight
        definition.altText1PositionOverride = null
        check("single right-top after mixed edges", Edges(primary = Edge.Top))
        key.altText.text = ""
        key.altText1.text = "É"
        definition.altTextPositionOverride = null
        definition.altText1PositionOverride = AltTextPosition.Bottom
        check("secondary bottom after single primary", Edges(secondary = Edge.Bottom))

        definition.altText1PositionOverride = null
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        layoutKey(key)
        val hidden = readInk("hidden after independent", key, Edges())
        assertNotNull("Hidden labels retain the same stable main font frame", key.mainText.glyphPlacement)
        // Hidden keys retain the normal no-sublabel typography. Compare actual ink
        // to a fresh hidden key rather than requiring a different font-metric policy.
        val freshDefinition = appearance(primary = "?", secondary = null)
        val fresh = createKey(freshDefinition)
        layoutKey(fresh)
        assertEquals("Hidden layout must not retain an old offset or scale", readInk("fresh hidden", fresh, Edges()).main, hidden.main)

        key.altText.text = "?"
        definition.altTextPositionOverride = null
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Top)
        check("restored theme top corners", Edges(Edge.Top, Edge.Top))
        assertEquals("Restoring the original layout restores its ink", initialInk, inkBounds(key.mainText))

        // Exercise the independently rendered uppercase view's hidden/reset path too.
        val upperDefinition = appearance(secondary = null, uppercase = true, secondaryPosition = AltTextPosition.TopRight)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Top)
        val upperKey = createKey(upperDefinition)
        layoutKey(upperKey)
        assertBalanced("uppercase before hiding", upperKey, Edges(primary = Edge.Top, uppercase = Edge.Top))
        upperDefinition.altText1PositionOverride = null
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.None)
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        layoutKey(upperKey)
        assertEquals("Hidden uppercase clears its old offset", hidden.main, readInk("uppercase hidden", upperKey, Edges()).main)
        assertNotNull(upperKey.mainText.glyphPlacement)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Bottom)
        layoutKey(upperKey)
        assertBalanced("uppercase restored at bottom", upperKey, Edges(uppercase = Edge.Bottom))
    }

    @Test
    fun representativeRowsKeepNormalGapsAndCompactOverlaysInsideKey() = withPreferences {
        // Deliberately sampled rather than a density x font x glyph x size product.
        val samples = listOf(
            Sample("g", Typeface.SANS_SERIF, 1.6f, 240, 1.3f, AltTextPosition.Top, AltTextPosition.TopRight),
            Sample("E", Typeface.SERIF, 1f, 560, 1f, AltTextPosition.Top, AltTextPosition.Bottom, uppercase = true),
            Sample("主", Typeface.SANS_SERIF, 1.4f, 240, 1.3f, AltTextPosition.Bottom, AltTextPosition.Bottom),
            Sample("Shift", Typeface.MONOSPACE, 2.4f, 560, 1f, AltTextPosition.Bottom, AltTextPosition.TopRight),
        )
        samples.forEach { sample ->
            val sampleContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                densityDpi = sample.density
                fontScale = sample.fontScale
            })
            ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
            ThemeManager.prefs.uppercasePosition.setValue(if (sample.uppercase) UppercasePosition.Top else UppercasePosition.None)
            val key = createKey(appearance(
                main = sample.main, secondary = if (sample.uppercase) null else "É",
                uppercase = sample.uppercase, primaryPosition = sample.primary, secondaryPosition = sample.secondary
            ), sampleContext, sample.font, sample.scale)
            val edges = if (sample.uppercase) {
                Edges(primary = sample.primary.edge(), uppercase = sample.secondary.edge())
            } else Edges(sample.primary.edge(), sample.secondary.edge())
            for (height in listOf(72, 32, 64)) {
                layoutKey(key, height)
                // 独立叠放不会为了避让超大副字缩小主字；短行上分别检查居中与裁切。
                val normalRow = height / sample.scale >= 52
                val effective = if (
                    key.altText.visibility == View.GONE &&
                    key.altText1.visibility == View.GONE &&
                    key.upperText.visibility == View.GONE
                ) Edges() else edges
                if (normalRow) {
                    assertBalanced("$sample height=$height", key, effective)
                } else {
                    val ink = readInk("$sample height=$height", key, effective, allowOverlay = true)
                    assertTrue("Main ink remains inside the compact key", ink.main.top >= key.vMargin - 1 &&
                        ink.main.bottom <= key.getChildAt(0).height - key.vMargin + 1)
                    assertEquals("Compact main frame stays centered", key.getChildAt(0).height / 2f,
                        key.mainText.top + key.mainText.renderedReferenceBounds().centerY(), 0.01f)
                }
            }
        }
    }

    @Test
    fun mixedRowSharesBaselinesOnlyWithinEachOccupiedEdgeBand() = withPreferences {
        val pairs = listOf(
            AltTextPosition.Top to AltTextPosition.TopRight,
            AltTextPosition.Bottom to AltTextPosition.Bottom,
            AltTextPosition.TopRight to AltTextPosition.Bottom,
        )
        val definitions = pairs.flatMap { (primary, secondary) ->
            listOf(
                Triple("g", "?", "É"),
                Triple("É", "…", "主"),
            ).map { (main, sub, sub1) ->
                AlphabetKey(main, sub, sub1, displayText = main).apply {
                    appearance.altTextPositionOverride = primary
                    appearance.altText1PositionOverride = secondary
                }
            }
        }
        val keyboard = object : BaseKeyboard(context, ThemePreset.MaterialLight, { listOf(definitions) }) {}
        val keys = (keyboard.getChildAt(0) as ViewGroup).children.filterIsInstance<AltTextKeyView>().toList()
        assertEquals(6, keys.size)
        for (changed in listOf(false, true)) {
            keys.forEachIndexed { index, key ->
                configureText(key, if (changed) Typeface.SERIF else Typeface.SANS_SERIF, 1f)
                key.mainText.text = if (changed) listOf("主", "Shift")[index % 2] else listOf("g", "É")[index % 2]
                val subTypeface = if (index % 2 == 0) Typeface.SERIF else Typeface.MONOSPACE
                key.altText.typeface = subTypeface
                key.altText1.typeface = if (index % 2 == 0) Typeface.MONOSPACE else Typeface.SERIF
            }
            repeat(3) {
                keys.forEach { it.refreshLayout() }
                keyboard.requestLayout()
                measureAndLayout(keyboard, 288, 64)
            }
            keys.chunked(2).forEachIndexed { index, group ->
                val (primary, secondary) = pairs[index]
                val label = "row $primary/$secondary changed=$changed"
                group.forEachIndexed { keyIndex, key ->
                    val ink = readInk(label, key, Edges(primary.edge(), secondary.edge()))
                    // Row baseline alignment is intentionally shared within an
                    // occupancy band, so different glyphs can have unequal ink
                    // gaps. They must nevertheless stay inside their own band.
                    val above = ink.main.top - ink.top
                    val below = ink.bottom - ink.main.bottom
                    assertTrue("$label key=$keyIndex: gaps=$above/$below", above >= dp(context, 2) - 1 && below >= dp(context, 2) - 1)
                }
                // readInk above draws first, updating AutoScaleTextView's real baseline.
                val baselines = group.map { it.top + it.getChildAt(0).top + it.mainText.top + it.mainText.baseline }
                assertTrue("$label must share a baseline: $baselines", baselines.max() - baselines.min() <= 1)
            }
        }
    }

    @Test
    fun stableHeightRefreshPreservesRowPlacementInTheImmediateDraw() = withPreferences {
        val definitions = listOf(
            AlphabetKey("g", "?", "É", displayText = "g"),
            AlphabetKey("É", "…", "主", displayText = "É"),
        )
        val keyboard = object : BaseKeyboard(context, ThemePreset.MaterialLight, { listOf(definitions) }) {}
        val keys = (keyboard.getChildAt(0) as ViewGroup).children.filterIsInstance<AltTextKeyView>().toList()
        keys.forEach { configureText(it, Typeface.SANS_SERIF, 1f) }
        for (height in listOf(64, 32, 72)) {
            repeat(3) {
                keyboard.requestLayout()
                measureAndLayout(keyboard, 96, height)
            }
            val before = keys.map { inkBounds(it.mainText) }
            // Model InputView's global-layout callback: refresh after row layout,
            // then draw immediately without another measure/layout pass.
            repeat(2) {
                keyboard.refreshAltTextLayouts()
                keys.forEach { assertNotNull(it.mainText.glyphPlacement) }
                assertEquals("Refresh must not draw a fallback position at height=$height",
                    before, keys.map { inkBounds(it.mainText) })
                measureAndLayout(keyboard, 96, height)
                assertEquals("The following layout must preserve the same ink",
                    before, keys.map { inkBounds(it.mainText) })
            }
        }
    }

    @Test
    fun unchangedOrdinaryLabelsDoNotRequestAnotherLayout() = withPreferences {
        for (position in listOf(PunctuationPosition.Top, PunctuationPosition.None)) {
            ThemeManager.prefs.punctuationPosition.setValue(position)
            val key = createKey(appearance())
            layoutKey(key)
            repeat(3) {
                // Force the parent through its callbacks with identical geometry.
                key.requestLayout()
                measureAndLayout(key, 48, 64)
                assertFalse("The key must finish layout for $position", key.isLayoutRequested)
                for (label in listOf(key.mainText, key.altText, key.altText1, key.upperText)) {
                    if (label.visibility == View.VISIBLE) {
                        assertFalse("Unchanged '${label.text}' must not request another layout",
                            label.isLayoutRequested)
                    }
                }
            }
        }
    }

    private enum class Edge { Top, Bottom }
    private data class Edges(val primary: Edge? = null, val secondary: Edge? = null, val uppercase: Edge? = null)
    private data class Ink(val main: Rect, val top: Int, val bottom: Int)
    private data class Sample(
        val main: String, val font: Typeface, val scale: Float, val density: Int, val fontScale: Float,
        val primary: AltTextPosition, val secondary: AltTextPosition, val uppercase: Boolean = false,
    )

    private fun AltTextPosition.edge() = when (this) {
        AltTextPosition.Top, AltTextPosition.TopRight -> Edge.Top
        AltTextPosition.Bottom -> Edge.Bottom
        AltTextPosition.TopBottom -> error("TopBottom needs two explicitly specified edges")
    }

    private fun AltTextPosition.punctuation() = when (this) {
        AltTextPosition.Top -> PunctuationPosition.Top
        AltTextPosition.TopRight -> PunctuationPosition.TopRight
        AltTextPosition.Bottom -> PunctuationPosition.Bottom
        AltTextPosition.TopBottom -> error("TopBottom is not a theme preference")
    }

    private fun appearance(
        main: String = "g", primary: String = "?", secondary: String? = "É", uppercase: Boolean = false,
        primaryPosition: AltTextPosition? = null, secondaryPosition: AltTextPosition? = null,
    ) = KeyDef.Appearance.AltText(
        displayText = main, character = "g", altText = primary, altText1 = secondary,
        supportsUppercaseHint = uppercase, textSize = 24f,
    ).apply {
        altTextPositionOverride = primaryPosition
        altText1PositionOverride = secondaryPosition
    }

    private fun createKey(
        definition: KeyDef.Appearance.AltText, ctx: Context = context,
        font: Typeface = Typeface.SANS_SERIF, scale: Float = 1f,
    ) = AltTextKeyView(ctx, ThemePreset.MaterialLight, definition).also { configureText(it, font, scale) }

    private fun configureText(key: AltTextKeyView, font: Typeface, scale: Float) {
        key.setTextScale(scale)
        key.mainText.typeface = font
        key.mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * scale)
        // Keep these fixtures independent of a device's persisted custom font sizes.
        listOf(key.altText, key.altText1, key.upperText).forEach { label ->
            label.setTypeface(Typeface.SANS_SERIF, if (label === key.altText1) Typeface.NORMAL else Typeface.BOLD)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.666667f * scale)
        }
    }

    private fun layoutKey(key: AltTextKeyView, heightDp: Int = 64) {
        repeat(3) {
            key.refreshLayout()
            measureAndLayout(key, 48, heightDp)
        }
    }

    private fun measureAndLayout(view: View, widthDp: Int, heightDp: Int) {
        val width = dp(view.context, widthDp)
        val height = dp(view.context, heightDp)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun assertBalanced(label: String, key: AltTextKeyView, edges: Edges, normalRow: Boolean = true) {
        // 固定字体框居中；实际墨迹另行验证间距和裁切。
        val reference = key.mainText.renderedReferenceBounds()
        assertEquals("$label: stable font frame is centered on the whole key",
            key.getChildAt(0).height / 2f, key.mainText.top + reference.centerY(), 0.01f)
        assertGaps(label, readInk(label, key, edges), if (normalRow) dp(key.context, 2) - 1 else 0)
    }

    private fun readInk(label: String, key: AltTextKeyView, edges: Edges, allowOverlay: Boolean = false): Ink {
        val appearance = key.getChildAt(0)
        val tops = mutableListOf<Rect>()
        val bottoms = mutableListOf<Rect>()
        val labels = listOf(Triple("primary", key.altText, edges.primary),
            Triple("secondary", key.altText1, edges.secondary), Triple("uppercase", key.upperText, edges.uppercase))
        labels.forEach { (name, view, edge) ->
            assertEquals("$label: $name visibility", if (edge == null) View.GONE else View.VISIBLE, view.visibility)
            if (edge != null) {
                assertEquals("$label: $name parent", appearance, view.parent)
                val params = view.layoutParams as ConstraintLayout.LayoutParams
                assertEquals("$label: $name $edge anchor", ConstraintLayout.LayoutParams.PARENT_ID,
                    if (edge == Edge.Top) params.topToTop else params.bottomToBottom)
                val ink = inkBounds(view)
                assertTrue("$label: $name ink outside appearance: $ink",
                    ink.top >= key.vMargin - 1 && ink.bottom <= appearance.height - key.vMargin + 1)
                if (edge == Edge.Top) tops.add(ink) else bottoms.add(ink)
            }
        }
        // Use fixture-declared edges, not proximity to the midpoint or main View box.
        val top = tops.maxOfOrNull { it.bottom } ?: key.vMargin
        val bottom = bottoms.minOfOrNull { it.top } ?: appearance.height - key.vMargin
        if (!allowOverlay) assertTrue("$label: no remaining band [$top, $bottom]", bottom > top)
        assertEquals("$label: main parent", appearance, key.mainText.parent)
        val main = inkBounds(key.mainText)
        assertTrue("$label: invalid main scale ${key.mainText.textScaleX}",
            key.mainText.textScaleX.isFinite() && key.mainText.textScaleX > 0f)
        return Ink(main, top, bottom)
    }

    private fun assertGaps(label: String, ink: Ink, minimum: Int) {
        val above = ink.main.top - ink.top
        val below = ink.bottom - ink.main.bottom
        val message = "$label: main=${ink.main}, band=[${ink.top}, ${ink.bottom}], gaps=$above/$below"
        assertTrue("$message, minimum=$minimum", above >= minimum && below >= minimum)
    }

    private fun inkBounds(view: AutoScaleTextView): Rect {
        assertEquals("Expected visible text '${view.text}'", View.VISIBLE, view.visibility)
        assertTrue("Unmeasured text '${view.text}'", view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val originalColor = view.currentTextColor
        try {
            view.setTextColor(Color.BLACK)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(pixels[y * bitmap.width + x]) > 0) {
                    bounds.left = minOf(bounds.left, x)
                    bounds.top = minOf(bounds.top, y)
                    bounds.right = maxOf(bounds.right, x + 1)
                    bounds.bottom = maxOf(bounds.bottom, y + 1)
                }
            }
            assertFalse("No visible ink for '${view.text}'", bounds.isEmpty)
            bounds.offset(view.left, view.top)
            return bounds
        } finally {
            view.setTextColor(originalColor)
            bitmap.recycle()
        }
    }

    private fun dp(ctx: Context, value: Int) = (value * ctx.resources.displayMetrics.density).roundToInt()

    private fun withPreferences(block: () -> Unit) {
        instrumentation.runOnMainSync {
            val prefs = ThemeManager.prefs
            val punctuation = prefs.punctuationPosition.getValue()
            val uppercase = prefs.uppercasePosition.getValue()
            val margin = prefs.keyVerticalMargin.getValue()
            val landscapeMargin = prefs.keyVerticalMarginLandscape.getValue()
            val horizontalMargin = prefs.keyHorizontalMargin.getValue()
            val landscapeHorizontalMargin = prefs.keyHorizontalMarginLandscape.getValue()
            try {
                prefs.punctuationPosition.setValue(PunctuationPosition.Top)
                prefs.uppercasePosition.setValue(UppercasePosition.None)
                prefs.keyVerticalMargin.setValue(2)
                prefs.keyVerticalMarginLandscape.setValue(2)
                prefs.keyHorizontalMargin.setValue(2)
                prefs.keyHorizontalMarginLandscape.setValue(2)
                block()
            } finally {
                prefs.punctuationPosition.setValue(punctuation)
                prefs.uppercasePosition.setValue(uppercase)
                prefs.keyVerticalMargin.setValue(margin)
                prefs.keyVerticalMarginLandscape.setValue(landscapeMargin)
                prefs.keyHorizontalMargin.setValue(horizontalMargin)
                prefs.keyHorizontalMarginLandscape.setValue(landscapeHorizontalMargin)
            }
        }
    }
}
