/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fxboomk.fcitx5.android.input.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTransferTest {
    @Test
    fun toolbarThemeRoundTripsWithItsFormat() {
        val theme = ButtonsLayoutTheme("Compact", ButtonsLayoutConfig.default())
        val decoded = ThemeTransferCodec.decodeToolbar(ThemeTransferCodec.encodeToolbar(theme))
        assertEquals(theme, decoded)
    }

    @Test
    fun keyIconThemeRoundTripsWithItsFormat() {
        val theme = KeyIconTheme("High contrast", mapOf(KeyIconIds.BACKSPACE to "ic_custom_backspace"))
        val decoded = ThemeTransferCodec.decodeKeyIcons(ThemeTransferCodec.encodeKeyIcons(theme))
        assertEquals(theme, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importingTheOtherThemeTypeIsRejected() {
        ThemeTransferCodec.decodeKeyIcons(
            ThemeTransferCodec.encodeToolbar(
                ButtonsLayoutTheme("Toolbar", ButtonsLayoutConfig.default())
            )
        )
    }

    @Test
    fun iconFontCodePointAcceptsCommonUnicodeReferences() {
        assertEquals(0xE141, ButtonIconSpec.codePoint("font:E141"))
        assertEquals(0xE141, ButtonIconSpec.codePoint("U+E141"))
        assertEquals(0xE141, ButtonIconSpec.codePoint("0xE141"))
        assertEquals(0xE141, ButtonIconSpec.codePoint("&#xE141;"))
        assertEquals("\uE141", ButtonIconSpec.glyph("U+E141"))
    }
}
