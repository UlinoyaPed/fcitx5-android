/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class ImageAltTextIconLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun directionalBackspaceIconStaysCenteredWithUnevenLabels() = withPunctuationPosition(
        PunctuationPosition.Top
    ) {
        val definition = BackspaceKey(swipeUpLabel = ".", swipeDownLabel = "Enter")
        val key = ImageAltTextKeyView(
            context,
            ThemePreset.MaterialLight,
            definition.appearance as KeyDef.Appearance.ImageAltText,
        )
        layoutKey(key)
        assertIconCentered(key)
    }

    @Test
    fun bottomBackspaceSublabelDoesNotShiftIcon() = withPunctuationPosition(
        PunctuationPosition.Bottom
    ) {
        val definition = BackspaceKey(swipeLabel = "Enter")
        val key = ImageAltTextKeyView(
            context,
            ThemePreset.MaterialLight,
            definition.appearance as KeyDef.Appearance.ImageAltText,
        )
        layoutKey(key)
        assertIconCentered(key)
    }

    private fun assertIconCentered(key: ImageAltTextKeyView) {
        val keyFace = key.getChildAt(0)
        assertEquals(
            keyFace.height / 2f,
            (key.img.top + key.img.bottom) / 2f,
            1f,
        )
    }

    private fun layoutKey(key: ImageAltTextKeyView) {
        val width = dp(64)
        val height = dp(64)
        repeat(3) {
            key.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            key.layout(0, 0, width, height)
        }
    }

    private fun withPunctuationPosition(position: PunctuationPosition, block: () -> Unit) {
        instrumentation.runOnMainSync {
            val preference = ThemeManager.prefs.punctuationPosition
            val original = preference.getValue()
            try {
                preference.setValue(position)
                block()
            } finally {
                preference.setValue(original)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
