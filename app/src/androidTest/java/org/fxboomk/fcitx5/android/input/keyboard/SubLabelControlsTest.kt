package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.SecondaryLabelPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.AltTextPosition
import org.fxboomk.fcitx5.android.ui.main.settings.SettingsSearchIndex
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class SubLabelControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun paddingMovesBothHintsInwardWithoutMovingOrShrinkingMain() = withPreferences {
        for (position in AltTextPosition.entries) {
            ThemeManager.prefs.subLabelPadding.setValue(0)
            val key = key(position)
            layout(key)
            val main = bounds(key.mainText)
            val mainSize = key.mainText.paint.textSize * key.mainText.textScaleX
            val hints = listOf(key.altText, key.altText1)
            val before = hints.map(::bounds)
            val center = key.getChildAt(0).height / 2f
            for (padding in listOf(2, 8, 0)) {
                ThemeManager.prefs.subLabelPadding.setValue(padding)
                layout(key)
                val inset = dp(padding).toFloat()
                hints.forEachIndexed { i, hint ->
                    val actual = bounds(hint)
                    val expectedY = before[i].top + if (before[i].centerY() < center) inset else -inset
                    assertEquals("$position hint $i padding $padding", expectedY, actual.top, 1f)
                }
                assertBounds(main, bounds(key.mainText))
                assertEquals(mainSize, key.mainText.paint.textSize * key.mainText.textScaleX, 0.01f)
                // Forced refreshes must use base anchors instead of accumulating padding.
                val stable = hints.map(::bounds)
                repeat(10) { layout(key) }
                hints.forEachIndexed { i, hint -> assertBounds(stable[i], bounds(hint)) }
            }
        }
    }

    @Test fun paddingInsetsFunctionKeyHintsToo() = withPreferences {
        ThemeManager.prefs.subLabelPadding.setValue(0)
        val key = ImageAltTextKeyView(context, ThemePreset.MaterialLight,
            KeyDef.Appearance.ImageAltText(R.drawable.ic_capslock_none, "!", altText1 = "?").apply {
                directionalSwipeLabels = true
            })
        layout(key)
        val before = listOf(bounds(key.altText), bounds(key.altText1))
        ThemeManager.prefs.subLabelPadding.setValue(4)
        layout(key)
        assertEquals(before[0].top + dp(4), bounds(key.altText).top, 1f)
        assertEquals(before[1].top - dp(4), bounds(key.altText1).top, 1f)
        repeat(10) { layout(key) }
        assertEquals(before[0].top + dp(4), bounds(key.altText).top, 1f)
    }

    @Test fun globalSecondPositionKeepsPhysicalEdgeAndSwipeTargets() = withPreferences {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Bottom)
        for (position in listOf(SecondaryLabelPosition.Top, SecondaryLabelPosition.TopRight,
            SecondaryLabelPosition.Bottom)) {
            ThemeManager.prefs.secondaryLabelPosition.setValue(position)
            val key = key()
            layout(key)
            if (position == SecondaryLabelPosition.Bottom) {
                assertTrue(key.altText.left < key.altText1.left)
            } else {
                assertTrue(bounds(key.altText1).bottom < bounds(key.altText).top)
            }
            assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(-20))
            assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(20))
        }
    }

    @Test fun hidingSecondHintKeepsFirstHintAndCenteredMain() = withPreferences {
        ThemeManager.prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.Top)
        val key = key()
        layout(key)
        val before = bounds(key.mainText)
        ThemeManager.prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.None)
        layout(key)
        assertEquals(View.GONE, key.altText1.visibility)
        assertEquals(View.VISIBLE, key.altText.visibility)
        assertNull(key.selectAltTextSwipeTarget(-20))
        assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(20))
        assertBounds(before, bounds(key.mainText))
    }

    @Test fun rowSecondPositionOverridesGlobalAndFollowLayoutPreservesExistingCorners() = withPreferences {
        ThemeManager.prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.Top)
        val row = key(AltTextPosition.Top).apply { def.altText1PositionOverride = AltTextPosition.Bottom }
        layout(row)
        assertTrue(bounds(row.altText).bottom < bounds(row.altText1).top)
        ThemeManager.prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.FollowLayout)
        val inherited = key()
        layout(inherited)
        assertEquals(bounds(inherited.altText).top, bounds(inherited.altText1).top, 1f)
        assertTrue(inherited.altText.left < inherited.altText1.left)
    }

    @Test fun uppercaseHintUsesSecondPositionWithoutEnablingHiddenUppercaseHints() = withPreferences {
        ThemeManager.prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.TopRight)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.Bottom)
        val key = AltTextKeyView(context, ThemePreset.MaterialLight,
            KeyDef.Appearance.AltText("q", "!", "q", supportsUppercaseHint = true, textSize = 23f))
        layout(key)
        assertEquals(View.VISIBLE, key.upperText.visibility)
        assertTrue(bounds(key.upperText).bottom < bounds(key.altText).top)
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.None)
        layout(key)
        assertEquals(View.GONE, key.upperText.visibility)
    }

    @Test fun functionSwipeLabelsKeepTheirDirectionsWhenGlobalSecondPositionChanges() = withPreferences {
        val key = key().apply { def.directionalSwipeLabels = true }
        for (position in SecondaryLabelPosition.entries) {
            ThemeManager.prefs.secondaryLabelPosition.setValue(position)
            layout(key)
            assertEquals(View.VISIBLE, key.altText1.visibility)
            assertTrue(bounds(key.altText).top < bounds(key.altText1).top)
            assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(-20))
            assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(20))
        }
    }

    @Test fun bothControlsAreDiscoverableInSettingsSearch() = withPreferences {
        val keys = SettingsSearchIndex.androidItems(context).mapNotNull { it.preferenceKey }.toSet()
        assertTrue("sub_label_padding" in keys)
        assertTrue("secondary_label_position" in keys)
    }

    private fun key(position: AltTextPosition? = null) = AltTextKeyView(context,
        ThemePreset.MaterialLight, KeyDef.Appearance.AltText("q", "!", "q", "?", textSize = 23f).apply {
            altTextPositionOverride = position
        }).apply {
            mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            listOf(altText, altText1).forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) }
        }

    private fun layout(key: KeyView) {
        val width = dp(70)
        val height = dp(80)
        repeat(3) {
            when (key) {
                is AltTextKeyView -> key.refreshLayout()
                is ImageAltTextKeyView -> key.updateTheme(ThemePreset.MaterialLight)
            }
            key.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            key.layout(0, 0, width, height)
        }
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
        val oldPadding = prefs.subLabelPadding.getValue()
        val oldSecond = prefs.secondaryLabelPosition.getValue()
        val oldFirst = prefs.punctuationPosition.getValue()
        val oldUpper = prefs.uppercasePosition.getValue()
        try {
            prefs.subLabelPadding.setValue(2)
            prefs.secondaryLabelPosition.setValue(SecondaryLabelPosition.FollowLayout)
            prefs.punctuationPosition.setValue(PunctuationPosition.Bottom)
            prefs.uppercasePosition.setValue(UppercasePosition.None)
            block()
        } finally {
            prefs.subLabelPadding.setValue(oldPadding)
            prefs.secondaryLabelPosition.setValue(oldSecond)
            prefs.punctuationPosition.setValue(oldFirst)
            prefs.uppercasePosition.setValue(oldUpper)
        }
    }
}
