/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import org.fxboomk.fcitx5.android.data.theme.Theme

internal sealed interface ThemeListItem {
    data object Add : ThemeListItem
    data class Header(val isDark: Boolean, val count: Int, val expanded: Boolean) : ThemeListItem
    data class Card(val theme: Theme) : ThemeListItem
}

internal fun buildThemeListItems(
    themes: List<Theme>,
    collapsedGroups: Set<Boolean>
): List<ThemeListItem> = buildList {
    add(ThemeListItem.Add)
    for (isDark in listOf(false, true)) {
        val group = themes.filter { it.isDark == isDark }
        val expanded = isDark !in collapsedGroups
        add(ThemeListItem.Header(isDark, group.size, expanded))
        if (expanded) group.forEach { add(ThemeListItem.Card(it)) }
    }
}
