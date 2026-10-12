package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.SecondaryLabelPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class BottomRightSubLabelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun rightMeansPhysicalRightInRtlLayouts() = withPreferences {
        val key = key().apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        layout(key)
        assertBottomRight(key, key.altText1)
    }

    @Test fun mixedEdgesKeepSecondHintAtBottomRightAndMainCentered() = withPreferences {
        val key = key()
        ThemeManager.prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.Bottom)
        layout(key)
        val main = bounds(key.mainText)
        val first = bounds(key.altText)
        ThemeManager.prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.BottomRight)
        repeat(10) { layout(key) }
        assertBottomRight(key, key.altText1)
        assertBounds(main, bounds(key.mainText))
        assertBounds(first, bounds(key.altText))
        assertEquals(key.getChildAt(0).height / 2f,
            key.mainText.top + key.mainText.renderedReferenceBounds().centerY(), 0.01f)
        assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(-20))
        assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(20))
    }

    @Test fun hiddenFirstHintKeepsSecondAtBottomRightWithAdjustableInset() = withPreferences {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        val key = key()
        for (padding in listOf(0, 2, 8)) {
            ThemeManager.prefs.subLabelPadding.setValue(padding)
            layout(key)
            assertEquals(View.GONE, key.altText.visibility)
            assertBottomRight(key, key.altText1)
            assertNull(key.selectAltTextSwipeTarget(-20))
            assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(20))
        }
    }

    @Test fun bottomPairKeepsBothCornersAndItsExistingSwipeOrder() = withPreferences {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Bottom)
        val key = key()
        layout(key)
        assertTrue(bounds(key.altText).centerX() < key.getChildAt(0).width / 2f)
        assertBottomRight(key, key.altText1)
        assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(-20))
        assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(20))
    }

    @Test fun enabledUppercaseHintCanUseBottomRight() = withPreferences {
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Top)
        val key = key(uppercase = true)
        layout(key)
        assertEquals(View.GONE, key.altText1.visibility)
        assertBottomRight(key, key.upperText)
        assertEquals(AltTextSwipeTarget.Uppercase, key.selectAltTextSwipeTarget(20))
    }

    @Test fun explicitRowPositionTakesPriorityOverGlobalBottomRight() = withPreferences {
        val key = key().apply {
            def.altText1PositionOverride = KeyDef.Appearance.AltTextPosition.Top
        }
        layout(key)
        assertTrue(bounds(key.altText1).bottom < key.getChildAt(0).height / 2f)
    }

    private fun key(uppercase: Boolean = false) = AltTextKeyView(context, ThemePreset.MaterialLight,
        KeyDef.Appearance.AltText("q", "!", "q", altText1 = if (uppercase) null else "?",
            supportsUppercaseHint = uppercase, textSize = 23f)).apply {
        listOf(altText, altText1, upperText).forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) }
    }

    private fun layout(key: AltTextKeyView) {
        repeat(3) {
            key.refreshLayout()
            key.measure(View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(62), View.MeasureSpec.EXACTLY))
            key.layout(0, 0, dp(48), dp(62))
        }
    }

    private fun assertBottomRight(key: AltTextKeyView, label: AutoScaleTextView) {
        val ink = bounds(label)
        assertEquals(View.VISIBLE, label.visibility)
        assertTrue(ink.centerX() > key.getChildAt(0).width / 2f)
        assertTrue(ink.centerY() > key.getChildAt(0).height / 2f)
        val padding = dp(ThemeManager.prefs.subLabelPadding.getValue())
        assertTrue(ink.right > key.getChildAt(0).width - key.hMargin - padding - dp(5))
        assertTrue(ink.right <= key.getChildAt(0).width - key.hMargin - padding + 1)
        assertTrue(ink.bottom <= key.getChildAt(0).height - key.vMargin - padding + 1)
    }

    private fun bounds(label: AutoScaleTextView) = label.renderedGlyphBounds().apply {
        offset(label.left.toFloat(), label.top.toFloat())
    }

    private fun assertBounds(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.right, actual.right, 0.01f)
        assertEquals(expected.bottom, actual.bottom, 0.01f)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private fun withPreferences(block: () -> Unit) = instrumentation.runOnMainSync {
        val prefs = ThemeManager.prefs
        val first = prefs.punctuationPosition.getValue()
        val second = prefs.secondaryLabelPosition.getValue()
        val upper = prefs.uppercasePosition.getValue()
        val padding = prefs.subLabelPadding.getValue()
        try {
            prefs.punctuationPosition.setValue(PunctuationPosition.Top)
            prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.BottomRight)
            prefs.uppercasePosition.setValue(UppercasePosition.None)
            prefs.subLabelPadding.setValue(2)
            block()
        } finally {
            prefs.punctuationPosition.setValue(first)
            prefs.secondaryLabelPosition.setValue(second)
            prefs.uppercasePosition.setValue(upper)
            prefs.subLabelPadding.setValue(padding)
        }
    }
}
