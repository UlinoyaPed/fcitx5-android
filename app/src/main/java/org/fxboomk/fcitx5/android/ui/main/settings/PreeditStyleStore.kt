/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import androidx.preference.PreferenceDataStore
import org.fxboomk.fcitx5.android.core.RawConfig
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.input.preedit.CompositionAreaStyle

internal class PreeditStyleStore(
    private val cfg: RawConfig,
    private val engineValues: PreeditStyleOption.EngineValues,
    private val scope: PreeditStyleScope? = null
) : PreferenceDataStore() {
    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        val keyboard = AppPrefs.getInstance().keyboard
        val stored = scope?.let {
            PreeditStyleScopes.read(it.storageKey) ?: PreeditStyleScopes.read(it.fallbackKey)
        }
        val inlineInCandidateBar = when (stored) {
            PreeditStyleOption.EMBEDDED -> true
            PreeditStyleOption.STANDALONE,
            PreeditStyleOption.COMPOSING,
            PreeditStyleOption.COMMIT_PREVIEW -> false
            else -> keyboard.compositionAreaStyle.getValue() ==
                CompositionAreaStyle.InlineCandidateBar
        }
        return PreeditStyleOption.displayValue(
            engineValue = cfg.value,
            inlineInCandidateBar = inlineInCandidateBar,
            engineValues = engineValues
        )
    }

    override fun putString(key: String?, value: String?) {
        if (key == null || value == null) return
        when (value) {
            PreeditStyleOption.STANDALONE -> {
                cfg.value = engineValues.none
                writeScope(value)
            }
            PreeditStyleOption.EMBEDDED -> {
                cfg.value = engineValues.none
                writeScope(value)
            }
            PreeditStyleOption.COMPOSING -> {
                cfg.value = engineValues.composing
                writeScope(value)
            }
            PreeditStyleOption.COMMIT_PREVIEW -> {
                cfg.value = engineValues.commitPreview
                writeScope(value)
            }
        }
    }

    private fun writeScope(value: String) {
        if (scope != null) {
            PreeditStyleScopes.write(scope.storageKey, value)
            return
        }
        // Fallback for callers that do not provide a scope: preserve the old
        // behavior of updating the global composition-area style directly.
        val keyboard = AppPrefs.getInstance().keyboard
        val target = if (value == PreeditStyleOption.EMBEDDED) {
            CompositionAreaStyle.InlineCandidateBar
        } else {
            CompositionAreaStyle.Default
        }
        keyboard.compositionAreaStyle.setValue(target)
        keyboard.fireChange(keyboard.compositionAreaStyle.key)
    }
}
