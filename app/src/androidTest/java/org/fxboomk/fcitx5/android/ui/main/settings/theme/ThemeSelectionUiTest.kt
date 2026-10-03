/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.Theme
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.ui.common.ThemeMultiSelectPreference
import org.fxboomk.fcitx5.android.ui.main.MainActivity
import org.junit.Assert.*
import org.junit.Test
import splitties.dimensions.dp

class ThemeSelectionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun withActivity(block: (MainActivity) -> Unit) {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        try {
            instrumentation.runOnMainSync { block(activity) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun collapsingAndRefreshingPreservesSelectionAndRestoresGroups() = withActivity { activity ->
        val light = ThemePreset.MaterialLight
        val dark = ThemePreset.MaterialDark
        val adapter = adapter().apply {
            setThemes(listOf(dark, light))
            setSelectedThemes(light)
        }
        val parent = ResponsiveThemeListView(activity)
        val header = adapter.createViewHolder(parent, ThemeListAdapter.HEADER)
        adapter.bindViewHolder(header, 1)
        header.itemView.performClick()
        assertEquals(setOf(false), adapter.collapsedGroups)
        assertEquals(4, adapter.itemCount)
        adapter.setThemes(listOf(light, dark))
        assertEquals(setOf(false), adapter.collapsedGroups)
        adapter.bindViewHolder(header, 1)
        header.itemView.performClick()
        val card = adapter.createViewHolder(parent, ThemeListAdapter.THEME)
        try {
            adapter.bindViewHolder(card, 2)
            assertEquals(light.name, (card.ui as ThemeThumbnailUi).themeNameText.text.toString())
            assertEquals(View.VISIBLE, card.ui.checkMark.visibility)
        } finally {
            adapter.onViewRecycled(card)
        }
        adapter.bindViewHolder(header, 3)
        header.itemView.performClick()
        val restored = adapter(adapter.collapsedGroups).apply { setThemes(listOf(light, dark)) }
        assertEquals(setOf(true), restored.collapsedGroups)
        assertEquals(4, restored.itemCount)
    }

    @Test
    fun headersSpanTheGridAfterResizeInBothDirections() = withActivity { activity ->
        val adapter = adapter().apply { setThemes(listOf(ThemePreset.MaterialLight, ThemePreset.MaterialDark)) }
        val list = ResponsiveThemeListView(activity).apply { this.adapter = adapter }
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            list.layoutDirection = direction
            for (widthDp in listOf(360, 720)) {
                val width = activity.dp(widthDp)
                val height = activity.dp(600)
                list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                list.layout(0, 0, width, height)
                val grid = list.layoutManager as GridLayoutManager
                assertTrue(grid.spanCount >= 2)
                for (position in listOf(0, 1, 3)) assertEquals(grid.spanCount, grid.spanSizeLookup.getSpanSize(position))
                for (position in listOf(2, 4)) assertEquals(1, grid.spanSizeLookup.getSpanSize(position))
                for (i in 0 until list.childCount) {
                    val child = list.getChildAt(i)
                    assertTrue("left bound", child.left >= 0)
                    assertTrue("right bound", child.right <= width)
                }
            }
        }
        list.adapter = null
    }

    @Test
    fun poolBadgeClickTogglesPoolWithoutSelectingTheme() = withActivity { activity ->
        val theme = ThemePreset.MaterialLight
        var selectedTheme: Theme? = null
        var toggledTheme: Theme? = null
        val adapter = adapter(
            selectThemeCallback = { selectedTheme = it },
            toggleThemePoolCallback = { toggledTheme = it }
        ).apply { setThemes(listOf(theme)) }
        val parent = ResponsiveThemeListView(activity)
        val card = adapter.createViewHolder(parent, ThemeListAdapter.THEME)
        try {
            val ui = card.ui as ThemeThumbnailUi
            assertEquals(View.GONE, ui.poolBadge.visibility)
            val position = (0 until adapter.itemCount).first {
                adapter.getItemViewType(it) == ThemeListAdapter.THEME
            }
            adapter.bindViewHolder(card, position)
            val inPool = ThemeManager.isThemeInAnyPool(theme.name)
            assertEquals(View.VISIBLE, ui.poolBadge.visibility)
            assertEquals(
                activity.getString(
                    if (inPool) R.string.remove_from_theme_pool else R.string.add_to_theme_pool
                ),
                ui.poolBadge.contentDescription
            )
            ui.poolBadge.performClick()
            assertSame(theme, toggledTheme)
            assertNull(selectedTheme)
        } finally {
            adapter.onViewRecycled(card)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun modeDialogsFilterCardsAndSaveOnlyVisibleSelections() {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        val preferences = activity.getSharedPreferences("theme-selection-ui-test", 0)
        try {
            for (isDark in listOf(false, true)) {
                lateinit var preference: ThemeMultiSelectPreference
                lateinit var initialNames: Set<String>
                lateinit var expected: Set<String>
                instrumentation.runOnMainSync {
                    val allThemes = ThemeManager.getAllThemes()
                    initialNames = allThemes.map { it.name }.toSet() + "missing-theme"
                    expected = allThemes.filter { it.isDark == isDark }.map { it.name }.toSet()
                    val manager = PreferenceManager(activity).apply {
                        sharedPreferencesName = "theme-selection-ui-test"
                    }
                    preferences.edit().putString("themes", initialNames.joinToString("|")).commit()
                    preference = ThemeMultiSelectPreference(activity, isDark).apply { key = "themes" }
                    manager.createPreferenceScreen(activity).addPreference(preference)
                    preference.performClick()
                    val root = WindowInspector.getGlobalWindowViews().last { findThemeList(it) != null }
                    val adapter = findThemeList(root)!!.adapter as ThemeMultiSelectPreference.MultiSelectThemeAdapter
                    assertTrue(expected.isNotEmpty())
                    assertEquals(expected.size, adapter.itemCount)
                    assertEquals(expected, adapter.getSelectedThemeNames())
                    root.findViewById<Button>(android.R.id.button2).performClick()
                }
                // AlertDialog posts its button callback and dismissal to the main queue.
                // Drain it outside the main thread before inspecting persisted values.
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(initialNames, preferences.getString("themes", "")!!.split("|").toSet())
                    preference.performClick()
                    val root = WindowInspector.getGlobalWindowViews().last { findThemeList(it) != null }
                    val list = findThemeList(root)!!
                    val adapter = list.adapter as ThemeMultiSelectPreference.MultiSelectThemeAdapter
                    val card = adapter.createViewHolder(list, adapter.getItemViewType(0))
                    try {
                        adapter.bindViewHolder(card, 0)
                        card.itemView.performClick()
                        assertEquals(expected.size - 1, adapter.getSelectedThemeNames().size)
                        card.itemView.performClick()
                    } finally {
                        adapter.onViewRecycled(card)
                    }
                    root.findViewById<Button>(android.R.id.button1).performClick()
                }
                instrumentation.waitForIdleSync()
                assertEquals(expected, preferences.getString("themes", "")!!.split("|").toSet())
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            preferences.edit().clear().commit()
        }
    }

    private fun findThemeList(view: View): ResponsiveThemeListView? {
        if (view is ResponsiveThemeListView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findThemeList(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun adapter(
        collapsed: Set<Boolean> = emptySet(),
        selectThemeCallback: (Theme) -> Unit = {},
        toggleThemePoolCallback: (Theme) -> Unit = {}
    ) = object : ThemeListAdapter(collapsed) {
        override fun onAddNewTheme() = Unit
        override fun onSelectTheme(theme: Theme) = selectThemeCallback(theme)
        override fun onEditTheme(theme: Theme.Custom) = Unit
        override fun onEditMonetTheme(theme: Theme.Monet) = Unit
        override fun onToggleThemePool(theme: Theme) = toggleThemePoolCallback(theme)
        override fun onExportTheme(theme: Theme.Custom) = Unit
    }
}
