/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.dialog

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.Toolbar
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class LayoutFileProfileInputActivityTest {
    @Test
    fun previewStaysAboveFixedSettingsInBothThemesAndEntryModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        try {
            for (mode in listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                for (action in listOf(
                    LayoutFileProfileInputActivity.ACTION_RENAME,
                    LayoutFileProfileInputActivity.ACTION_CREATE
                )) {
                    val intent = Intent(
                        instrumentation.targetContext,
                        LayoutFileProfileInputActivity::class.java
                    ).apply {
                        putExtra(LayoutFileProfileInputActivity.EXTRA_ACTION, action)
                        putExtra(LayoutFileProfileInputActivity.EXTRA_INITIAL_PROFILE,
                            UserConfigFiles.DEFAULT_TEXT_KEYBOARD_LAYOUT_PROFILE)
                        putExtra(LayoutFileProfileInputActivity.EXTRA_SHOW_COPY_SWITCH,
                            action == LayoutFileProfileInputActivity.ACTION_CREATE)
                        putExtra(LayoutFileProfileInputActivity.EXTRA_INITIAL_HEIGHT_PERCENT_PORTRAIT, 40)
                        putExtra(LayoutFileProfileInputActivity.EXTRA_INITIAL_HEIGHT_PERCENT_LANDSCAPE, 25)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val activity = instrumentation.startActivitySync(intent) as LayoutFileProfileInputActivity
                    try {
                        instrumentation.waitForIdleSync()
                        instrumentation.runOnMainSync { verifyLayout(activity) }
                    } finally {
                        instrumentation.runOnMainSync { activity.finish() }
                        instrumentation.waitForIdleSync()
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(originalMode) }
        }
    }

    private fun verifyLayout(activity: LayoutFileProfileInputActivity) {
        val toolbar = descendants(activity.window.decorView).filterIsInstance<Toolbar>().single()
        val root = toolbar.parent as ViewGroup
        val previewScroll = root.getChildAt(1) as ScrollView
        val settingsScroll = root.getChildAt(2) as ScrollView
        val preview = previewScroll.getChildAt(0) as ViewGroup
        assertEquals(activity.getString(R.string.text_keyboard_layout_customize_preview),
            (preview.getChildAt(0) as TextView).text.toString())
        assertEquals(2, preview.childCount)
        assertTrue(descendants(preview).none { it is SeekBar || it is AppCompatEditText })
        assertEquals(1, descendants(settingsScroll).filterIsInstance<AppCompatEditText>().count())
        val sliders = descendants(settingsScroll).filterIsInstance<SeekBar>().toList()
        assertEquals(2, sliders.size)
        val save = toolbar.menu.getItem(0)
        assertFalse(save.isEnabled)

        val initialProgress = sliders.map { it.progress }
        val originalWidth = root.width
        val originalHeight = root.height
        fun layout(width: Int, height: Int) {
            root.forceLayout()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(root.left, root.top, root.left + width, root.top + height)
        }
        fun bounds() = sliders.map {
            Rect(0, 0, it.width, it.height).apply {
                root.offsetDescendantRectToMyCoords(it, this)
            }
        }
        try {
            for ((width, height) in listOf(360 to 640, 640 to 360)) {
                sliders.forEach { it.progress = 0 }
                layout(activity.dp(width), activity.dp(height))
                val before = bounds()
                val settingsTop = settingsScroll.top
                sliders.forEach { it.progress = it.max }
                layout(activity.dp(width), activity.dp(height))
                assertTrue(save.isEnabled)
                assertEquals("Preview growth must not move settings", settingsTop, settingsScroll.top)
                assertEquals("Slider positions must stay fixed", before, bounds())
                assertEquals(root.height - root.paddingBottom, settingsScroll.bottom)
                assertTrue(previewScroll.bottom <= settingsScroll.top)
                assertTrue("Preview must retain visible space", previewScroll.height > 0)
            }
        } finally {
            sliders.forEachIndexed { index, slider -> slider.progress = initialProgress[index] }
            layout(originalWidth, originalHeight)
        }
        assertFalse("Restoring heights must disable Save", save.isEnabled)
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
