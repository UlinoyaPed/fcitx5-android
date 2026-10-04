/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import splitties.resources.styledColor

class TextKeyboardLayoutProfileManagerActivityTest {
    @Test
    fun deleteButtonsUseDestructiveColorInBothThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        try {
            for (mode in listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                val intent = Intent(
                    instrumentation.targetContext,
                    TextKeyboardLayoutProfileManagerActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val activity = instrumentation.startActivitySync(intent) as TextKeyboardLayoutProfileManagerActivity
                try {
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        val buttons = descendants(activity.window.decorView).filterIsInstance<ImageButton>().toList()
                        val deleteProfile = buttons.single {
                            it.contentDescription == activity.getString(R.string.text_keyboard_layout_manage_delete_profile)
                        }
                        val tint = requireNotNull(deleteProfile.imageTintList)
                        val destructive = activity.styledColor(androidx.appcompat.R.attr.colorError)
                        assertEquals(destructive, tint.getColorForState(intArrayOf(android.R.attr.state_enabled), 0))
                        assertEquals(
                            activity.styledColor(android.R.attr.textColorHint),
                            tint.getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
                        )
                        buttons.filter {
                            it.contentDescription == activity.getString(R.string.text_keyboard_layout_manage_delete)
                        }.forEach {
                            assertEquals(destructive, requireNotNull(it.imageTintList).defaultColor)
                        }
                    }
                } finally {
                    instrumentation.runOnMainSync { activity.finish() }
                    instrumentation.waitForIdleSync()
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(originalMode) }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
