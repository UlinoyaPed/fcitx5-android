/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
@file:Suppress("ClickableViewAccessibility")
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

class HueSliderView(context: Context) : View(context) {

    var onHueChanged: ((Float) -> Unit)? = null

    private var hue = 0f
    private val gradientPaint = Paint()
    private val gradientColors = intArrayOf(
        0xFFFF0000.toInt(),
        0xFFFFFF00.toInt(),
        0xFF00FF00.toInt(),
        0xFF00FFFF.toInt(),
        0xFF0000FF.toInt(),
        0xFFFF00FF.toInt(),
        0xFFFF0000.toInt()
    )
    private var gradient: LinearGradient? = null
    private val indicatorPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = (2f * resources.displayMetrics.density)
    }

    fun setHue(hue: Float) {
        this.hue = hue.coerceIn(0f, 360f)
        gradient = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (gradient == null || gradientHeight != height) {
            gradient = LinearGradient(0f, 0f, 0f, height.toFloat(), gradientColors, null, Shader.TileMode.CLAMP)
            gradientHeight = height
        }
        gradientPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

        val y = (hue / 360f) * height
        canvas.drawRect(0f, y - 2f, width.toFloat(), y + 2f, indicatorPaint)
    }

    private var gradientHeight = -1

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateHueFromTouch(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.action == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateHueFromTouch(rawY: Float) {
        if (height <= 0) return
        val y = rawY.coerceIn(0f, height.toFloat())
        val newHue = (y / height) * 360f
        if (newHue != hue) {
            hue = newHue
            invalidate()
            onHueChanged?.invoke(hue)
        }
    }
}
