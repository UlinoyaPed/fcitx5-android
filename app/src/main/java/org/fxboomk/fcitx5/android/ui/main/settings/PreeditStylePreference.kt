/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.preference.DialogPreference
import org.fxboomk.fcitx5.android.R

/**
 * Composite preference for the four preedit styles.
 *
 * Two groups are shown in the dialog so the editor-preview behavior is
 * explicit, while the persisted value is handled by [PreeditStyleStore].
 */
class PreeditStylePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : DialogPreference(context, attrs) {

    private var defaultValue = PreeditStyleOption.STANDALONE

    var value: String
        get() = getPersistedString(defaultValue)
        private set(newValue) {
            val normalized = if (newValue in PreeditStyleOption.values) {
                newValue
            } else {
                PreeditStyleOption.STANDALONE
            }
            if (normalized == getPersistedString(defaultValue)) return
            if (callChangeListener(normalized)) {
                persistString(normalized)
                notifyChanged()
                updateSummary()
            }
        }

    override fun onSetInitialValue(defaultValue: Any?) {
        this.defaultValue = defaultValue as? String ?: PreeditStyleOption.STANDALONE
        updateSummary()
    }

    override fun onClick() {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.preference_preedit_style, null)
        val group = view.findViewById<RadioGroup>(R.id.preedit_style_group)
        group.check(radioIdFor(value))

        val builder = AlertDialog.Builder(context)
            .setTitle(dialogTitle ?: title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                valueForRadioId(group.checkedRadioButtonId)?.let { value = it }
            }
            .setNegativeButton(android.R.string.cancel, null)
        dialogMessage?.let(builder::setMessage)
        builder.show()
    }

    private fun updateSummary() {
        summary = context.getString(PreeditStyleOption.labelRes(value))
    }

    private fun radioIdFor(value: String): Int = when (value) {
        PreeditStyleOption.EMBEDDED -> R.id.preedit_style_embedded
        PreeditStyleOption.COMPOSING -> R.id.preedit_style_composing
        PreeditStyleOption.COMMIT_PREVIEW -> R.id.preedit_style_commit_preview
        else -> R.id.preedit_style_standalone
    }

    private fun valueForRadioId(id: Int?): String? = when (id) {
        R.id.preedit_style_standalone -> PreeditStyleOption.STANDALONE
        R.id.preedit_style_embedded -> PreeditStyleOption.EMBEDDED
        R.id.preedit_style_composing -> PreeditStyleOption.COMPOSING
        R.id.preedit_style_commit_preview -> PreeditStyleOption.COMMIT_PREVIEW
        else -> null
    }
}
