/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.fxboomk.fcitx5.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class MappingEditorFloatingActionButtonTest {

    @Test
    fun swipeSymbolEditorHasAccessibleFloatingAddActionAndListClearance() {
        verifyAddAction(SwipeSymbolMappingEditorActivity::class.java, R.string.swipe_symbol_add_mapping)
    }

    @Test
    fun popupEditorHasAccessibleFloatingAddActionAndListClearance() {
        verifyAddAction(PopupEditorActivity::class.java, R.string.popup_editor_add_mapping)
    }

    private fun verifyAddAction(activityClass: Class<out Activity>, labelRes: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val views = descendants(content).toList()
                val fab = views.filterIsInstance<FloatingActionButton>().single()
                val recyclerView = views.filterIsInstance<RecyclerView>().single()
                val params = fab.layoutParams as FrameLayout.LayoutParams
                val margin = activity.dp(16)
                val label = activity.getString(labelRes)

                assertEquals(Gravity.BOTTOM or Gravity.END, params.gravity)
                assertEquals(margin, params.bottomMargin)
                assertEquals(margin, params.leftMargin)
                assertEquals(margin, params.rightMargin)
                assertEquals(label, fab.contentDescription.toString())
                assertTrue(fab.isShown)
                assertTrue(fab.hasOnClickListeners())
                assertNotNull(fab.drawable)
                assertFalse(views.filterIsInstance<TextView>().any { it.text.toString() == label })

                assertTrue("The FAB must be laid out", fab.height > 0)
                assertEquals(fab.height + margin, recyclerView.paddingBottom)
                assertFalse(recyclerView.clipToPadding)
                val listLocation = IntArray(2)
                val fabLocation = IntArray(2)
                recyclerView.getLocationInWindow(listLocation)
                fab.getLocationInWindow(fabLocation)
                assertTrue(
                    "The last row must be able to scroll above the floating add action",
                    listLocation[1] + recyclerView.height - recyclerView.paddingBottom <= fabLocation[1],
                )
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                yieldAll(descendants(view.getChildAt(index)))
            }
        }
    }
}
