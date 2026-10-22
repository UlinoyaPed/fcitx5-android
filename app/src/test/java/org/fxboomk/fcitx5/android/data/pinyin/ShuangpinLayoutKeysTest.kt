/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuangpinLayoutKeysTest {

    @Test
    fun suffixRoundTripsThroughUniqueName() {
        assertEquals("shuangpin-mysp", ShuangpinLayoutKeys.uniqueNameForSuffix("mysp"))
        assertEquals("mysp", ShuangpinLayoutKeys.suffixOf("shuangpin-mysp"))
        assertEquals(
            ShuangpinLayoutKeys.DEFAULT_CUSTOM_SUFFIX,
            ShuangpinLayoutKeys.suffixOf(
                ShuangpinLayoutKeys.uniqueNameForSuffix(ShuangpinLayoutKeys.DEFAULT_CUSTOM_SUFFIX)
            )
        )
    }

    @Test
    fun schemeUniqueNameDetectionExcludesBaseAndOtherKeys() {
        assertTrue(ShuangpinLayoutKeys.isSchemeUniqueName("shuangpin-xiaohe"))
        assertTrue(ShuangpinLayoutKeys.isSchemeUniqueName("shuangpin-custom"))
        // The base layout key itself is NOT a scheme.
        assertFalse(ShuangpinLayoutKeys.isSchemeUniqueName(ShuangpinLayoutKeys.BASE_LAYOUT_KEY))
        // Prefix without a suffix is not a scheme.
        assertFalse(ShuangpinLayoutKeys.isSchemeUniqueName(ShuangpinLayoutKeys.SCHEME_PREFIX))
        // Unrelated layout keys (English factory preset, other IMEs, Rime sub-modes).
        assertFalse(ShuangpinLayoutKeys.isSchemeUniqueName("default"))
        assertFalse(ShuangpinLayoutKeys.isSchemeUniqueName("pinyin"))
        assertFalse(ShuangpinLayoutKeys.isSchemeUniqueName("rime:倉頡五代"))
        assertNull(ShuangpinLayoutKeys.suffixOf("default"))
    }

    @Test
    fun builtinSuffixDetectionIsCaseInsensitive() {
        assertTrue(ShuangpinLayoutKeys.isBuiltinSchemeSuffix("xiaohe"))
        assertTrue(ShuangpinLayoutKeys.isBuiltinSchemeSuffix("XIAOHE"))
        assertFalse(ShuangpinLayoutKeys.isBuiltinSchemeSuffix("mysp"))
    }

    @Test
    fun customSchemeDetectionFollowsBuiltinSuffixes() {
        assertTrue(ShuangpinLayoutKeys.isCustomSchemeUniqueName("shuangpin-custom"))
        assertTrue(ShuangpinLayoutKeys.isCustomSchemeUniqueName("shuangpin-mysp"))
        assertFalse(ShuangpinLayoutKeys.isCustomSchemeUniqueName("shuangpin-xiaohe"))
        assertFalse(ShuangpinLayoutKeys.isCustomSchemeUniqueName("shuangpin"))
        assertFalse(ShuangpinLayoutKeys.isCustomSchemeUniqueName("default"))
    }

    @Test
    fun validCustomSuffixesAreAccepted() {
        assertTrue(ShuangpinLayoutKeys.isValidCustomSuffix("mysp"))
        assertTrue(ShuangpinLayoutKeys.isValidCustomSuffix("my-sp"))
        assertTrue(ShuangpinLayoutKeys.isValidCustomSuffix("abc123"))
        // Whitespace and case are normalised before validation.
        assertTrue(ShuangpinLayoutKeys.isValidCustomSuffix("  MySP  "))
        // Exactly the maximum length is allowed.
        assertTrue(ShuangpinLayoutKeys.isValidCustomSuffix("a".repeat(32)))
    }

    @Test
    fun invalidCustomSuffixesAreRejected() {
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix(""))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("   "))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("-abc"))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("abc-"))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("-"))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("ab_c"))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("双拼"))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("ab c"))
        // One over the maximum length.
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("a".repeat(33)))
        // Builtin suffixes must not be shadowed by a custom scheme (case-insensitive).
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("xiaohe"))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("XIAOHE"))
        assertFalse(ShuangpinLayoutKeys.isValidCustomSuffix("pinyinjiajia"))
    }

    @Test
    fun parentBaseLayoutKeyOnlyForSchemes() {
        assertEquals(ShuangpinLayoutKeys.BASE_LAYOUT_KEY, ShuangpinLayoutKeys.parentBaseLayoutKey("shuangpin-xiaohe"))
        assertEquals(ShuangpinLayoutKeys.BASE_LAYOUT_KEY, ShuangpinLayoutKeys.parentBaseLayoutKey("shuangpin-custom"))
        assertNull(ShuangpinLayoutKeys.parentBaseLayoutKey("shuangpin"))
        assertNull(ShuangpinLayoutKeys.parentBaseLayoutKey("default"))
        assertNull(ShuangpinLayoutKeys.parentBaseLayoutKey("rime:倉頡五代"))
    }
}
