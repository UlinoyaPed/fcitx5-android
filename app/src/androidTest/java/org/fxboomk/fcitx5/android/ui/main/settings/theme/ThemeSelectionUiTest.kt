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
    @SdkSuppress(minSdkVersion = 29)
    fun modeDialogsFilterCardsAndSaveOnlyVisibleSelections() = withActivity { activity ->
        val allThemes = ThemeManager.getAllThemes()
        val initialNames = allThemes.map { it.name }.toSet() + "missing-theme"
        for (isDark in listOf(false, true)) {
            val manager = PreferenceManager(activity).apply { sharedPreferencesName = "theme-selection-ui-test" }
            val preferences = manager.sharedPreferences!!
            preferences.edit().putString("themes", initialNames.joinToString("|")).commit()
            try {
                val preference = ThemeMultiSelectPreference(activity, isDark).apply { key = "themes" }
                manager.createPreferenceScreen(activity).addPreference(preference)
                preference.performClick()
                var root = WindowInspector.getGlobalWindowViews().last { findThemeList(it) != null }
                var list = findThemeList(root)!!
                var adapter = list.adapter as ThemeMultiSelectPreference.MultiSelectThemeAdapter
                val expected = allThemes.filter { it.isDark == isDark }.map { it.name }.toSet()
                assertTrue(expected.isNotEmpty())
                assertEquals(expected.size, adapter.itemCount)
                assertEquals(expected, adapter.getSelectedThemeNames())
                root.findViewById<Button>(android.R.id.button2).performClick()
                assertEquals(initialNames, preferences.getString("themes", "")!!.split("|").toSet())
                preference.performClick()
                root = WindowInspector.getGlobalWindowViews().last { findThemeList(it) != null }
                list = findThemeList(root)!!
                adapter = list.adapter as ThemeMultiSelectPreference.MultiSelectThemeAdapter
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
                assertEquals(expected, preferences.getString("themes", "")!!.split("|").toSet())
            } finally {
                preferences.edit().clear().commit()
            }
        }
    }

    private fun findThemeList(view: View): ResponsiveThemeListView? {
        if (view is ResponsiveThemeListView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findThemeList(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun adapter(collapsed: Set<Boolean> = emptySet()) = object : ThemeListAdapter(collapsed) {
        override fun onAddNewTheme() = Unit
        override fun onSelectTheme(theme: Theme) = Unit
        override fun onEditTheme(theme: Theme.Custom) = Unit
        override fun onEditMonetTheme(theme: Theme.Monet) = Unit
        override fun onExportTheme(theme: Theme.Custom) = Unit
    }
}
