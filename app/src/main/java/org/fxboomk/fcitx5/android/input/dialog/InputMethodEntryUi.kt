/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
@file:Suppress("OVERRIDE_DEPRECATION")

package org.fxboomk.fcitx5.android.input.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import org.fxboomk.fcitx5.android.utils.alpha
import splitties.dimensions.dp
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.views.dsl.core.Ui
import kotlin.math.min

class InputMethodEntryUi(override val ctx: Context) : Ui {
    private val activatedBgColor = ctx.styledColor(android.R.attr.colorControlActivated)
    private val activatedTextColor = if (ColorUtils.calculateLuminance(activatedBgColor) > 0.5) {
        Color.BLACK
    } else {
        Color.WHITE
    }

    val title = TextView(ctx).apply {
        textSize = 16f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        TextViewCompat.setTextAppearance(this, ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem))
        val defaultColor = currentTextColor
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
                intArrayOf(activatedTextColor, defaultColor)
            )
        )
    }

    val subtitle = TextView(ctx).apply {
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        TextViewCompat.setTextAppearance(this, ctx.resolveThemeAttribute(android.R.attr.textAppearanceSmall))
        val defaultColor = currentTextColor
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
                intArrayOf(activatedTextColor.alpha(0.74f), defaultColor)
            )
        )
    }

    private val titleTypeface = title.typeface

    /**
     * Leading single-choice indicator for a Rime schema row. It is decorative from an
     * accessibility standpoint; the row itself stays the click target.
     */
    internal val schemaRadio = RadioButton(ctx).apply {
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        minimumWidth = 0
        minWidth = 0
        minimumHeight = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
        // The platform glyph is a 32dp asset with a 20dp ring inside, and scaling it down
        // blurs the thin outline. Draw a self-contained 20dp indicator instead so the ring
        // stays crisp and keeps clear of the view bounds.
        buttonDrawable = SchemaRadioDrawable(
            ctx,
            uncheckedColor = ctx.styledColor(android.R.attr.colorControlNormal),
            checkedColor = activatedBgColor
        )
    }

    private val basePaddingStart = ctx.dp(8)
    private val basePaddingEnd = ctx.dp(8)
    private val schemaRowIndent = ctx.dp(20)

    override val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Align the leading indicator by gravity, not by the baseline of the empty
        // RadioButton: baseline alignment can shift it above the row and clip its top.
        isBaselineAligned = false
        minimumHeight = dp(40)
        setPadding(basePaddingStart, dp(5), basePaddingEnd, dp(5))
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_activated),
                GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(activatedBgColor)
                }
            )
            addState(
                intArrayOf(),
                ColorDrawable(Color.TRANSPARENT)
            )
        }
        isFocusable = true
        isClickable = true
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        addView(
            schemaRadio,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                // Keep a little air between the ring and the schema title without shifting
                // the title rows that have no indicator.
                marginEnd = dp(4)
            }
        )
        addView(
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                    topMargin = dp(1)
                    bottomMargin = dp(1)
                }
                addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2)
                })
            }
        )
    }

    fun setHierarchy(isChild: Boolean, isParent: Boolean) {
        schemaRadio.visibility = if (isChild) View.VISIBLE else View.GONE
        val verticalPadding = if (isChild) 0 else ctx.dp(5)
        // A schema row keeps the leading radio but shifts the whole row to the right.
        root.setPaddingRelative(
            basePaddingStart + if (isChild) schemaRowIndent else 0,
            verticalPadding,
            basePaddingEnd,
            verticalPadding
        )
        title.typeface = if (isParent) Typeface.create(titleTypeface, Typeface.BOLD) else titleTypeface
        ViewCompat.setAccessibilityHeading(title, isParent)
    }

    fun setSchemaSelected(selected: Boolean) {
        schemaRadio.isChecked = selected
    }

    fun setActivated(activated: Boolean) {
        root.isActivated = activated
        title.isActivated = activated
        subtitle.isActivated = activated
    }
}

/**
 * Ring-style indicator for a Rime schema row. It is drawn at 20dp with the 2dp stroke
 * inset from the bounds, so the outline is never clipped by the RadioButton viewport.
 * Colors are resolved from the current theme instead of the platform radio asset, whose
 * 32dp glyph only draws a 20dp ring and blurs when scaled.
 */
private class SchemaRadioDrawable(
    private val ctx: Context,
    private val uncheckedColor: Int,
    private val checkedColor: Int
) : Drawable() {
    private val strokeWidth = ctx.dp(2).toFloat()
    private val edgeInset = ctx.dp(1).toFloat()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SchemaRadioDrawable.strokeWidth
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var checked = false

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val newChecked = state.contains(android.R.attr.state_checked)
        if (newChecked == checked) return false
        checked = newChecked
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth() = ctx.dp(20)

    override fun getIntrinsicHeight() = ctx.dp(20)

    override fun draw(canvas: Canvas) {
        val radius = min(bounds.width(), bounds.height()) / 2f - edgeInset
        val color = if (checked) checkedColor else uncheckedColor
        ringPaint.color = color
        canvas.drawCircle(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            radius - strokeWidth / 2f,
            ringPaint
        )
        if (checked) {
            dotPaint.color = color
            canvas.drawCircle(
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                radius - strokeWidth * 2f,
                dotPaint
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        ringPaint.alpha = alpha
        dotPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ringPaint.colorFilter = colorFilter
        dotPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
