/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.Color
import android.view.Gravity
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DirectionalSwipeLabelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var originalPosition: PunctuationPosition
    private var originalSideKeyStyle = false

    @Before
    fun savePreferences() {
        originalPosition = ThemeManager.prefs.punctuationPosition.getValue()
        originalSideKeyStyle = ThemeManager.prefs.gboardStyleSideKeys.getValue()
        ThemeManager.prefs.gboardStyleSideKeys.setValue(false)
    }

    @After
    fun restorePreferences() {
        ThemeManager.prefs.punctuationPosition.setValue(originalPosition)
        ThemeManager.prefs.gboardStyleSideKeys.setValue(originalSideKeyStyle)
    }

    @Test
    fun allFiveKeyTypesKeepUpAboveDownRegardlessOfRowPosition() = onMain {
        listOf(PunctuationPosition.Top, PunctuationPosition.TopRight, PunctuationPosition.Bottom)
            .forEach { position ->
                ThemeManager.prefs.punctuationPosition.setValue(position)
                definitions("UP", "DOWN").forEach { definition ->
                    definition.appearance.altTextPositionOverride = KeyDef.Appearance.AltTextPosition.Bottom
                    definition.appearance.altText1PositionOverride = KeyDef.Appearance.AltTextPosition.Top
                    val key = createView(definition)
                    layout(key.view)

                    assertEquals(View.VISIBLE, key.up.visibility)
                    assertEquals(View.VISIBLE, key.down.visibility)
                    assertTrue(key.up.bottom < key.down.top)
                    assertEquals(
                        if (position == PunctuationPosition.TopRight) {
                            Gravity.END or Gravity.CENTER_VERTICAL
                        } else Gravity.CENTER,
                        key.up.gravity
                    )
                    assertEquals(AltTextSwipeTarget.Primary, key.hints.selectAltTextSwipeTarget(-20))
                    assertEquals(AltTextSwipeTarget.Secondary, key.hints.selectAltTextSwipeTarget(20))
                }
            }
    }

    @Test
    fun downOnlyLabelRemainsAtBottomWhenGlobalPositionIsTop() = onMain {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Top)
        definitions(null, "DOWN").forEach { definition ->
            val key = createView(definition)
            layout(key.view)

            assertEquals(View.GONE, key.up.visibility)
            assertEquals(View.VISIBLE, key.down.visibility)
            assertEquals(
                ConstraintLayout.LayoutParams.PARENT_ID,
                (key.down.layoutParams as ConstraintLayout.LayoutParams).bottomToBottom
            )
            assertNull(key.hints.selectAltTextSwipeTarget(-20))
            assertEquals(AltTextSwipeTarget.Secondary, key.hints.selectAltTextSwipeTarget(20))
        }
    }

    @Test
    fun hiddenPunctuationHidesBothDirectionalLabelsDespiteRowOverrides() = onMain {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.None)
        definitions("UP", "DOWN").forEach { definition ->
            definition.appearance.altTextPositionOverride = KeyDef.Appearance.AltTextPosition.Top
            definition.appearance.altText1PositionOverride = KeyDef.Appearance.AltTextPosition.Bottom
            val key = createView(definition)
            layout(key.view)

            assertEquals(View.GONE, key.up.visibility)
            assertEquals(View.GONE, key.down.visibility)
            assertNull(key.hints.selectAltTextSwipeTarget(-20))
            assertNull(key.hints.selectAltTextSwipeTarget(20))
        }
    }

    @Test
    fun changingDirectionalLabelContentRefreshesVisibilityAtTheSameHeight() = onMain {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Top)
        definitions(null, "DOWN").forEach { definition ->
            val key = createView(definition)
            layout(key.view)
            key.up.text = "UP"
            key.down.text = ""
            layout(key.view)

            assertEquals(View.VISIBLE, key.up.visibility)
            assertEquals(View.GONE, key.down.visibility)
            assertEquals(AltTextSwipeTarget.Primary, key.hints.selectAltTextSwipeTarget(-20))
            assertNull(key.hints.selectAltTextSwipeTarget(20))
        }
    }

    @Test
    fun returnIconAndBothLabelsKeepAccentColorsAfterThemeChanges() = onMain {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Top)
        val key = createView(ReturnKey(swipeUpLabel = "UP", swipeDownLabel = "DOWN"))
        val imageKey = key.view as ImageAltTextKeyView
        assertEquals(ThemePreset.MaterialLight.accentKeyTextColor, imageKey.img.imageTintList?.defaultColor)
        assertEquals(ThemePreset.MaterialLight.accentKeyTextColor, key.up.currentTextColor)
        assertEquals(ThemePreset.MaterialLight.accentKeyTextColor, key.down.currentTextColor)

        imageKey.updateTheme(ThemePreset.MaterialDark)

        assertEquals(ThemePreset.MaterialDark.accentKeyTextColor, imageKey.img.imageTintList?.defaultColor)
        assertEquals(ThemePreset.MaterialDark.accentKeyTextColor, key.up.currentTextColor)
        assertEquals(ThemePreset.MaterialDark.accentKeyTextColor, key.down.currentTextColor)
    }

    @Test
    fun imageLabelsKeepCustomColorAndScaleTogetherWhileIconCanChange() = onMain {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.TopRight)
        val appearance = KeyDef.Appearance.ImageAltText(
            src = R.drawable.ic_capslock_none,
            altText = "UP",
            altText1 = "DOWN",
            viewId = R.id.button_caps,
            altTextColor = Color.MAGENTA
        ).apply { directionalSwipeLabels = true }
        val key = ImageAltTextKeyView(context, ThemePreset.MaterialLight, appearance)
        val originalTextSize = key.altText.textSize
        key.setTextScale(1.5f)
        key.updateTheme(ThemePreset.MaterialDark)
        (key as KeyViewWithImage).img.setImageResource(R.drawable.ic_capslock_lock)
        layout(key)

        assertEquals(originalTextSize * 1.5f, key.altText.textSize, 0.01f)
        assertEquals(key.altText.textSize, key.altText1.textSize, 0.01f)
        assertEquals(Color.MAGENTA, key.altText.currentTextColor)
        assertEquals(Color.MAGENTA, key.altText1.currentTextColor)
        assertTrue(key.img.drawable != null)
        assertTrue(key.altText.bottom <= key.img.top)
        assertTrue(key.img.bottom <= key.altText1.top)
    }

    @Test
    fun legacySingleLabelViewsStillFollowBottomPosition() = onMain {
        ThemeManager.prefs.punctuationPosition.setValue(PunctuationPosition.Bottom)
        listOf(
            SymbolKey("!", swipeLabel = "LEGACY"),
            CapsKey(swipeLabel = "LEGACY")
        ).forEach { definition ->
            val key = createView(definition)
            layout(key.view)

            assertEquals(View.VISIBLE, key.up.visibility)
            assertEquals(View.GONE, key.down.visibility)
            assertEquals(
                ConstraintLayout.LayoutParams.PARENT_ID,
                (key.up.layoutParams as ConstraintLayout.LayoutParams).bottomToBottom
            )
            assertNull(key.hints.selectAltTextSwipeTarget(-20))
            assertEquals(AltTextSwipeTarget.Primary, key.hints.selectAltTextSwipeTarget(20))
        }
    }

    private fun definitions(up: String?, down: String?) = listOf(
        CapsKey(swipeUpLabel = up, swipeDownLabel = down),
        LayoutSwitchKey("?123", swipeUpLabel = up, swipeDownLabel = down),
        SymbolKey("!", swipeUpLabel = up, swipeDownLabel = down),
        ReturnKey(swipeUpLabel = up, swipeDownLabel = down),
        BackspaceKey(swipeUpLabel = up, swipeDownLabel = down)
    )

    private fun createView(definition: KeyDef): Labels = when (val appearance = definition.appearance) {
        is KeyDef.Appearance.AltText -> {
            val view = AltTextKeyView(context, ThemePreset.MaterialLight, appearance)
            Labels(view, view.altText, view.altText1, view)
        }
        is KeyDef.Appearance.ImageAltText -> {
            val view = ImageAltTextKeyView(context, ThemePreset.MaterialLight, appearance)
            Labels(view, view.altText, view.altText1, view)
        }
        else -> error("Expected a labelled appearance for ${definition.javaClass.simpleName}")
    }

    private fun layout(view: KeyView) {
        val size = (96 * context.resources.displayMetrics.density).toInt()
        repeat(2) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, size, size)
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private data class Labels(
        val view: KeyView,
        val up: AutoScaleTextView,
        val down: AutoScaleTextView,
        val hints: SwipeHintAwareKeyView
    )
}
