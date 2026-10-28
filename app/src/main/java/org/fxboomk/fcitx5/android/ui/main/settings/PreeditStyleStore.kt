/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import androidx.preference.PreferenceDataStore
import org.fxboomk.fcitx5.android.core.RawConfig
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.input.preedit.CompositionAreaStyle

/**
 * Bridges the four app-level preedit styles to the engine's three-value
 * `PreeditMode` enum and the app's inline composition area preference.
 */
internal class PreeditStyleStore(
    private val cfg: RawConfig,
    private val engineValues: PreeditStyleOption.EngineValues
) : PreferenceDataStore() {

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        val keyboard = AppPrefs.getInstance().keyboard
        return PreeditStyleOption.displayValue(
            engineValue = cfg.value,
            inlineInCandidateBar =
                keyboard.compositionAreaStyle.getValue() == CompositionAreaStyle.InlineCandidateBar,
            engineValues = engineValues
        )
    }

    override fun putString(key: String?, value: String?) {
        if (key == null || value == null) return
        val keyboard = AppPrefs.getInstance().keyboard
        when (value) {
            PreeditStyleOption.STANDALONE -> {
                cfg.value = engineValues.none
                setInlineInCandidateBar(keyboard, false)
            }

            PreeditStyleOption.EMBEDDED -> {
                cfg.value = engineValues.none
                setInlineInCandidateBar(keyboard, true)
            }

            PreeditStyleOption.COMPOSING -> cfg.value = engineValues.composing
            PreeditStyleOption.COMMIT_PREVIEW -> cfg.value = engineValues.commitPreview
        }
    }

    private fun setInlineInCandidateBar(keyboard: AppPrefs.Keyboard, inline: Boolean) {
        keyboard.compositionAreaStyle.setValue(
            if (inline) CompositionAreaStyle.InlineCandidateBar else CompositionAreaStyle.Default
        )
        // InputView listens on the preference provider, not the ManagedPreference itself.
        keyboard.fireChange(keyboard.compositionAreaStyle.key)
    }
}
