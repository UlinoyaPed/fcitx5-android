package org.fxboomk.fcitx5.android.input.keyboard

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SwipeLetterCaseTest {
    @Test fun customSublabelsFollowUnicodeCaseInBothDirections() {
        for ((lower, upper) in listOf("α" to "Α", "β" to "Β", "ä" to "Ä", "ü" to "Ü", "ö" to "Ö", "ß" to "ẞ")) {
            assertEquals(upper, transformSwipeLetters(lower, true))
            assertEquals(lower, transformSwipeLetters(upper, false))
        }
        assertEquals("σος", transformSwipeLetters("ΣΟΣ", false))
        assertEquals("𐐀", transformSwipeLetters("𐐨", true))
    }
    @Test fun nonLettersAndMixedLiteralLabelsRemainLiteral() {
        for (text in listOf("?!", "9", "😄", "abc?", "", " ", "\u0301")) {
            assertFalse(isSwipeLetterText(text))
            assertEquals(text, transformSwipeLetters(text, true))
        }
    }
    @Test fun overridesSupportReversiblePairsAndUnicodeFallback() {
        assertEquals("SS", transformSwipeLetters("ß", true, ""))
        assertEquals("SS", transformSwipeLetters("ß", true, "ß=SS"))
        assertEquals("ß", transformSwipeLetters("SS", false, "ß=SS"))
        assertEquals("ẞÄ", transformSwipeLetters("ßä", true))
        assertEquals("E\u0301", transformSwipeLetters("e\u0301", true))
    }
    @Test fun invalidOrAmbiguousMappingsAreRejected() {
        assertTrue(isValidSwipeCaseMappings(""))
        assertTrue(isValidSwipeCaseMappings("ß=ẞ\nα=Α"))
        for (text in listOf("ß", "ß=ẞ=x", "=ẞ", "α=α", "ß=ẞ\nß=SS", "1=A", "😄=A")) assertFalse(text, isValidSwipeCaseMappings(text))
    }
    @Test fun conversionDoesNotDependOnDeviceLocale() {
        val old = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("tr")); assertEquals("I", transformSwipeLetters("i", true)) }
        finally { Locale.setDefault(old) }
    }
}
