/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutDataManager
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutHeightPercentOverrides
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class TextKeyboardLayoutEditorActivityTest {
    @Test fun missingBaseIsVisibleAndBrowsingDoesNotMaterializeIt() = withEditor { activity, file ->
        set(activity, "allImesFromJson", arrayOf(InputMethodEntry("test-ime", "Test IME", "", "", "", "zh", "test-ime", false)))
        assertTrue((invoke(activity, "visibleBaseLayoutKeys") as List<*>).contains("test-ime"))
        selectDraft(activity, "test-ime", null)
        repeat(2) { invoke(activity, "updateSaveButtonState") }
        assertFalse(invoke(activity, "hasChanges") as Boolean)
        assertFalse(manager(activity).entries.containsKey("test-ime"))
        assertFalse(LayoutDataManager(activity).apply { loadFromFile(file) }.entries.containsKey("test-ime"))
    }

    @Test fun dropdownHidesDisabledPresetsAndKeepsCustomizedLayoutsWithoutWriting() = withEditor { activity, file ->
        val data = manager(activity)
        val originalFile = file.readText()
        for (base in listOf("pinyin", "wubi", "custom-keys", "custom-height")) {
            data.entries[base] = data.copyLayout(data.entries.getValue("default"))
        }
        data.entries.getValue("custom-keys")[0][0]["main"] = "changed"
        data.setLayoutHeightPercentOverride("custom-height",
            LayoutHeightPercentOverrides(43, 29))
        data.entries["child-only:child"] = data.copyLayout(data.entries.getValue("default"))
        val originalData = data.exportCurrentJsonString()
        val english = InputMethodEntry("keyboard-us", "English", "", "", "", "en", "androidkeyboard", false)
        set(activity, "allImesFromJson", arrayOf(english))
        val visible = invoke(activity, "visibleBaseLayoutKeys") as List<*>
        assertEquals(listOf("default", "custom-keys", "custom-height", "child-only"), visible)
        assertTrue("pinyin" in data.baseLayoutNames()) // Source lookup still sees stored, hidden groups.
        invoke(activity, "buildSpinner")
        val spinner = get(activity, "layoutSpinner") as android.widget.Spinner
        val items = (0 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() }
        assertEquals(listOf("English", "custom-keys", "custom-height", "child-only"), items)
        assertEquals(originalData, data.exportCurrentJsonString())
        assertEquals(originalFile, file.readText())
    }

    @Test fun menuRefreshDoesNotLoseLaterBaseOrSubmodeEdits() = withEditor { activity, file ->
        // Exercise both virtual bases and inherited non-Rime submodes through the same draft lifecycle.
        for ((base, submode) in listOf(
            "test-ime" to null,
            "default" to "test-mode",
            "child-only-ime" to "child"
        )) {
            val key = if (submode == null) base else "$base:$submode"
            val originalBase = manager(activity).copyLayout(manager(activity).entries.getValue("default"))
            selectDraft(activity, base, submode)
            repeat(2) { invoke(activity, "updateSaveButtonState") }
            assertFalse("Menu preparation must not make the file dirty", invoke(activity, "hasChanges") as Boolean)
            assertEquals(key, get(activity, "bufferedLayoutKey"))
            rows(activity)[0][0]["main"] = "x"
            assertTrue("A later edit must still register the draft", invoke(activity, "hasChanges") as Boolean)
            assertEquals(originalBase, manager(activity).entries.getValue("default"))
            assertEquals("x", manager(activity).entries.getValue(key)[0][0]["main"])
            assertTrue(invoke(activity, "saveLayout") as Boolean)
            val reloaded = LayoutDataManager(activity).apply { loadFromFile(file) }
            assertEquals("x", reloaded.entries.getValue(key)[0][0]["main"])
            if (base == "child-only-ime") assertFalse(reloaded.entries.containsKey(base))
        }
    }

    @Test fun invalidInheritedRowsRecoverWithoutMaterializingTheAbandonedDraft() = withEditor { activity, _ ->
        val data = manager(activity)
        data.entries["healthy"] = data.copyLayout(data.entries.getValue("default"))
        data.entries["default"] = mutableListOf()
        set(activity, "allImesFromJson", arrayOf(InputMethodEntry("healthy", "Healthy", "", "", "", "en", "test", false)))
        selectDraft(activity, "missing-base", null)
        assertEquals("healthy", get(activity, "currentLayout"))
        assertNull(get(activity, "bufferedLayoutKey"))
        assertFalse(data.entries.containsKey("missing-base"))
    }

    @Test fun emptyChildRecoversToBaseWithoutReselectingTheEmptyChild() = withEditor { activity, _ ->
        val data = manager(activity)
        data.entries["healthy"] = data.copyLayout(data.entries.getValue("default"))
        data.entries["default"] = mutableListOf()
        data.entries["healthy:bad"] = mutableListOf()
        set(activity, "targetForceBase", false)
        selectDraft(activity, "healthy", "bad")
        assertEquals("healthy", get(activity, "currentLayout"))
        assertNull(get(activity, "previewSubModeLabel"))
        assertTrue(data.entries.getValue("healthy:bad").isEmpty())
    }

    @Test fun profileReloadDiscardsDraftWithoutLeakingItIntoNextProfile() = withEditor { activity, _ ->
        val secondProfile = "layout-editor-test-${UUID.randomUUID()}"
        val secondFile = requireNotNull(UserConfigFiles.textKeyboardLayoutJson(secondProfile))
        try {
            secondFile.writeText(manager(activity).exportCurrentJsonString())
            selectDraft(activity, "default", "discarded-mode")
            rows(activity)[0][0]["main"] = "discard-me"
            val switch = activity.javaClass.getDeclaredMethod("switchLayoutProfile", String::class.java)
            switch.isAccessible = true
            switch.invoke(activity, secondProfile)
            assertFalse(manager(activity).entries.containsKey("default:discarded-mode"))
            assertFalse(invoke(activity, "hasChanges") as Boolean)
        } finally {
            removeTestFile(secondFile)
        }
    }

    private fun selectDraft(activity: TextKeyboardLayoutEditorActivity, base: String, submode: String?) {
        set(activity, "currentLayout", base)
        set(activity, "previewSubModeLabel", submode)
        invoke(activity, "buildRows")
        assertTrue(rows(activity).isNotEmpty())
    }

    private fun withEditor(block: (TextKeyboardLayoutEditorActivity, File) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val profile = "layout-editor-test-${UUID.randomUUID()}"
        val file = requireNotNull(UserConfigFiles.textKeyboardLayoutJson(profile))
        val data = LayoutDataManager(instrumentation.targetContext).apply { loadFromFile(null) }
        file.parentFile?.mkdirs()
        file.writeText(data.exportCurrentJsonString())
        var activity: TextKeyboardLayoutEditorActivity? = null
        try {
            activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
                TextKeyboardLayoutEditorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TextKeyboardLayoutEditorActivity.EXTRA_TARGET_PROFILE, profile)
                putExtra(TextKeyboardLayoutEditorActivity.EXTRA_TARGET_LAYOUT, "default")
            }) as TextKeyboardLayoutEditorActivity
            instrumentation.waitForIdleSync()
            val editor = activity
            instrumentation.runOnMainSync { block(editor, file) }
        } finally {
            activity?.let { editor -> instrumentation.runOnMainSync { editor.finish() } }
            instrumentation.waitForIdleSync()
            removeTestFile(file)
        }
    }

    private fun removeTestFile(file: File) {
        file.delete()
        file.parentFile?.listFiles { candidate -> candidate.name.startsWith("${file.nameWithoutExtension}_backup_") }
            ?.forEach { it.delete() }
    }

    private fun manager(activity: TextKeyboardLayoutEditorActivity) = get(activity, "dataManager") as LayoutDataManager

    @Suppress("UNCHECKED_CAST")
    private fun rows(activity: TextKeyboardLayoutEditorActivity) =
        get(activity, "currentRowsRef") as MutableList<MutableList<MutableMap<String, Any?>>>

    private fun get(activity: TextKeyboardLayoutEditorActivity, name: String): Any? =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(activity: TextKeyboardLayoutEditorActivity, name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    private fun invoke(activity: TextKeyboardLayoutEditorActivity, name: String): Any? =
        activity.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
}
