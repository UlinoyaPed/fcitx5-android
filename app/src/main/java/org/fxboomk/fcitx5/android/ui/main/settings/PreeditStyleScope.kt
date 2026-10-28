/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.input.preedit.CompositionAreaStyle

/**
 * Identifies the storage slot for one [PreeditStyleOption] value.
 *
 * Addon pages use `addon:<name>` as the engine-wide default. Input method
 * pages use `im:<uniqueName>` and fall back to the addon default until the
 * user changes that input method's value.
 */
data class PreeditStyleScope(
    val storageKey: String,
    val fallbackKey: String? = null
) {
    companion object {
        fun addon(uniqueName: String) = PreeditStyleScope("addon:$uniqueName")

        fun forInputMethod(uniqueName: String, engineAddon: String) =
            PreeditStyleScope("im:$uniqueName", "addon:$engineAddon")
    }
}

/**
 * Stores the four-option preedit style per scope in a string set.
 *
 * Entries are encoded as `<scope>=<value>` so a single [AppPrefs.Internal]
 * preference can hold independent values for every addon and input method.
 */
internal object PreeditStyleScopes {
    private fun prefs() = AppPrefs.getInstance().internal.preeditStyleScopes

    fun read(key: String?): String? {
        if (key == null) return null
        val prefix = "$key="
        return prefs().getValue()
            .firstOrNull { it.startsWith(prefix) }
            ?.substring(prefix.length)
    }

    fun write(key: String, value: String) {
        val storage = prefs()
        val entries = storage.getValue().toMutableSet()
        entries.removeAll { it.startsWith("$key=") }
        entries.add("$key=$value")
        storage.setValue(entries)
    }
}

/**
 * Applies the effective preedit style of the active input method to the
 * keyboard's global composition-area preference.
 */
internal object PreeditStyleResolver {
    fun apply(entry: InputMethodEntry) {
        val stored = PreeditStyleScopes.read("im:${entry.uniqueName}")
            ?: PreeditStyleScopes.read("addon:${entry.addon}")
        setInline(stored == PreeditStyleOption.EMBEDDED)
    }

    fun applyActive(fcitx: FcitxAPI) {
        apply(fcitx.inputMethodEntryCached)
    }

    private fun setInline(inline: Boolean) {
        val keyboard = AppPrefs.getInstance().keyboard
        val target = if (inline) {
            CompositionAreaStyle.InlineCandidateBar
        } else {
            CompositionAreaStyle.Default
        }
        if (keyboard.compositionAreaStyle.getValue() == target) return
        keyboard.compositionAreaStyle.setValue(target)
        keyboard.fireChange(keyboard.compositionAreaStyle.key)
    }
}
