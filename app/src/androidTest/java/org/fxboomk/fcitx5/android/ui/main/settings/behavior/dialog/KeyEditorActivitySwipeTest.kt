/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.dialog

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.utils.serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class KeyEditorActivitySwipeTest {

    private lateinit var activityRule: ActivityTestRule<KeyEditorActivity>

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val keyTypes = listOf("CapsKey", "LayoutSwitchKey", "SymbolKey", "ReturnKey", "BackspaceKey")

    @Test
    fun legacyLabelsAndEventsMigrateForEverySpecialKeyAndPosition() {
        val previousPosition = ThemeManager.prefs.punctuationPosition.getValue()
        try {
            for (position in listOf(PunctuationPosition.Top, PunctuationPosition.TopRight, PunctuationPosition.Bottom)) {
                instrumentation.runOnMainSync { ThemeManager.prefs.punctuationPosition.setValue(position) }
                val direction = if (position == PunctuationPosition.Bottom) "swipeDown" else "swipeUp"
                val opposite = if (direction == "swipeUp") "swipeDown" else "swipeUp"
                for (type in keyTypes) {
                    val legacyAction = action("legacy")
                    val activity = launchEditor(hashMapOf(
                        "type" to type,
                        "swipeLabel" to "Legacy label",
                        "swipe" to legacyAction,
                        "composeOverride" to hashMapOf(
                            "swipeLabel" to "Compose label",
                            "swipe" to action("compose")
                        )
                    ))
                    instrumentation.runOnMainSync {
                        assertEquals(if (direction == "swipeUp") "Legacy label" else "", label(activity, true).text.toString())
                        assertEquals(if (direction == "swipeDown") "Legacy label" else "", label(activity, false).text.toString())
                        eventButton(activity, true)
                        eventButton(activity, false)
                        label(activity, direction != "swipeUp").setText("Other label")
                        save(activity)
                    }
                    val saved = savedKey()
                    assertEquals("$type / $position", legacyAction, saved[direction])
                    assertFalse(saved.containsKey(opposite))
                    assertEquals("Legacy label", saved["${direction}Label"])
                    assertEquals("Other label", saved["${opposite}Label"])
                    assertNoLegacyFields(saved)
                    val compose = saved["composeOverride"] as Map<*, *>
                    assertEquals(action("compose"), compose[direction])
                    assertEquals("Compose label", compose["${direction}Label"])
                    assertNoLegacyFields(compose)
                    activityRule.finishActivity()
                }
            }
        } finally {
            activityRule.finishActivity()
            instrumentation.runOnMainSync { ThemeManager.prefs.punctuationPosition.setValue(previousPosition) }
        }
    }

    @Test
    fun editingOneDirectionKeepsTheOtherDirectionAndLabelsThroughRebuildAndSave() {
        for (type in keyTypes) {
            val activity = launchEditor(hashMapOf(
                "type" to type,
                "swipeUpLabel" to "Up label",
                "swipeDownLabel" to "Down label",
                "swipeUp" to action("up"),
                "swipeDown" to action("down"),
                "swipeLabel" to "Stale label",
                "swipe" to action("stale")
            ))
            try {
                instrumentation.runOnMainSync {
                    label(activity, true).setText("Edited up")
                    label(activity, false).setText("Edited down")
                }
                returnMacroResult(activity, up = true, text = "changed up")
                instrumentation.runOnMainSync {
                    assertEquals("Edited up", label(activity, true).text.toString())
                    assertEquals("Edited down", label(activity, false).text.toString())
                    assertEquals("text:\"changed up\"", eventPreview(activity, true).text.toString())
                    assertEquals("text:\"down\"", eventPreview(activity, false).text.toString())
                }
                returnMacroResult(activity, up = false, text = null)
                instrumentation.runOnMainSync {
                    assertEquals("Edited up", label(activity, true).text.toString())
                    assertEquals("Edited down", label(activity, false).text.toString())
                    assertEquals("text:\"changed up\"", eventPreview(activity, true).text.toString())
                    assertEquals(activity.getString(R.string.text_keyboard_layout_macro_no_event), eventPreview(activity, false).text.toString())
                    save(activity)
                }
                val saved = savedKey()
                assertEquals(type, saved["type"])
                assertEquals("Edited up", saved["swipeUpLabel"])
                assertEquals("Edited down", saved["swipeDownLabel"])
                assertEquals(action("changed up"), saved["swipeUp"])
                assertFalse("Cleared down event must stay removed", saved.containsKey("swipeDown"))
                assertNoLegacyFields(saved)
            } finally {
                activityRule.finishActivity()
            }
        }
    }

    @Test
    fun clearingMigratedLabelsAndEventsDoesNotRestoreLegacyFields() {
        val activity = launchEditor(hashMapOf(
            "type" to "CapsKey",
            "swipeLabel" to "Legacy label",
            "swipe" to action("legacy")
        ))
        try {
            instrumentation.runOnMainSync {
                label(activity, true).setText("")
                label(activity, false).setText("")
            }
            returnMacroResult(activity, up = true, text = null)
            returnMacroResult(activity, up = false, text = null)
            instrumentation.runOnMainSync { save(activity) }
            val saved = savedKey()
            for (field in listOf("swipeUp", "swipeDown", "swipeUpLabel", "swipeDownLabel")) {
                assertFalse("Cleared field must not reappear: $field", saved.containsKey(field))
            }
            assertNoLegacyFields(saved)
        } finally {
            activityRule.finishActivity()
        }
    }

    private fun launchEditor(keyData: HashMap<String, Any?>): KeyEditorActivity {
        // ActivityTestRule caches its result, so each launch needs a fresh instance.
        activityRule = ActivityTestRule(KeyEditorActivity::class.java, false, false)
        val intent = Intent(instrumentation.targetContext, KeyEditorActivity::class.java).apply {
            putExtra(KeyEditorActivity.EXTRA_KEY_DATA, keyData)
            putExtra(KeyEditorActivity.EXTRA_ROW_INDEX, 0)
            putExtra(KeyEditorActivity.EXTRA_KEY_INDEX, 0)
            putExtra(KeyEditorActivity.EXTRA_LOCK_TYPE_SELECTION, true)
        }
        return activityRule.launchActivity(intent).also { instrumentation.waitForIdleSync() }
    }

    private fun returnMacroResult(activity: KeyEditorActivity, up: Boolean, text: String?) {
        val steps = ArrayList<HashMap<String, String>>()
        if (text != null) steps.add(hashMapOf("type" to "text", "text" to text))
        val result = Intent().putExtra(MacroEditorActivity.EXTRA_MACRO_RESULT, steps)
        val monitor = instrumentation.addMonitor(
            MacroEditorActivity::class.java.name,
            ActivityResult(Activity.RESULT_OK, result),
            true
        )
        try {
            instrumentation.runOnMainSync { assertTrue(eventButton(activity, up).performClick()) }
            instrumentation.waitForIdleSync()
            assertEquals("Swipe event must launch its macro editor", 1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun save(activity: KeyEditorActivity) {
        val toolbar = descendants(activity.window.decorView).filterIsInstance<Toolbar>().single()
        val save = (0 until toolbar.menu.size()).map(toolbar.menu::getItem)
            .single { it.title == activity.getString(R.string.save) }
        assertTrue("Changed swipe fields must enable Save", save.isEnabled)
        assertTrue(activity.onOptionsItemSelected(save))
        assertTrue("Swipe fields must pass save validation", activity.isFinishing)
    }

    private fun savedKey(): HashMap<String, Any?> {
        instrumentation.waitForIdleSync()
        val result = activityRule.activityResult
        assertEquals(Activity.RESULT_OK, result.resultCode)
        return checkNotNull(result.resultData.serializable(KeyEditorActivity.EXTRA_RESULT_KEY_DATA))
    }

    private fun label(activity: KeyEditorActivity, up: Boolean): EditText {
        val titleRes = if (up) R.string.text_keyboard_layout_swipe_up_label else R.string.text_keyboard_layout_swipe_down_label
        val title = descendants(activity.window.decorView).filterIsInstance<TextView>()
            .single { it.text == activity.getString(titleRes) }
        return descendants(title.parent as View).filterIsInstance<EditText>().single()
    }

    private fun eventButton(activity: KeyEditorActivity, up: Boolean): TextView {
        val titleRes = if (up) R.string.text_keyboard_layout_macro_swipe_event_up else R.string.text_keyboard_layout_macro_swipe_event_down
        return descendants(activity.window.decorView).filterIsInstance<TextView>()
            .single { it.text == activity.getString(titleRes) }
    }

    private fun eventPreview(activity: KeyEditorActivity, up: Boolean): TextView {
        val button = eventButton(activity, up)
        val container = button.parent as ViewGroup
        return container.getChildAt(container.indexOfChild(button) + 1) as TextView
    }

    private fun action(text: String): HashMap<String, Any?> =
        hashMapOf("macro" to arrayListOf(hashMapOf("type" to "text", "text" to text)))

    private fun assertNoLegacyFields(keyData: Map<*, *>) {
        assertFalse(keyData.containsKey("swipe"))
        assertFalse(keyData.containsKey("swipeLabel"))
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
