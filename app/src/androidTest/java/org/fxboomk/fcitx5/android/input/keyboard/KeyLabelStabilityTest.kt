/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.content.res.Configuration
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.AltTextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** The reported phone uses density 3.25; compare rendered text, not nominal SP values. */
class KeyLabelStabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.createConfigurationContext(
        Configuration(instrumentation.targetContext.resources.configuration).apply {
            densityDpi = 520
            fontScale = 1f
        }
    )

    private data class Placement(val fontSize: Float, val baseline: Float)

    @Test
    fun accentedSecondRowHasTheSameFontSizeAsOtherLetterRows() = onMain {
        for (font in listOf(Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE)) {
            val rows = listOf(
                "qwertyuiop".mapIndexed { i, c -> key(c.toString(), listOf("`", "~", "+", "€", "α", "β", "ü", "\\", "ö", "|")[i], font) },
                "asdfghjkl".mapIndexed { i, c -> key(c.toString(), listOf("ä", "ß", "-", "=", "/", "{", "}", "[", "]")[i], font) },
                "zxcvbnm".mapIndexed { i, c -> key(c.toString(), listOf("'", ":", "\"", ";", "<", ">", "?")[i], font) },
            )
            rows.forEach { layout(it) }
            val placements = rows.flatten().map(::placement)
            placements.forEach { assertEquals("Unequal rendered letter sizes", placements.first().fontSize, it.fontSize, 0.01f) }
        }
    }

    @Test
    fun changingPunctuationBetweenLanguagesKeepsMainSizeAndBaseline() = onMain {
        for (position in listOf(AltTextPosition.Top, AltTextPosition.TopRight, AltTextPosition.Bottom)) {
            val key = key("g", "!").apply { definition.altTextPositionOverride = position }
            layout(listOf(key))
            val before = placement(key)
            for (punctuation in listOf("，", "！", "、", "β", "ü", "!")) {
                key.altText.text = punctuation
                layout(listOf(key))
                assertPlacement("$position / $punctuation", before, placement(key))
            }
        }
    }

    @Test
    fun changingNeighbouringLettersDoesNotMoveOrResizeTheSameKey() = onMain {
        val keys = listOf(key("a", "/"), key("c", "/"), key("e", "/"))
        layout(keys)
        val before = placement(keys.first())
        keys[1].mainText.text = "g"
        keys[2].mainText.text = "j"
        layout(keys)
        assertPlacement("Ascenders and descenders in other keys", before, placement(keys.first()))
    }

    @Test
    fun shiftAndCapsKeepTheSameTypographicBaseline() = onMain {
        val key = key("q", "/")
        layout(listOf(key))
        val before = placement(key)
        for (letter in listOf("Q", "q", "W", "w")) {
            key.mainText.text = letter
            layout(listOf(key))
            assertPlacement(letter, before, placement(key))
        }
    }

    @Test
    fun hidingAndRestoringASublabelKeepsTheMainFrameOnNormalKeys() = onMain {
        val key = key("g", "/")
        layout(listOf(key), heightDp = 80)
        val before = placement(key)
        for (label in listOf("", "/")) {
            key.altText.text = label
            layout(listOf(key), heightDp = 80)
            assertPlacement("Sublabel '$label'", before, placement(key))
        }
    }

    @Test
    fun stableHeightRefreshKeepsTheComputedPlacement() = onMain {
        val keys = listOf(key("a", "!"), key("g", "β"), key("t", "ü"))
        layout(keys)
        val before = keys.map(::placement)
        repeat(5) {
            keys.forEach { it.refreshLayout() }
            keys.forEachIndexed { i, key -> assertPlacement("Refresh $it / $i", before[i], placement(key)) }
        }
    }

    private val AltTextKeyView.definition: KeyDef.Appearance.AltText
        get() = def as KeyDef.Appearance.AltText

    private fun key(main: String, secondary: String, font: Typeface = Typeface.SANS_SERIF): AltTextKeyView {
        val definition = KeyDef.Appearance.AltText(main, secondary, main, textSize = 24f).apply {
            altTextPositionOverride = AltTextPosition.TopRight
        }
        return AltTextKeyView(context, ThemePreset.MaterialLight, definition).apply {
            mainText.typeface = font
            mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            listOf(altText, altText1, upperText).forEach {
                it.typeface = Typeface.SANS_SERIF
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.666667f)
            }
        }
    }

    private fun layout(keys: List<AltTextKeyView>, heightDp: Int = 62) {
        repeat(3) {
            keys.forEach { key ->
                key.refreshLayout()
                val width = (34 * context.resources.displayMetrics.density).roundToInt()
                val height = (heightDp * context.resources.displayMetrics.density).roundToInt()
                key.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                key.layout(0, 0, width, height)
            }
            AltTextKeyView.alignRemainingSpaceMainLabels(keys)
        }
    }

    private fun placement(key: AltTextKeyView): Placement {
        val ink = key.mainText.renderedGlyphBounds()
        assertTrue("Missing main ink", ink.height() > 0f)
        val actual = android.graphics.Rect().also {
            val text = key.mainText.text.toString()
            key.mainText.paint.getTextBounds(text, 0, text.length, it)
        }
        val scale = key.mainText.textScaleX
        return Placement(key.mainText.paint.textSize * scale, key.mainText.top + ink.top - actual.top * scale)
    }

    private fun assertPlacement(label: String, expected: Placement, actual: Placement) {
        assertEquals("$label: font size", expected.fontSize, actual.fontSize, 0.01f)
        assertEquals("$label: baseline", expected.baseline, actual.baseline, 0.01f)
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
