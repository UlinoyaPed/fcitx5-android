package org.fxboomk.fcitx5.android.ui.main.settings

import android.content.Context
import org.fxboomk.fcitx5.android.input.candidates.floating.FloatingCandidatesMode
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.input.config.*
import org.fxboomk.fcitx5.android.input.preedit.CompositionAreaStyle
import org.junit.Assert.*
import org.junit.Test

/** Upgrade contracts against the real Android preference and theme stores. */
class PersonalUpdateCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun candidateModesAndHardwareSettingsSurviveUpgrade() {
        val context = instrumentation.targetContext
        val storage = context.getSharedPreferences("maintenance_candidate_upgrade", Context.MODE_PRIVATE)
        val modeKey = AppPrefs.CANDIDATE_DISPLAY_MODE_KEY
        try {
            FloatingCandidatesMode.entries.forEach { mode ->
                storage.edit().clear().putString(modeKey, mode.name).commit()
                assertEquals(mode, AppPrefs(storage).candidates.mode.getValue())
                assertEquals(mode.name, storage.getString(modeKey, null))
            }
            storage.edit().putString(modeKey, "FutureMode").commit()
            assertEquals(FloatingCandidatesMode.InputDevice,
                AppPrefs(storage).candidates.mode.getValue())
            assertEquals("FutureMode", storage.getString(modeKey, null))
        } finally { storage.edit().clear().commit() }
        instrumentation.runOnMainSync {
            val preference = AppPrefs.getInstance().candidates.mode
            val original = preference.getValue()
            try {
                FloatingCandidatesMode.entries.forEach { mode ->
                    preference.setValue(mode)
                    val keys = SettingsSearchIndex.androidItems(context).mapNotNull { it.preferenceKey }.toSet()
                    assertTrue("Horizontal dock controls must remain searchable in $mode", "horizontal_candidate_style" in keys)
                    if (mode == FloatingCandidatesMode.InputDevice ||
                        mode == FloatingCandidatesMode.Always) {
                        assertTrue("Hardware floating controls must remain searchable in $mode", "personal_reverse_above_cursor" in keys)
                        assertTrue("candidates_window_orientation" in keys)
                    }
                }
            } finally { preference.setValue(original) }
        }
    }

    @Test fun scopedPreeditNeverRewritesTheGlobalChoice() {
        instrumentation.runOnMainSync {
            val prefs = AppPrefs.getInstance()
            val global = prefs.keyboard.compositionAreaStyle
            val scopes = prefs.internal.preeditStyleScopes
            val oldGlobal = global.getValue()
            val oldScopes = scopes.getValue().toSet()
            fun entry(name: String, addon: String) =
                InputMethodEntry(name, name, "", name, "", "zh", addon, true)
            val first = entry("maintenance-pinyin", "maintenance-addon")
            val other = entry("maintenance-other", "maintenance-other-addon")
            try {
                scopes.setValue(emptySet())
                global.setValue(CompositionAreaStyle.InlineCandidateBar)
                PreeditStyleResolver.apply(first)
                assertEquals(CompositionAreaStyle.InlineCandidateBar, PreeditStyleResolver.effectiveStyle)
                PreeditStyleScopes.write("addon:${first.addon}", PreeditStyleOption.STANDALONE)
                PreeditStyleResolver.apply(first)
                assertEquals(CompositionAreaStyle.Default, PreeditStyleResolver.effectiveStyle)
                assertEquals(CompositionAreaStyle.InlineCandidateBar, global.getValue())
                PreeditStyleScopes.write("im:${first.uniqueName}", PreeditStyleOption.EMBEDDED)
                PreeditStyleResolver.apply(first)
                assertEquals(CompositionAreaStyle.InlineCandidateBar, PreeditStyleResolver.effectiveStyle)
                PreeditStyleResolver.apply(other)
                assertEquals(CompositionAreaStyle.InlineCandidateBar, PreeditStyleResolver.effectiveStyle)
                PreeditStyleScopes.write("im:${other.uniqueName}", "future-unknown-style")
                PreeditStyleResolver.apply(other)
                assertEquals(CompositionAreaStyle.InlineCandidateBar, PreeditStyleResolver.effectiveStyle)
                assertEquals(CompositionAreaStyle.InlineCandidateBar, global.getValue())
            } finally {
                scopes.setValue(oldScopes)
                global.setValue(oldGlobal)
                PreeditStyleResolver.apply(entry("", ""))
            }
        }
    }

    @Test fun bundledThemeNamesPreserveSavedIconsAndLegacyLayout() {
        instrumentation.runOnMainSync {
            val files = listOfNotNull(UserConfigFiles.buttonsLayoutThemes(),
                UserConfigFiles.buttonsLayoutConfig(), UserConfigFiles.keyIconThemesConfig())
            assertEquals(3, files.size)
            val originals = files.associateWith { if (it.isFile) it.readBytes() else null }
            try {
                val custom = ButtonsLayoutConfig.default().copy(kawaiiBarButtons = listOf(
                    ConfigurableButton("undo", icon = "font:E123", label = "My undo"),
                    ConfigurableButton("clipboard", icon = "font:E124")))
                ButtonsLayoutThemeStore.save("谷歌", custom)
                val saved = files[0].readBytes()
                repeat(2) {
                    assertEquals(custom, ButtonsLayoutThemeStore.load().themes.first { it.name == "谷歌" }.config)
                }
                assertArrayEquals(saved, files[0].readBytes())
                assertEquals(custom, ButtonsLayoutThemeStore.activate("谷歌"))
                val icons = KeyIconThemesConfig("谷歌", listOf(KeyIconTheme("谷歌",
                    mapOf(KeyIconIds.CAPS_ONCE to "font:E125"))))
                KeyIconThemeStore.write(icons).getOrThrow()
                assertEquals("font:E125", KeyIconThemeStore.activeTheme().icons[KeyIconIds.CAPS_ONCE])
                val legacy = originals[files[1]]
                if (legacy == null) assertFalse(files[1].isFile)
                else assertArrayEquals(legacy, files[1].readBytes())
            } finally {
                originals.forEach { (file, bytes) ->
                    if (bytes == null) file.delete() else file.writeBytes(bytes)
                }
            }
        }
    }

    @Test fun retainedPluginAndTypographyControlsRemainSearchable() {
        instrumentation.runOnMainSync {
            val keys = SettingsSearchIndex.androidItems(instrumentation.targetContext)
                .mapNotNull { it.preferenceKey }.toSet()
            listOf("allow_original_plugins", "preedit_style", "sub_label_padding",
                "secondary_label_position").forEach { assertTrue("Missing setting $it", it in keys) }
        }
    }
}
