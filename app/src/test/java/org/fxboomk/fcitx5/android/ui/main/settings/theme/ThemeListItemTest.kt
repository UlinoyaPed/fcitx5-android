/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeListItemTest {
    private val light = ThemePreset.MaterialLight
    private val dark = ThemePreset.MaterialDark
    private val customLight = light.deriveCustomNoBackground("Dark-looking name")
    private val customDark = dark.deriveCustomNoBackground("Light-looking name")
    private val themes = listOf(dark, customLight, light, customDark)

    @Test
    fun groupsByColorMetadataAndPreservesOrderWithinEachGroup() {
        assertEquals(
            listOf(
                ThemeListItem.Add,
                ThemeListItem.Header(false, 2, true),
                ThemeListItem.Card(customLight), ThemeListItem.Card(light),
                ThemeListItem.Header(true, 2, true),
                ThemeListItem.Card(dark), ThemeListItem.Card(customDark)
            ),
            buildThemeListItems(themes, emptySet())
        )
    }

    @Test
    fun eitherGroupCanCollapseWithoutHidingTheOther() {
        for (isDark in listOf(false, true)) {
            val items = buildThemeListItems(themes, setOf(isDark))
            assertEquals(themes.filter { it.isDark != isDark }, items.filterIsInstance<ThemeListItem.Card>().map { it.theme })
            assertEquals(ThemeListItem.Header(isDark, 2, false), items.filterIsInstance<ThemeListItem.Header>().single { it.isDark == isDark })
        }
    }

    @Test
    fun bothCollapsedKeepAddEntryAndBothHeaders() {
        assertEquals(
            listOf(ThemeListItem.Add, ThemeListItem.Header(false, 2, false), ThemeListItem.Header(true, 2, false)),
            buildThemeListItems(themes, setOf(false, true))
        )
    }

    @Test
    fun refreshedThemeMovesToItsNewColorGroup() {
        val edited = customLight.copy(isDark = true)
        val items = buildThemeListItems(listOf(edited, dark), setOf(false))
        assertEquals(listOf(edited, dark), items.filterIsInstance<ThemeListItem.Card>().map { it.theme })
        assertEquals(ThemeListItem.Header(false, 0, false), items[1])
        assertEquals(ThemeListItem.Header(true, 2, true), items[2])
    }
}
