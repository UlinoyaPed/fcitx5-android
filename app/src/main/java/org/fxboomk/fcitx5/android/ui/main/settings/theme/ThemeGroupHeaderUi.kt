/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import org.fxboomk.fcitx5.android.R
import splitties.dimensions.dp
import splitties.resources.styledDrawable
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.textView

internal class ThemeGroupHeaderUi(override val ctx: Context) : Ui {
    private val title = textView {
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private val arrow = imageView {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        imageTintList = title.textColors
    }

    override val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPaddingRelative(dp(8), dp(8), dp(8), dp(8))
        background = ctx.styledDrawable(android.R.attr.selectableItemBackground)
        isFocusable = true
        addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(arrow, LinearLayout.LayoutParams(dp(24), dp(24)))
        ViewCompat.setAccessibilityHeading(this, true)
    }

    fun bind(header: ThemeListItem.Header, onToggle: () -> Unit) {
        val label = ctx.getString(if (header.isDark) R.string.dark_mode_theme else R.string.light_mode_theme)
        title.text = ctx.getString(R.string.theme_category_title, label, header.count)
        arrow.setImageResource(if (header.expanded) R.drawable.ic_baseline_expand_less_24 else R.drawable.ic_baseline_expand_more_24)
        ViewCompat.setStateDescription(root, ctx.getString(if (header.expanded) R.string.theme_category_expanded else R.string.theme_category_collapsed))
        root.setOnClickListener { onToggle() }
    }
}
