/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.AltTextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class IndependentSubLabelPositionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun hiddenPrimaryDoesNotRenderOrReceiveSwipe() = withTheme(PunctuationPosition.None) {
        for (position in listOf(AltTextPosition.Top, AltTextPosition.Bottom)) {
            val key = createKey(null, position)
            assertEquals(View.GONE, key.altText.visibility)
            assertEquals(View.VISIBLE, key.altText1.visibility)
            val direction = if (position == AltTextPosition.Top) -20 else 20
            assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(direction))
            assertNull(key.selectAltTextSwipeTarget(-direction))
        }
    }

    @Test
    fun independentEdgesKeepTheirSwipeAndCollisionBehavior() = withTheme(PunctuationPosition.Top) {
        val positions = listOf(AltTextPosition.Top, AltTextPosition.TopRight, AltTextPosition.Bottom)
        for (primary in positions) for (secondary in positions) {
            val key = createKey(primary, secondary)
            val primaryTop = primary != AltTextPosition.Bottom
            val secondaryTop = secondary != AltTextPosition.Bottom
            if (primaryTop == secondaryTop) {
                assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(-20))
                assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(20))
                if (primary == AltTextPosition.TopRight) {
                    assertTrue(key.altText.left > key.altText1.left)
                } else {
                    assertTrue(key.altText.left < key.altText1.left)
                }
            } else {
                assertEquals(primaryTop, key.altText.top < key.altText1.top)
                assertEquals(if (primaryTop) AltTextSwipeTarget.Primary else AltTextSwipeTarget.Secondary,
                    key.selectAltTextSwipeTarget(-20))
                assertEquals(if (primaryTop) AltTextSwipeTarget.Secondary else AltTextSwipeTarget.Primary,
                    key.selectAltTextSwipeTarget(20))
            }
        }
        // Preserve the existing collision fallback even when the primary text is empty.
        val single = createKey(AltTextPosition.TopRight, AltTextPosition.TopRight, "")
        assertEquals(View.GONE, single.altText.visibility)
        assertEquals(single.getChildAt(0).width / 2f,
            single.altText1.left + single.altText1.renderedGlyphBounds().centerX(), 1f)
    }

    private fun createKey(primary: AltTextPosition?, secondary: AltTextPosition, text: String = "?"): AltTextKeyView {
        val appearance = KeyDef.Appearance.AltText("q", text, "q", "!", textSize = 24f).apply {
            altTextPositionOverride = primary
            altText1PositionOverride = secondary
        }
        val key = AltTextKeyView(context, ThemePreset.MaterialLight, appearance)
        val density = context.resources.displayMetrics.density
        val width = (48 * density).roundToInt()
        val height = (52 * density).roundToInt()
        repeat(3) {
            key.refreshLayout()
            key.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            key.layout(0, 0, width, height)
        }
        return key
    }

    private fun withTheme(position: PunctuationPosition, block: () -> Unit) {
        instrumentation.runOnMainSync {
            val previous = ThemeManager.prefs.punctuationPosition.getValue()
            val previousUppercase = ThemeManager.prefs.uppercasePosition.getValue()
            try {
                ThemeManager.prefs.punctuationPosition.setValue(position)
                ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.None)
                block()
            } finally {
                ThemeManager.prefs.punctuationPosition.setValue(previous)
                ThemeManager.prefs.uppercasePosition.setValue(previousUppercase)
            }
        }
    }
}
