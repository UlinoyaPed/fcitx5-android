/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.input.predict.LlmPrefs
import splitties.dimensions.dp

object ThinkingEffortSelector {
    fun create(
        context: Context,
        initial: LlmPrefs.ThinkingEffort,
        onChanged: (LlmPrefs.ThinkingEffort) -> Unit = {},
    ): View {
        val titleView = TextView(context).apply {
            text = context.getString(R.string.llm_thinking_effort)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        val valueView = TextView(context).apply {
            text = context.getString(initial.titleRes)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                titleView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                valueView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val seekBar = SeekBar(context).apply {
            max = LlmPrefs.ThinkingEffort.entries.lastIndex
            progress = initial.ordinal
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val effort = LlmPrefs.ThinkingEffort.from(progress)
                    valueView.text = context.getString(effort.titleRes)
                    if (fromUser) onChanged(effort)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(8), 0, 0)
            addView(header)
            addView(
                seekBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }
}
