/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.Typeface
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.font.FontProviderApi
import org.fxboomk.fcitx5.android.input.font.FontProviders
import org.junit.Assert.assertEquals
import org.junit.Test

class UppercaseSubLabelTypefaceTest {

    @Test
    fun uppercaseHintIsNormalWithoutChangingOtherLabelStyles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val previousProvider = FontProviders.provider
            try {
                FontProviders.provider = object : FontProviderApi {
                    override fun clearCache() = Unit

                    override val fontTypefaceMap = mutableMapOf<String, Typeface?>(
                        "key_alt_font" to Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                    )

                    override val fontSizeMap = mutableMapOf<String, Float>()
                }
                val appearance = KeyDef.Appearance.AltText(
                    displayText = "q",
                    character = "q",
                    altText = "1",
                    altText1 = "!",
                    supportsUppercaseHint = true,
                    textSize = 24f,
                    textStyle = Typeface.BOLD_ITALIC
                )
                val key = AltTextKeyView(
                    instrumentation.targetContext,
                    ThemePreset.MaterialLight,
                    appearance
                )

                key.mainText.setFontTypeFace("key_main_font")
                key.altText.setFontTypeFace("key_alt_font")
                key.altText1.setFontTypeFace("key_alt_font")
                key.upperText.setFontTypeFace("key_alt_font")
                key.refreshLayout()

                assertEquals(Typeface.BOLD_ITALIC, key.mainText.typeface.style)
                assertEquals(Typeface.BOLD_ITALIC, key.altText.typeface.style)
                assertEquals(Typeface.BOLD_ITALIC, key.altText1.typeface.style)
                assertEquals(Typeface.ITALIC, key.upperText.typeface.style)
            } finally {
                FontProviders.provider = previousProvider
            }
        }
    }
}
