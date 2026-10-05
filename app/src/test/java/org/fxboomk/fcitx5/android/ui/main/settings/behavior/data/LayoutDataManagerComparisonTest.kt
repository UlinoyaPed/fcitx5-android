/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.data

import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.junit.Assert.*
import org.junit.Test

class LayoutDataManagerComparisonTest {
    private fun ime(name: String, displayName: String = name) =
        InputMethodEntry(name, displayName, "", "", "", "", "test", false)

    @Test fun onlyEnabledUncustomizedBasesAreListedWithoutMutatingStoredKeys() {
        val keys = mutableListOf("default", "pinyin", "rime", "shuangpin", "wubi")
        assertEquals(listOf("default", "rime", "shuangpin"), availableBaseLayoutNames(
            keys, arrayOf(ime("keyboard-us", "English"), ime("rime", "中州韵"), ime("shuangpin", "双拼"))
        ))
        assertEquals(listOf("default", "pinyin", "rime", "shuangpin", "wubi"), keys)
    }

    @Test fun storedDisplayNameAliasesDoNotCreateDuplicateBases() {
        assertEquals(listOf("拼音"), availableBaseLayoutNames(
            listOf("default", "拼音", "拼音:child"), arrayOf(ime("pinyin", "拼音"))
        ))
    }

    @Test fun disabledUncustomizedBasesAreHiddenWhenNoImesAreEnabled() {
        assertEquals(emptyList<String>(), availableBaseLayoutNames(
            listOf("default", "disabled-ime"), emptyArray()
        ))
    }

    @Test fun childOnlyLayoutsRemainAccessibleWithoutEnabledIme() {
        assertEquals(listOf("disabled-ime"), availableBaseLayoutNames(
            listOf("default", "disabled-ime:first", "disabled-ime:second"), emptyArray()
        ))
    }

    @Test fun missingEnabledBasesRemainVirtualAndDistinct() {
        assertEquals(listOf("pinyin", "rime"), availableBaseLayoutNames(
            listOf("default", "wubi"), arrayOf(ime("pinyin"), ime("rime"), ime("pinyin"))
        ))
    }

    @Test fun disabledCustomizationsAndSharedFallbackRemainAccessible() {
        assertEquals(listOf("default", "disabled-ime", "pinyin"), availableBaseLayoutNames(
            listOf("default", "disabled-ime", "wubi"), arrayOf(ime("pinyin")),
            setOf("default", "disabled-ime")
        ))
    }

    @Test fun explicitEnglishLayoutSuppressesUncustomizedDefaultDuplicate() {
        assertEquals(listOf("keyboard-us"), availableBaseLayoutNames(
            listOf("default", "keyboard-us"), arrayOf(ime("keyboard-us", "English"))
        ))
        assertEquals(listOf("English"), availableBaseLayoutNames(
            listOf("default", "English"), arrayOf(ime("keyboard-us", "English"))
        ))
    }

    @Test fun explicitEnglishDoesNotHideCustomizedSharedFallback() {
        assertEquals(listOf("default", "keyboard-us"), availableBaseLayoutNames(
            listOf("default", "keyboard-us"), arrayOf(ime("keyboard-us", "English")), setOf("default")
        ))
    }

    @Test fun uniqueNameWinsOverUncustomizedDisplayNameAlias() {
        assertEquals(listOf("pinyin"), availableBaseLayoutNames(
            listOf("default", "拼音", "pinyin"), arrayOf(ime("pinyin", "拼音"))
        ))
    }

    @Test fun englishFallbackDoesNotDependOnTranslatedDisplayName() {
        assertEquals(listOf("default"), availableBaseLayoutNames(
            listOf("default", "wubi"), arrayOf(ime("keyboard-us", "英语"))
        ))
    }

    @Test fun englishSelectionUsesDefaultEvenAfterAnEarlierHistoricalCustomization() {
        val english = ime("keyboard-us", "English")
        val visible = availableBaseLayoutNames(
            listOf("disabled-custom", "default"), arrayOf(english), setOf("disabled-custom")
        )
        assertEquals(listOf("disabled-custom", "default"), visible)
        assertEquals("default", baseLayoutKeyForIme(visible, english))
    }

    @Test fun selectionPrefersUniqueNameOverAnEarlierCustomizedAlias() {
        val english = ime("keyboard-us", "English")
        val visible = availableBaseLayoutNames(
            listOf("default", "English", "keyboard-us"), arrayOf(english), setOf("default", "English")
        )
        assertEquals(listOf("default", "English", "keyboard-us"), visible)
        assertEquals("keyboard-us", baseLayoutKeyForIme(visible, english))
    }

    @Test fun defaultChildGroupRemainsAccessibleAlongsideExplicitEnglish() {
        assertEquals(listOf("default", "keyboard-us"), availableBaseLayoutNames(
            listOf("default", "default:child", "keyboard-us"), arrayOf(ime("keyboard-us", "English"))
        ))
    }

    @Test fun numericAndOmittedNullFieldsCompareEqualAfterJsonRoundTrip() {
        assertTrue(deepEquals(
            listOf(mapOf("main" to "q", "weight" to null, "size" to 1)),
            listOf(mapOf("main" to "q", "size" to 1.0))
        ))
    }

    @Test fun changedKeysAndNestedPropertiesStillCompareUnequal() {
        assertFalse(deepEquals(mapOf("main" to "q"), mapOf("main" to "w")))
        assertFalse(deepEquals(mapOf("a" to 1), mapOf("b" to 1)))
        assertFalse(deepEquals(listOf(mapOf("displayText" to mapOf("mode" to "a"))),
            listOf(mapOf("displayText" to mapOf("mode" to "b")))))
    }
}
