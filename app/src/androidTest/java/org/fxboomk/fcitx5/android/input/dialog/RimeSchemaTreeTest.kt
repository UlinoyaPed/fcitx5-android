/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.dialog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.core.Action
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.input.keyboard.LangSwitchLongPressBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class RimeSchemaTreeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_FcitxAppTheme)
    private val rime = engine("rime", "中州韵", "rime")
    private val enabled = arrayOf(engine("keyboard-us", "English", "androidkeyboard"), rime, engine("pinyin", "拼音", "pinyin"))
    private val schemas = arrayOf(schema(10, "朙月拼音"), schema(20, "小鹤双拼"))

    @Test
    fun schemasAreIndentedBelowTheirParentWithTreeBranches() = instrumentation.runOnMainSync {
        val entries = InputMethodData.internalEntries(enabled, schemas, LangSwitchLongPressBehavior.BuiltInAndRime)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        var clicked: InputMethodData? = null
        val adapter = InputMethodListAdapter(entries, 0) { clicked = it }
        val holders = entries.indices.map { index ->
            adapter.onCreateViewHolder(container, 0).also {
                adapter.onBindViewHolder(it, index)
                container.addView(it.itemView)
            }
        }
        container.measure(View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        container.layout(0, 0, container.measuredWidth, container.measuredHeight)
        val parent = holders[1].ui
        val firstChild = holders[2].ui
        val lastChild = holders[3].ui
        assertTrue(parent.title.typeface.isBold)
        assertEquals(View.GONE, parent.treeBranch.visibility)
        assertEquals(View.VISIBLE, firstChild.treeBranch.visibility)
        assertFalse(firstChild.treeBranch.isLastChild)
        assertTrue(lastChild.treeBranch.isLastChild)
        assertEquals(View.GONE, firstChild.subtitle.visibility)
        assertTrue(titleLeft(firstChild) - titleLeft(parent) >= dp(28))
        assertTrue(firstChild.root.top >= parent.root.bottom)
        lastChild.root.performClick()
        assertEquals(20, clicked?.rimeSchemaActionId)

        val bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
        container.draw(Canvas(bitmap))
        File(instrumentation.targetContext.cacheDir, "rime-schema-tree.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun recyclingResetsHeadersIndentationAndClickTargets() = instrumentation.runOnMainSync {
        val schemaRows = InputMethodData.internalEntries(enabled, schemas, LangSwitchLongPressBehavior.RimeOnly)
        val rows = schemaRows + InputMethodData("keyboard-us", "English", false)
        val parent = FrameLayout(context)
        var clicked: InputMethodData? = null
        val adapter = InputMethodListAdapter(rows, -1) { clicked = it }
        val holder = adapter.onCreateViewHolder(parent, 0)
        val normalPaddingTop = holder.ui.root.paddingTop

        adapter.onBindViewHolder(holder, 0)
        assertTrue(holder.ui.title.typeface.isBold)
        assertFalse(holder.ui.root.isClickable)
        assertFalse(holder.ui.root.performClick())
        assertEquals(null, clicked)

        adapter.onBindViewHolder(holder, 1)
        assertFalse(holder.ui.title.typeface.isBold)
        assertEquals(View.VISIBLE, holder.ui.treeBranch.visibility)
        assertTrue(holder.ui.root.performClick())
        assertEquals(10, clicked?.rimeSchemaActionId)

        adapter.onBindViewHolder(holder, rows.lastIndex)
        assertEquals(View.GONE, holder.ui.treeBranch.visibility)
        assertEquals(View.VISIBLE, holder.ui.subtitle.visibility)
        assertEquals(normalPaddingTop, holder.ui.root.paddingTop)
        assertFalse(holder.ui.title.typeface.isBold)
        assertTrue(holder.ui.root.performClick())
        assertEquals("keyboard-us", clicked?.uniqueName)
    }

    private fun titleLeft(ui: InputMethodEntryUi) = (ui.title.parent as View).left + ui.title.left
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private fun engine(id: String, name: String, addon: String) =
        InputMethodEntry(id, name, "", "", "", "zh", addon, false)
    private fun schema(id: Int, label: String) = Action(id, false, false, false, "", "", label, "", null)
}
