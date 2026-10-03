/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyWidthRatioConverterTest {
    @Test
    fun `legacy weights display as decimal width ratios`() {
        assertEquals("", KeyWidthRatioConverter.formatLegacyWeight(null))
        assertEquals("0.0", KeyWidthRatioConverter.formatLegacyWeight(0f))
        assertEquals("0.8", KeyWidthRatioConverter.formatLegacyWeight(0.08f))
        assertEquals("1.0", KeyWidthRatioConverter.formatLegacyWeight(0.1f))
        assertEquals("1.5", KeyWidthRatioConverter.formatLegacyWeight(0.15f))
        assertEquals("10.0", KeyWidthRatioConverter.formatLegacyWeight(1f))
    }

    @Test
    fun `edited ratios save in legacy weight units`() {
        assertEquals(0f, KeyWidthRatioConverter.resolveLegacyWeight(null, "0"))
        assertEquals(0.1f, KeyWidthRatioConverter.resolveLegacyWeight(null, "1.0"))
        assertEquals(0.15f, KeyWidthRatioConverter.resolveLegacyWeight(null, "1.5"))
        assertEquals(1f, KeyWidthRatioConverter.resolveLegacyWeight(null, "10"))
        assertNull(KeyWidthRatioConverter.resolveLegacyWeight(null, ""))
        assertNull(KeyWidthRatioConverter.resolveLegacyWeight(null, "10.1"))
        assertNull(KeyWidthRatioConverter.resolveLegacyWeight(null, "1e-2147483647"))
    }

    @Test
    fun `unchanged ratio preserves original weight without drift`() {
        val originalFloat = 0.15f
        val resolvedFloat = KeyWidthRatioConverter.resolveLegacyWeight(originalFloat, "1.5")
        assertTrue(resolvedFloat is Float)
        assertEquals(originalFloat.toBits(), (resolvedFloat as Float).toBits())

        val originalDouble = 0.15
        val resolvedDouble = KeyWidthRatioConverter.resolveLegacyWeight(originalDouble, "1.5")
        assertTrue(resolvedDouble is Double)
        assertEquals(originalDouble, resolvedDouble)
    }

    @Test
    fun `blank remains inherited and clearing removes explicit weight`() {
        assertNull(KeyWidthRatioConverter.resolveLegacyWeight(null, ""))
        assertNull(KeyWidthRatioConverter.resolveLegacyWeight(0.1f, ""))
    }
}
