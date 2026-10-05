/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutDataManager
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutHeightPercentOverrides
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.LayoutJsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.resources.styledColor
import splitties.dimensions.dp
import java.util.UUID

class TextKeyboardLayoutCustomizeActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun saveStateAndTintFollowChangesInLightMode() = verifySaveState(dark = false)

    @Test
    fun saveStateAndTintFollowChangesInDarkMode() = verifySaveState(dark = true)

    @Test
    fun viewingMissingBaseDoesNotWriteLayoutEntry() {
        val profile = "virtual-view-test-${UUID.randomUUID()}"
        val file = requireNotNull(UserConfigFiles.textKeyboardLayoutJson(profile))
        val originalText = createDefaultProfile(file)
        var activity: TextKeyboardLayoutCustomizeActivity? = null
        try {
            activity = startActivity(profile, "virtual-ime")
            instrumentation.waitForIdleSync()
            assertEquals(originalText, file.readText())
            val reloaded = LayoutDataManager(instrumentation.targetContext).apply { loadFromFile(file) }
            assertTrue("Viewing must not materialize a virtual base", "virtual-ime" !in reloaded.entries)
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            file.delete()
        }
    }

    @Test
    fun changedHeightSaveMaterializesMissingBaseFromDefault() {
        val profile = "virtual-save-test-${UUID.randomUUID()}"
        val file = requireNotNull(UserConfigFiles.textKeyboardLayoutJson(profile))
        createDefaultProfile(file)
        var activity: TextKeyboardLayoutCustomizeActivity? = null
        try {
            val editor = startActivity(profile, "virtual-ime")
            activity = editor
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val slider = descendants(editor.window.decorView).filterIsInstance<SeekBar>().first()
                slider.progress = changedProgress(slider)
                val save = toolbar(editor).menu.getItem(0)
                assertTrue(save.isEnabled)
                assertTrue(toolbar(editor).menu.performIdentifierAction(save.itemId, 0))
            }
            instrumentation.waitForIdleSync()

            val reloaded = LayoutDataManager(editor).apply { loadFromFile(file) }
            val defaultRows = reloaded.entries.getValue(LayoutJsonUtils.DEFAULT_BASE_LAYOUT_KEY)
            val virtualRows = reloaded.entries.getValue("virtual-ime")
            assertEquals(defaultRows, virtualRows)
            assertNotSame(defaultRows, virtualRows)
            assertNotSame(defaultRows.first(), virtualRows.first())
            assertNotSame(defaultRows.first().first(), virtualRows.first().first())
            assertTrue(reloaded.getLayoutHeightPercentOverride("virtual-ime") != null)
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            file.delete()
        }
    }

    @Test
    fun failedHeightSaveDoesNotOpenLayoutEditor() {
        val profile = "virtual-save-failure-test-${UUID.randomUUID()}"
        val file = requireNotNull(UserConfigFiles.textKeyboardLayoutJson(profile))
        createDefaultProfile(file)
        var activity: TextKeyboardLayoutCustomizeActivity? = null
        val monitor = instrumentation.addMonitor(
            TextKeyboardLayoutEditorActivity::class.java.name,
            null,
            false
        )
        try {
            val editor = startActivity(profile, "virtual-ime")
            activity = editor
            instrumentation.waitForIdleSync()
            assertTrue(file.delete())
            assertTrue(file.mkdir())
            instrumentation.runOnMainSync {
                val slider = descendants(editor.window.decorView).filterIsInstance<SeekBar>().first()
                slider.progress = changedProgress(slider)
                descendants(editor.window.decorView).filterIsInstance<Button>()
                    .single { it.text == editor.getString(R.string.text_keyboard_layout_manage_more_customize) }
                    .performClick()
            }
            assertNull(monitor.waitForActivityWithTimeout(500))
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            file.deleteRecursively()
        }
    }

    @Test
    fun inheritedChildHeightSaveOnlyMaterializesChildNotBaseOrSibling() =
        verifyChildHeightIsolation(explicitBase = true, existingChild = false)

    @Test
    fun virtualParentChildHeightSaveOnlyMaterializesChildNotParentOrSibling() =
        verifyChildHeightIsolation(explicitBase = false, existingChild = false)

    @Test
    fun customizedChildInheritsMissingOrientationWithoutChangingBaseOrSibling() =
        verifyChildHeightIsolation(explicitBase = true, existingChild = true)

    @Test
    fun childOnlyHeightInheritsDefaultWithoutMaterializingParent() =
        verifyChildHeightIsolation(explicitBase = false, existingChild = true)

    private fun verifyChildHeightIsolation(explicitBase: Boolean, existingChild: Boolean) {
        val profile = "child-height-test-${UUID.randomUUID()}"
        val file = requireNotNull(UserConfigFiles.textKeyboardLayoutJson(profile))
        val manager = LayoutDataManager(instrumentation.targetContext).apply {
            loadFromFile(null)
            entries.clear()
            entries["default"] = mutableListOf(mutableListOf(mutableMapOf<String, Any?>("main" to "d")))
            entries["ime:sibling"] = mutableListOf(mutableListOf(mutableMapOf<String, Any?>("main" to "s")))
            if (explicitBase) {
                entries["ime"] = mutableListOf(mutableListOf(mutableMapOf<String, Any?>("main" to "b")))
                setLayoutHeightPercentOverride("ime", LayoutHeightPercentOverrides(portrait = 43))
            }
            if (existingChild) {
                entries["ime:child"] = mutableListOf(mutableListOf(mutableMapOf<String, Any?>("main" to "c")))
                setLayoutHeightPercentOverride("ime:child", LayoutHeightPercentOverrides(landscape = 56))
            }
            setLayoutHeightPercentOverride("default", LayoutHeightPercentOverrides(64, 65))
            setLayoutHeightPercentOverride("ime:sibling", LayoutHeightPercentOverrides(71, 72))
            setProfileHeightOverrides(LayoutHeightPercentOverrides(32, 33))
        }
        assertTrue(manager.saveToFile(file))
        val originalText = file.readText()
        var activity: TextKeyboardLayoutCustomizeActivity? = null
        val expectedPortrait = if (explicitBase) 43 else 64
        val expectedLandscape = if (existingChild) 56 else if (explicitBase) 33 else 65
        try {
            val editor = startActivity(profile, "ime", "child")
            activity = editor
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val sliders = descendants(editor.window.decorView).filterIsInstance<SeekBar>().toList()
                assertEquals(listOf(expectedPortrait, expectedLandscape), sliders.map { it.progress + 10 })
                assertSaveState(editor, enabled = false)
                assertEquals("Viewing must not write", originalText, file.readText())
                // Returning a slider to its inherited value must also be a no-op save.
                sliders[0].progress += 1
                sliders[0].progress -= 1
                assertTrue(editor.onOptionsItemSelected(toolbar(editor).menu.getItem(0)))
                assertEquals("Unchanged save must not materialize rows", originalText, file.readText())
                sliders[0].progress += 1
                assertTrue(toolbar(editor).menu.performIdentifierAction(toolbar(editor).menu.getItem(0).itemId, 0))
                assertSaveState(editor, enabled = false)
            }
            instrumentation.waitForIdleSync()
            val reloaded = LayoutDataManager(editor).apply { loadFromFile(file) }
            assertEquals(
                LayoutHeightPercentOverrides(expectedPortrait + 1, expectedLandscape),
                reloaded.getLayoutHeightPercentOverride("ime:child")
            )
            assertEquals(manager.entries.keys + "ime:child", reloaded.entries.keys)
            for (key in listOf("default", "ime", "ime:sibling")) {
                assertEquals("Rows changed for $key", manager.entries[key], reloaded.entries[key])
                assertEquals("Height changed for $key", manager.getLayoutHeightPercentOverride(key),
                    reloaded.getLayoutHeightPercentOverride(key))
            }
            assertEquals(manager.profileHeightOverrides, reloaded.profileHeightOverrides)
            val source = if (existingChild) "ime:child" else if (explicitBase) "ime" else "default"
            assertEquals(manager.entries[source], reloaded.entries["ime:child"])
            if (!existingChild) {
                val inherited = reloaded.entries.getValue(source)
                val child = reloaded.entries.getValue("ime:child")
                assertNotSame(inherited, child)
                assertNotSame(inherited.first(), child.first())
                assertNotSame(inherited.first().first(), child.first().first())
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            file.delete()
        }
    }

    private fun verifySaveState(dark: Boolean) {
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        val profile = "save-state-test-${UUID.randomUUID()}"
        val file = requireNotNull(UserConfigFiles.textKeyboardLayoutJson(profile))
        var activity: TextKeyboardLayoutCustomizeActivity? = null
        try {
            instrumentation.runOnMainSync {
                AppCompatDelegate.setDefaultNightMode(
                    if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
            val intent = Intent(
                instrumentation.targetContext,
                TextKeyboardLayoutCustomizeActivity::class.java
            ).apply {
                putExtra(TextKeyboardLayoutCustomizeActivity.EXTRA_PROFILE, profile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val editor = instrumentation.startActivitySync(intent) as TextKeyboardLayoutCustomizeActivity
            activity = editor
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                    editor.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                )
                assertNotEquals(
                    editor.styledColor(android.R.attr.textColorPrimary),
                    editor.styledColor(android.R.attr.textColorHint)
                )
                val previewTitle = descendants(editor.window.decorView).filterIsInstance<TextView>()
                    .single { it.text == editor.getString(R.string.text_keyboard_layout_customize_preview) }
                val content = previewTitle.parent as ViewGroup
                assertEquals("Preview must be the first section", 0, content.indexOfChild(previewTitle))
                assertTrue("Preview container must follow its title", content.getChildAt(1) is ViewGroup)
                assertEquals("Only the preview belongs in the scrolling area", 2, content.childCount)
                assertHeightControlsStayFixed(editor)
                // Check before touching a slider: the menu is created after onCreate.
                assertSaveState(editor, enabled = false)
                editor.invalidateOptionsMenu()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertSaveState(editor, enabled = false)
                val sliders = descendants(editor.window.decorView).filterIsInstance<SeekBar>().toList()
                assertEquals(2, sliders.size)
                for (slider in sliders) {
                    val initial = slider.progress
                    slider.progress = changedProgress(slider)
                    assertSaveState(editor, enabled = true)
                    slider.progress = initial
                    assertSaveState(editor, enabled = false)
                }
                sliders.first().progress = changedProgress(sliders.first())
                editor.invalidateOptionsMenu()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertSaveState(editor, enabled = true)
                val toolbar = toolbar(editor)
                assertTrue(toolbar.menu.performIdentifierAction(toolbar.menu.getItem(0).itemId, 0))
                assertSaveState(editor, enabled = false)
                val sliders = descendants(editor.window.decorView).filterIsInstance<SeekBar>().toList()
                val persisted = LayoutDataManager(editor).apply { loadFromFile(file) }
                    .getLayoutHeightPercentOverride(LayoutJsonUtils.DEFAULT_BASE_LAYOUT_KEY)
                assertEquals(sliders[0].progress + 10, persisted?.portrait)
                assertEquals(sliders[1].progress + 10, persisted?.landscape)
                val saved = sliders[0].progress
                sliders[0].progress = changedProgress(sliders[0])
                assertSaveState(editor, enabled = true)
                sliders[0].progress = saved
                assertSaveState(editor, enabled = false)
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(originalMode) }
            file.delete()
        }
    }

    private fun changedProgress(slider: SeekBar): Int =
        if (slider.progress < slider.max) slider.progress + 1 else slider.progress - 1

    private fun createDefaultProfile(file: java.io.File): String {
        val manager = LayoutDataManager(instrumentation.targetContext)
        manager.loadFromFile(null)
        assertTrue(manager.saveToFile(file))
        return file.readText()
    }

    private fun startActivity(profile: String, layout: String, subMode: String? = null): TextKeyboardLayoutCustomizeActivity {
        val intent = Intent(
            instrumentation.targetContext,
            TextKeyboardLayoutCustomizeActivity::class.java
        ).apply {
            putExtra(TextKeyboardLayoutCustomizeActivity.EXTRA_PROFILE, profile)
            putExtra(TextKeyboardLayoutCustomizeActivity.EXTRA_LAYOUT, layout)
            subMode?.let { putExtra(TextKeyboardLayoutCustomizeActivity.EXTRA_SUBMODE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return instrumentation.startActivitySync(intent) as TextKeyboardLayoutCustomizeActivity
    }

    private fun assertHeightControlsStayFixed(activity: TextKeyboardLayoutCustomizeActivity) {
        val root = toolbar(activity).parent as ViewGroup
        val scroll = descendants(root).filterIsInstance<NestedScrollView>().single()
        val sliders = descendants(root).filterIsInstance<SeekBar>().toList()
        assertEquals(2, sliders.size)
        assertTrue("Sliders must not scroll with the preview",
            descendants(scroll).none { it is SeekBar })
        val controls = sliders.first().parent.parent as ViewGroup
        assertEquals(root, controls.parent)
        assertEquals("Height controls must be the bottom section",
            root.childCount - 1, root.indexOfChild(controls))
        assertEquals(activity.getString(R.string.keyboard_height),
            (controls.getChildAt(0) as TextView).text.toString())

        val originalWidth = root.width
        val originalHeight = root.height
        val initialProgress = sliders.map { it.progress }
        fun layout(width: Int, height: Int) {
            root.forceLayout()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(root.left, root.top, root.left + width, root.top + height)
        }
        fun sliderBounds() = sliders.map {
            Rect(0, 0, it.width, it.height).apply {
                root.offsetDescendantRectToMyCoords(it, this)
            }
        }
        try {
            for ((width, height) in listOf(360 to 640, 640 to 360)) {
                sliders.forEach { it.progress = 0 }
                layout(activity.dp(width), activity.dp(height))
                val bounds = sliderBounds()
                val controlsTop = controls.top
                assertTrue("Preview must retain visible space", scroll.height > 0)
                sliders.forEach { it.progress = it.max }
                layout(activity.dp(width), activity.dp(height))
                assertEquals("Preview growth must not move sliders", bounds, sliderBounds())
                assertEquals(controlsTop, controls.top)
                assertEquals(root.height - root.paddingBottom, controls.bottom)
                assertTrue("Preview must not overlap controls", scroll.bottom <= controls.top)
                bounds.forEach {
                    assertTrue("Sliders must stay visible", it.top >= controls.top && it.bottom <= controls.bottom)
                }
            }
        } finally {
            sliders.forEachIndexed { index, slider -> slider.progress = initialProgress[index] }
            layout(originalWidth, originalHeight)
        }
    }

    private fun toolbar(activity: TextKeyboardLayoutCustomizeActivity): Toolbar =
        descendants(activity.window.decorView).filterIsInstance<Toolbar>().single()

    private fun assertSaveState(activity: TextKeyboardLayoutCustomizeActivity, enabled: Boolean) {
        val save = toolbar(activity).menu.getItem(0)
        assertEquals("Save enabled state", enabled, save.isEnabled)
        val expected = requireNotNull(
            AppCompatResources.getDrawable(activity, R.drawable.ic_baseline_save_24)
        ).mutate().apply {
            setTint(activity.styledColor(
                if (enabled) android.R.attr.textColorPrimary else android.R.attr.textColorHint
            ))
        }
        val actualBitmap = render(requireNotNull(save.icon))
        val expectedBitmap = render(expected)
        try {
            assertTrue("Save icon must use the themed state color", actualBitmap.sameAs(expectedBitmap))
        } finally {
            actualBitmap.recycle()
            expectedBitmap.recycle()
        }
    }

    private fun render(drawable: Drawable): Bitmap {
        val originalBounds = android.graphics.Rect(drawable.bounds)
        return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, it.width, it.height)
            drawable.draw(Canvas(it))
            drawable.bounds = originalBounds
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
