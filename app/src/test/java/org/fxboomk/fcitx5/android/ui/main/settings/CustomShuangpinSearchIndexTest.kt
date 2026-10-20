/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */

package org.fxboomk.fcitx5.android.ui.main.settings

import org.fxboomk.fcitx5.android.data.pinyin.CustomShuangpinSchemeCodec
import org.fxboomk.fcitx5.android.ui.main.settings.im.CustomShuangpinFragment
import org.fxboomk.fcitx5.android.ui.main.settings.im.CustomShuangpinMappingFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomShuangpinSearchIndexTest {

    private val labels = CustomShuangpinMappingLabels(
        pageTitle = "自定义双拼",
        tabTitle = "Syllable mapping",
        zeroInitialTitle = "Zero-initial marker",
        initialsTitle = "Initial mappings",
        finalsTitle = "Final mappings",
        syllablesTitle = "Syllable overrides",
        editSyllablesTitle = "Edit syllable overrides",
        noneTitle = "None",
        keyword = "Custom Shuangpin"
    )

    private val items = buildCustomShuangpinMappingItems(
        labels,
        mapOf("b" to "1", "zh" to "2"),
        mapOf("a" to "ai")
    )

    @Test
    fun everyEntryTargetsTheMappingTab() {
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.tab == CustomShuangpinFragment.TAB_MAPPING })
        assertTrue(items.all { it.route == SettingsRoute.CustomShuangpin })
        assertTrue(items.all { it.preferenceKey != null })
    }

    @Test
    fun groupEntriesAreSearchable() {
        val expected = mapOf(
            CustomShuangpinMappingFragment.KEY_ZERO_INITIAL to "Zero-initial marker",
            CustomShuangpinMappingFragment.KEY_INITIALS_GROUP to "Initial mappings",
            CustomShuangpinMappingFragment.KEY_FINALS_GROUP to "Final mappings",
            CustomShuangpinMappingFragment.KEY_SYLLABLES_GROUP to "Syllable overrides",
            CustomShuangpinMappingFragment.KEY_SYLLABLES to "Edit syllable overrides"
        )

        expected.forEach { (key, title) ->
            val entry = items.firstOrNull { it.preferenceKey == key }
            assertNotNull("missing entry for $key", entry)
            assertEquals(title, entry!!.title)
            assertTrue(entry.matches(title))
        }
    }

    @Test
    fun letterEntriesAreSearchableAndCarryTheMappingValue() {
        CustomShuangpinSchemeCodec.INITIALS.forEach { initial ->
            val entry = items.firstOrNull {
                it.preferenceKey == CustomShuangpinMappingFragment.KEY_INITIAL_PREFIX + initial
            }
            assertNotNull("missing initial entry for $initial", entry)
            assertEquals(initial, entry!!.title)
            assertTrue(entry.matches(initial))
        }
        CustomShuangpinSchemeCodec.FINALS.forEach { final ->
            val entry = items.firstOrNull {
                it.preferenceKey == CustomShuangpinMappingFragment.KEY_FINAL_PREFIX + final
            }
            assertNotNull("missing final entry for $final", entry)
            assertEquals(final, entry!!.title)
        }

        val b = items.first {
            it.preferenceKey == CustomShuangpinMappingFragment.KEY_INITIAL_PREFIX + "b"
        }
        assertEquals("1", b.summary)
        val zh = items.first {
            it.preferenceKey == CustomShuangpinMappingFragment.KEY_INITIAL_PREFIX + "zh"
        }
        assertEquals("2", zh.summary)
        val unmapped = items.first {
            it.preferenceKey == CustomShuangpinMappingFragment.KEY_INITIAL_PREFIX + "p"
        }
        assertEquals("None", unmapped.summary)
    }

    @Test
    fun mappingTabIsPartOfTheSearchPath() {
        val entry = items.first {
            it.preferenceKey == CustomShuangpinMappingFragment.KEY_INITIALS_GROUP
        }

        assertEquals(listOf("自定义双拼", "Syllable mapping"), entry.path)
        assertTrue(entry.matches("Syllable mapping"))
    }

    @Test
    fun searchResultHasNoTabByDefault() {
        val result = SettingsSearchResult(title = "Theme", path = listOf("Theme"))

        assertNull(result.tab)
    }
}
