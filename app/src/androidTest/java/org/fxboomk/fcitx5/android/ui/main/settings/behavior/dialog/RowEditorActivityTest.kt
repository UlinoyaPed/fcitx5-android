/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.dialog

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Spinner
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.KeyboardRowStyleUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.HashMap

class RowEditorActivityTest {

    @Test
    fun nonblankInvalidWidthCannotBeSavedButBlankWidthCan() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, RowEditorActivity::class.java).apply {
            putExtra(RowEditorActivity.EXTRA_ROW_INDEX, 0)
            putExtra(RowEditorActivity.EXTRA_ROW_META, HashMap(KeyboardRowStyleUtils.buildMeta(
                KeyboardRowStyleUtils.RowStyle(keyWidthMultiplier = 1f)
            )))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as RowEditorActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView).toList()
                val width = views.filterIsInstance<EditText>().last()
                val save = views.filterIsInstance<Toolbar>().single().menu.getItem(0)
                for (invalid in listOf("abc", "1..2", "0", "-1", "NaN", "Infinity")) {
                    width.setText(invalid)
                    assertFalse("Invalid width must disable Save: $invalid", save.isEnabled)
                }
                for (valid in listOf("", "1.5")) {
                    width.setText(valid)
                    assertTrue("Blank or valid changed width can be saved: $valid", save.isEnabled)
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun followingThemeAfterCustomBackgroundEnablesSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (backgroundStyle in KeyboardRowStyleUtils.BackgroundStyle.entries) {
            for (reference in listOf(null, "theme:accentKeyBackgroundColor", "system_accent1_500")) {
                val style = KeyboardRowStyleUtils.RowStyle(
                    backgroundStyle = backgroundStyle,
                    backgroundColor = if (reference == null) 0xff224466.toInt() else null,
                    backgroundColorMonet = reference
                )
                val intent = Intent(context, RowEditorActivity::class.java).apply {
                    putExtra(RowEditorActivity.EXTRA_ROW_INDEX, 0)
                    putExtra(RowEditorActivity.EXTRA_ROW_META, HashMap(KeyboardRowStyleUtils.buildMeta(style)))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val activity = instrumentation.startActivitySync(intent) as RowEditorActivity
                try {
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        val views = descendants(activity.window.decorView).toList()
                        val toolbar = views.filterIsInstance<Toolbar>().single()
                        assertFalse(toolbar.menu.getItem(0).isEnabled)
                        val colorTitle = views.filterIsInstance<TextView>().single {
                            it.text == activity.getString(R.string.text_keyboard_layout_row_background_color)
                        }
                        assertTrue((colorTitle.parent as View).performClick())
                    }
                    instrumentation.waitForIdleSync()
                    val automation = instrumentation.uiAutomation
                    automation.waitForIdle(100, 5000)
                    val followTheme = context.getString(R.string.text_keyboard_layout_key_color_mode_theme)
                    var option = automation.rootInActiveWindow
                        .findAccessibilityNodeInfosByText(followTheme).single { it.text == followTheme }
                    while (!option.isClickable) {
                        option = checkNotNull(option.parent)
                    }
                    assertTrue(option.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        val views = descendants(activity.window.decorView).toList()
                        assertEquals(0, views.filterIsInstance<Spinner>().last().selectedItemPosition)
                        val save = views.filterIsInstance<Toolbar>().single().menu.getItem(0)
                        assertTrue("Resetting $backgroundStyle / $reference must enable Save", save.isEnabled)
                        assertTrue(views.filterIsInstance<TextView>().any {
                            it.text == activity.getString(R.string.text_keyboard_layout_row_background_not_set)
                        })
                        assertTrue(activity.onOptionsItemSelected(save))
                        assertTrue("The reset must pass save validation", activity.isFinishing)
                    }
                } finally {
                    instrumentation.runOnMainSync { activity.finish() }
                }
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
