/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fxboomk.fcitx5.android.utils

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import splitties.experimental.InternalSplittiesApi
import splitties.resources.color
import splitties.resources.styledColor
import splitties.resources.withResolvedThemeAttribute
import splitties.views.dsl.core.Ui

/**
 * 工具栏“保存”菜单图标的可点击态着色：
 * 可点击用主题主文字色（亮色模式呈深色、暗色模式呈浅色，与 colorPrimary 工具栏保持高对比），
 * 不可点击用主题提示色（半透明灰，两种模式下均呈置灰观感）。
 */
@ColorInt
fun Context.saveIconTint(enabled: Boolean): Int =
    styledColor(if (enabled) android.R.attr.textColorPrimary else android.R.attr.textColorHint)

@OptIn(InternalSplittiesApi::class)
fun Context.styledFloat(@AttrRes attrRes: Int) = withResolvedThemeAttribute(attrRes) {
    when (type) {
        TypedValue.TYPE_FLOAT -> float
        else -> throw IllegalArgumentException("float attribute expected")
    }
}

@OptIn(InternalSplittiesApi::class)
@ColorInt
fun Context.styledColorOrDefault(@AttrRes attrRes: Int, @ColorInt defaultValue: Int) =
    withResolvedThemeAttribute(attrRes) {
        when (type) {
            in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> data
            TypedValue.TYPE_STRING if string.startsWith("res/color/") -> color(resourceId)
            else -> defaultValue
        }
    }

@ColorInt
const val DefaultAccentColor = 0xFF008577.toInt() // material_deep_teal_500

@Suppress("NOTHING_TO_INLINE")
inline fun View.styledFloat(@AttrRes attrRes: Int) = context.styledFloat(attrRes)

@Suppress("NOTHING_TO_INLINE")
inline fun View.styledColorOrDefault(@AttrRes attrRes: Int, @ColorInt defaultValue: Int) =
    context.styledColorOrDefault(attrRes, defaultValue)

@Suppress("NOTHING_TO_INLINE")
inline fun Ui.styledFloat(@AttrRes attrRes: Int) = ctx.styledFloat(attrRes)

@Suppress("NOTHING_TO_INLINE")
inline fun Ui.styledColorOrDefault(@AttrRes attrRes: Int, @ColorInt defaultValue: Int) =
    ctx.styledColorOrDefault(attrRes, defaultValue)

inline val ConstraintLayout.LayoutParams.unset
    get() = ConstraintLayout.LayoutParams.UNSET
