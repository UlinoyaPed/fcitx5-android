/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import android.os.Handler
import android.os.Looper
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
 * Resolves the active input method's display override without changing the
 * user's persisted global default.
 */
internal object PreeditStyleResolver {
    @Volatile private var activeEntry: InputMethodEntry? = null
    @Volatile private var lastAppliedStyle: CompositionAreaStyle? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    val effectiveStyle: CompositionAreaStyle
        get() {
            val entry = activeEntry
            val stored = entry?.let {
                PreeditStyleScopes.read("im:${it.uniqueName}")
                    ?: PreeditStyleScopes.read("addon:${it.addon}")
            }
            return resolveScopedCompositionAreaStyle(
                stored, AppPrefs.getInstance().keyboard.compositionAreaStyle.getValue()
            )
        }

    fun apply(entry: InputMethodEntry) {
        activeEntry = entry
        val resolved = effectiveStyle
        if (lastAppliedStyle == resolved) return
        lastAppliedStyle = resolved
        val notify = Runnable {
            val keyboard = AppPrefs.getInstance().keyboard
            keyboard.fireChange(keyboard.compositionAreaStyle.key)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) notify.run() else mainHandler.post(notify)
    }

    fun applyActive(fcitx: FcitxAPI) {
        apply(fcitx.inputMethodEntryCached)
    }

}

internal fun resolveScopedCompositionAreaStyle(
    stored: String?, global: CompositionAreaStyle
): CompositionAreaStyle = when (stored) {
    PreeditStyleOption.EMBEDDED -> CompositionAreaStyle.InlineCandidateBar
    PreeditStyleOption.STANDALONE, PreeditStyleOption.COMPOSING,
    PreeditStyleOption.COMMIT_PREVIEW -> CompositionAreaStyle.Default
    else -> global
}
