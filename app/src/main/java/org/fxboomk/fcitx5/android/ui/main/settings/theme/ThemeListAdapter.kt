/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fxboomk.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui

abstract class ThemeListAdapter(
    collapsedGroups: Set<Boolean> = emptySet()
) : RecyclerView.Adapter<ThemeListAdapter.ViewHolder>() {
    class ViewHolder(val ui: Ui) : RecyclerView.ViewHolder(ui.root)

    private val entries = mutableListOf<Theme>()
    private var items = buildThemeListItems(entries, collapsedGroups)
    var collapsedGroups: Set<Boolean> = collapsedGroups.toSet()
        private set

    private var activeName: String? = null
    private var lightName: String? = null
    private var darkName: String? = null

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (holder.ui is ThemeThumbnailUi) {
            holder.ui.cleanup()
        }
    }

    fun setThemes(themes: List<Theme>) {
        entries.clear()
        entries.addAll(themes)
        rebuildItems()
    }

    private fun rebuildItems() {
        items = buildThemeListItems(entries, collapsedGroups)
        notifyDataSetChanged()
    }

    private fun toggleGroup(isDark: Boolean) {
        collapsedGroups = if (isDark in collapsedGroups) collapsedGroups - isDark else collapsedGroups + isDark
        rebuildItems()
    }

    fun setSelectedThemes(active: Theme, light: Theme? = null, dark: Theme? = null) {
        val affectedNames = setOf(activeName, lightName, darkName, active.name, light?.name, dark?.name)
        activeName = active.name
        lightName = light?.name
        darkName = dark?.name
        items.forEachIndexed { position, item ->
            if (item is ThemeListItem.Card && item.theme.name in affectedNames) {
                notifyItemChanged(position)
            }
        }
    }

    fun prependTheme(theme: Theme) {
        entries.add(0, theme)
        rebuildItems()
    }

    fun removeTheme(name: String) {
        if (entries.removeAll { it.name == name }) rebuildItems()
    }

    fun replaceTheme(theme: Theme) = replaceTheme(theme.name, theme)

    fun replaceTheme(oldName: String, theme: Theme) {
        val replacedName = if (entries.any { it.name == oldName }) oldName else theme.name
        entries.removeAll { it.name == replacedName }
        entries.add(0, theme)
        if (activeName == replacedName) activeName = theme.name
        if (lightName == replacedName) lightName = theme.name
        if (darkName == replacedName) darkName = theme.name
        rebuildItems()
    }

    fun isFullSpan(position: Int) = items.getOrNull(position)?.let { it !is ThemeListItem.Card } == true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            when (viewType) {
                ADD_THEME -> NewThemeEntryUi(parent.context)
                THEME -> ThemeThumbnailUi(parent.context)
                HEADER -> ThemeGroupHeaderUi(parent.context)
                else -> throw IllegalArgumentException(INVALID_TYPE + viewType)
            }
        ).apply {
            if (viewType != THEME) {
                itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (viewType == ADD_THEME) parent.context.dp(64) else ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val it = getItemViewType(position)) {
            ADD_THEME -> holder.ui.root.setOnClickListener { onAddNewTheme() }
            HEADER -> {
                val header = items[position] as ThemeListItem.Header
                (holder.ui as ThemeGroupHeaderUi).bind(header) { toggleGroup(header.isDark) }
            }
            THEME -> (holder.ui as ThemeThumbnailUi).apply {
                val theme = (items[position] as ThemeListItem.Card).theme
                setTheme(theme)
                setChecked(
                    when (theme.name) {
                        darkName -> ThemeThumbnailUi.State.DarkMode
                        lightName -> ThemeThumbnailUi.State.LightMode
                        activeName -> ThemeThumbnailUi.State.Selected
                        else -> ThemeThumbnailUi.State.Normal
                    }
                )
                root.setOnClickListener {
                    onSelectTheme(theme)
                }
                root.setOnLongClickListener {
                    if (theme is Theme.Custom) {
                        onExportTheme(theme)
                        true
                    } else if (theme is Theme.Monet) {
                        onExportTheme(theme.toCustom())
                        true
                    } else false
                }
                editButton.setOnClickListener {
                    when (theme) {
                        is Theme.Custom -> onEditTheme(theme)
                        is Theme.Monet -> onEditMonetTheme(theme)
                        else -> Unit
                    }
                }
            }
            else -> throw IllegalArgumentException(INVALID_TYPE + it)
        }
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        ThemeListItem.Add -> ADD_THEME
        is ThemeListItem.Header -> HEADER
        is ThemeListItem.Card -> THEME
    }

    abstract fun onAddNewTheme()

    abstract fun onSelectTheme(theme: Theme)

    abstract fun onEditTheme(theme: Theme.Custom)

    abstract fun onEditMonetTheme(theme: Theme.Monet)

    abstract fun onExportTheme(theme: Theme.Custom)

    companion object {
        const val ADD_THEME = 0
        const val THEME = 1
        const val HEADER = 2

        const val INVALID_TYPE = "Invalid ItemView Type: "
    }
}
