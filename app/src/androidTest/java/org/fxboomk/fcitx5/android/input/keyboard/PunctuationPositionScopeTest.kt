/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.AltTextPosition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PunctuationPositionScopeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var originalPosition: PunctuationPosition
    private lateinit var originalUppercasePosition: UppercasePosition
    private var originalSideKeyStyle = false

    @Before
    fun savePreferences() {
        originalPosition = ThemeManager.prefs.punctuationPosition.getValue()
        originalUppercasePosition = ThemeManager.prefs.uppercasePosition.getValue()
        originalSideKeyStyle = ThemeManager.prefs.gboardStyleSideKeys.getValue()
        ThemeManager.prefs.uppercasePosition.setValue(UppercasePosition.None)
        ThemeManager.prefs.gboardStyleSideKeys.setValue(false)
    }

    @After
    fun restorePreferences() {
        ThemeManager.prefs.punctuationPosition.setValue(originalPosition)
        ThemeManager.prefs.uppercasePosition.setValue(originalUppercasePosition)
        ThemeManager.prefs.gboardStyleSideKeys.setValue(originalSideKeyStyle)
    }

    @Test
    fun all26LettersInBothCasesFollowEveryPunctuationPosition() = onMain {
        (('a'..'z') + ('A'..'Z')).forEach { character ->
            val key = textView(AlphabetKey(character.toString(), "!"))
            PunctuationPosition.entries.forEach { position ->
                applyPosition(key, position)
                assertSingleLabelPosition(key.altText, position)
                assertEquals(
                    AltTextSwipeTarget.Primary.takeIf {
                        position == PunctuationPosition.Top || position == PunctuationPosition.TopRight
                    },
                    key.selectAltTextSwipeTarget(-20)
                )
                assertEquals(
                    AltTextSwipeTarget.Primary.takeIf { position == PunctuationPosition.Bottom },
                    key.selectAltTextSwipeTarget(20)
                )
            }
        }
    }

    @Test
    fun letterIdentitySurvivesCustomAndChangingDisplayText() = onMain {
        val key = textView(AlphabetKey("q", "!", displayText = "自定义"))
        applyPosition(key, PunctuationPosition.TopRight)
        assertSingleLabelPosition(key.altText, PunctuationPosition.TopRight)

        key.mainText.text = "Q"
        applyPosition(key, PunctuationPosition.None)
        assertEquals(View.GONE, key.altText.visibility)

        key.mainText.text = "7"
        applyPosition(key, PunctuationPosition.Bottom)
        assertSingleLabelPosition(key.altText, PunctuationPosition.Bottom)
    }

    @Test
    fun nonletterCharactersKeepSingleLabelsAtBottomEvenWhenDisplayedAsLetters() = onMain {
        listOf("", "7", "!", "é", "中", "ab").forEach { character ->
            val key = AltTextKeyView(
                context,
                ThemePreset.MaterialLight,
                KeyDef.Appearance.AltText(
                    displayText = "a",
                    character = character,
                    altText = "HINT",
                    textSize = 23f
                )
            )
            applyPosition(key, PunctuationPosition.Bottom)
            val expected = textState(key)
            PunctuationPosition.entries.forEach { position ->
                applyPosition(key, position)
                assertSingleLabelPosition(key.altText, PunctuationPosition.Bottom)
                assertEquals("character=$character, position=$position", expected, textState(key))
                assertNull(key.selectAltTextSwipeTarget(-20))
                assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(20))
            }
        }
    }

    @Test
    fun macroSecondaryLabelsKeepTheirLayoutAndSwipeTargets() = onMain {
        val key = textView(
            MacroKey(
                label = "a",
                character = "macro",
                altLabel = "FIRST",
                altLabel1 = "SECOND",
                tap = MacroAction(listOf(MacroStep.Text("macro")))
            )
        )
        applyPosition(key, PunctuationPosition.Bottom)
        val expected = textState(key)
        PunctuationPosition.entries.forEach { position ->
            applyPosition(key, position)
            assertEquals(View.VISIBLE, key.altText.visibility)
            assertEquals(View.VISIBLE, key.altText1.visibility)
            assertEquals(expected, textState(key))
            assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(-20))
            assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(20))
        }
    }

    @Test
    fun iconLabelsKeepTheirLayoutAndExplicitPosition() = onMain {
        listOf(null, AltTextPosition.TopRight).forEach { override ->
            val appearance = CapsKey(swipeLabel = "HINT").appearance as KeyDef.Appearance.ImageAltText
            appearance.altTextPositionOverride = override
            val key = ImageAltTextKeyView(context, ThemePreset.MaterialLight, appearance)
            applyPosition(key, PunctuationPosition.Bottom)
            val expectedLabel = labelState(key.altText)
            val expectedIcon = bounds(key.img)
            val expectedPosition = if (override == null) PunctuationPosition.Bottom else PunctuationPosition.TopRight
            PunctuationPosition.entries.forEach { position ->
                applyPosition(key, position)
                assertSingleLabelPosition(key.altText, expectedPosition)
                assertEquals(expectedLabel, labelState(key.altText))
                assertEquals(expectedIcon, bounds(key.img))
            }
        }
    }

    @Test
    fun explicitTextLabelPositionsSurviveEveryGlobalPosition() = onMain {
        listOf("a", "7").forEach { character ->
            val appearance = AlphabetKey(character, "FIRST", "SECOND").appearance
            appearance.altTextPositionOverride = AltTextPosition.TopRight
            appearance.altText1PositionOverride = AltTextPosition.Bottom
            val key = AltTextKeyView(context, ThemePreset.MaterialLight, appearance as KeyDef.Appearance.AltText)
            applyPosition(key, PunctuationPosition.Bottom)
            val expected = textState(key)
            PunctuationPosition.entries.forEach { position ->
                applyPosition(key, position)
                assertSingleLabelPosition(key.altText, PunctuationPosition.TopRight)
                assertSingleLabelPosition(key.altText1, PunctuationPosition.Bottom)
                assertEquals(expected, textState(key))
                assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(-20))
                assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(20))
            }
        }
    }

    @Test
    fun unspecifiedSecondLabelOnNonletterKeepsBottomWithPrimaryOverride() = onMain {
        val appearance = AlphabetKey("7", "FIRST", "SECOND").appearance
        appearance.altTextPositionOverride = AltTextPosition.Top
        val key = AltTextKeyView(context, ThemePreset.MaterialLight, appearance as KeyDef.Appearance.AltText)
        PunctuationPosition.entries.forEach { position ->
            applyPosition(key, position)
            assertSingleLabelPosition(key.altText, PunctuationPosition.Top)
            assertSingleLabelPosition(key.altText1, PunctuationPosition.Bottom)
            assertEquals(AltTextSwipeTarget.Primary, key.selectAltTextSwipeTarget(-20))
            assertEquals(AltTextSwipeTarget.Secondary, key.selectAltTextSwipeTarget(20))
        }
    }

    private fun textView(definition: KeyDef) = AltTextKeyView(
        context, ThemePreset.MaterialLight, definition.appearance as KeyDef.Appearance.AltText
    )

    private fun applyPosition(key: KeyView, position: PunctuationPosition) {
        ThemeManager.prefs.punctuationPosition.setValue(position)
        key.updateTheme(ThemePreset.MaterialLight)
        val size = (96 * context.resources.displayMetrics.density).toInt()
        repeat(2) {
            key.measure(
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
            )
            key.layout(0, 0, size, size)
        }
    }

    private fun assertSingleLabelPosition(label: AutoScaleTextView, position: PunctuationPosition) {
        assertEquals(if (position == PunctuationPosition.None) View.GONE else View.VISIBLE, label.visibility)
        if (position == PunctuationPosition.None) return
        val params = label.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(
            ConstraintLayout.LayoutParams.PARENT_ID,
            if (position == PunctuationPosition.Bottom) params.bottomToBottom else params.topToTop
        )
        assertEquals(
            if (position == PunctuationPosition.TopRight) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER,
            label.gravity
        )
    }

    private fun textState(key: AltTextKeyView) = Triple(
        labelState(key.altText), labelState(key.altText1), bounds(key.mainText)
    )

    private fun labelState(label: AutoScaleTextView) = Triple(label.visibility, label.gravity, bounds(label))

    private fun bounds(view: View) = Rect(view.left, view.top, view.right, view.bottom)

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
