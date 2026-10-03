/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.theme

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ThemePoolMembershipTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun withPools(block: () -> Unit) = instrumentation.runOnMainSync {
        val prefs = ThemeManager.prefs
        val storage = prefs.lightModeThemes.sharedPreferences
        val keys = listOf(
            prefs.lightModeThemes.key, prefs.darkModeThemes.key,
            prefs.currentLightThemeIndex.key, prefs.currentDarkThemeIndex.key,
            prefs.followSystemDayNightTheme.key, prefs.normalModeTheme.key
        )
        val saved = storage.all.filterKeys { it in keys }
        val configuration = Configuration(instrumentation.targetContext.resources.configuration)
        try {
            block()
        } finally {
            storage.edit().apply {
                keys.forEach { remove(it) }
                saved.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> error("Unexpected preference type: $key")
                    }
                }
            }.commit()
            ThemeManager.onSystemPlatteChange(configuration)
        }
    }

    private fun themes(dark: Boolean) = ThemeManager.getAllThemes()
        .filter { it is Theme.Builtin && it.isDark == dark }.take(3)
        .also { assertEquals(3, it.size) }

    @Test
    fun removingAndAddingEarlierMembersPreservesSelectionInBothPools() = withPools {
        val prefs = ThemeManager.prefs
        for (dark in listOf(false, true)) {
            val themes = themes(dark)
            val pool = if (dark) prefs.darkModeThemes else prefs.lightModeThemes
            val index = if (dark) prefs.currentDarkThemeIndex else prefs.currentLightThemeIndex
            pool.setValue(themes.map { it.name }.toSet())
            index.setValue(1)
            prefs.followSystemDayNightTheme.setValue(true)
            ThemeManager.onSystemPlatteChange(Configuration().apply {
                uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            })
            val observed = mutableListOf<String>()
            val listener = ThemeManager.OnThemeChangeListener { observed += it.name }
            ThemeManager.addOnChangedListener(listener)
            try {
                assertEquals(themes[1].name, ThemeManager.activeTheme.name)
                assertFalse(ThemeManager.toggleThemePoolMembership(themes[0]))
                assertEquals(0, index.getValue())
                assertEquals(themes[1].name, ThemeManager.activeTheme.name)
                assertTrue(ThemeManager.toggleThemePoolMembership(themes[0]))
                assertEquals(1, index.getValue())
                assertEquals(themes[1].name, ThemeManager.activeTheme.name)
                assertTrue("No transient switch to a different theme", observed.all { it == themes[1].name })
            } finally {
                ThemeManager.removeOnChangedListener(listener)
            }
        }
    }

    @Test
    fun removingSelectedLastMemberNormalizesIndexAndNextCycle() = withPools {
        val prefs = ThemeManager.prefs
        val themes = themes(false)
        prefs.lightModeThemes.setValue(themes.map { it.name }.toSet())
        prefs.currentLightThemeIndex.setValue(2)
        prefs.followSystemDayNightTheme.setValue(true)
        ThemeManager.onSystemPlatteChange(Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_NO })
        assertFalse(ThemeManager.toggleThemePoolMembership(themes[2]))
        assertEquals(1, prefs.currentLightThemeIndex.getValue())
        assertEquals(themes[1].name, ThemeManager.getCurrentLightTheme().name)
        assertEquals(themes[0].name, ThemeManager.toggleConfiguredDayNightTheme().name)
    }

    @Test
    fun removingSharedLastMemberUpdatesBothPoolsBeforeListenersRun() = withPools {
        val prefs = ThemeManager.prefs
        val theme = themes(false).first()
        prefs.lightModeThemes.setValue(setOf(theme.name))
        prefs.darkModeThemes.setValue(setOf(theme.name))
        prefs.currentLightThemeIndex.setValue(7)
        prefs.currentDarkThemeIndex.setValue(9)
        val storage = prefs.lightModeThemes.sharedPreferences
        var notifications = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            notifications++
            assertTrue(prefs.lightModeThemes.getValue().isEmpty())
            assertTrue(prefs.darkModeThemes.getValue().isEmpty())
            assertEquals(0, prefs.currentLightThemeIndex.getValue())
            assertEquals(0, prefs.currentDarkThemeIndex.getValue())
        }
        storage.registerOnSharedPreferenceChangeListener(listener)
        try {
            assertFalse(ThemeManager.toggleThemePoolMembership(theme))
            assertTrue(notifications > 0)
        } finally {
            storage.unregisterOnSharedPreferenceChangeListener(listener)
        }
        assertEquals(ThemePreset.PixelLight.name, ThemeManager.getCurrentLightTheme().name)
        assertEquals(ThemePreset.PixelDark.name, ThemeManager.getCurrentDarkTheme().name)
        assertTrue(ThemeManager.toggleThemePoolMembership(theme))
        assertEquals(setOf(theme.name), prefs.lightModeThemes.getValue())
        assertTrue(prefs.darkModeThemes.getValue().isEmpty())
        assertEquals(0, prefs.currentLightThemeIndex.getValue())
    }
}
