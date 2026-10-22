/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import org.fxboomk.fcitx5.android.ui.main.MainViewModel
import org.fxboomk.fcitx5.android.utils.CollapsiblePreferenceCategory
import splitties.resources.styledColor
import kotlin.math.roundToInt

object PreferenceScrollHelper {

    /**
     * Key of the scroll request that is currently being processed, so that we never run
     * two scroll+highlight sequences for the same preference at the same time.
     */
    private var scrollingKey: String? = null

    /**
     * Scroll to the preference requested by [viewModel] and highlight it.
     *
     * [tab] is the index of the tab the preference lives on, for pages that host their
     * preferences in a ViewPager. It is used to make sure that only the tab actually
     * holding the preference reacts to the request.
     */
    fun scrollToPendingPreference(
        fragment: PreferenceFragmentCompat,
        viewModel: MainViewModel,
        tab: Int? = null
    ) {
        val key = viewModel.peekPendingPreferenceScrollKey() ?: return
        val pendingTab = viewModel.peekPendingPreferenceTab()
        // The request targets another tab of the same page, leave it to that tab.
        if (pendingTab != null && pendingTab != tab) return
        // Only handle the request when the page is actually visible, otherwise we would
        // scroll and highlight a page the user is not looking at. The lifecycle of the
        // fragment is still STARTED while onResume runs, so use isResumed instead.
        if (!fragment.isResumed) return
        val listView = fragment.safeListView() ?: return
        val preference = fragment.findPreference<Preference>(key) ?: return
        // Preferences living in a collapsed group are not part of the visible list,
        // expand the group first, otherwise there is nothing to scroll to.
        expandCollapsedAncestors(preference)
        if (scrollingKey == key) return
        scrollingKey = key
        listView.post {
            scrollToPreference(listView, key, viewModel)
        }
    }

    /**
     * Expand every collapsed group the preference belongs to.
     */
    private fun expandCollapsedAncestors(preference: Preference) {
        var parent = preference.parent
        while (parent != null) {
            if (parent is CollapsiblePreferenceCategory && parent.isCollapsed) {
                parent.setCollapsed(false)
            }
            parent = parent.parent
        }
    }

    private fun scrollToPreference(
        listView: RecyclerView,
        key: String,
        viewModel: MainViewModel,
        attempt: Int = 0
    ) {
        val position = (listView.adapter as? PreferenceGroup.PreferencePositionCallback)
            ?.getPreferenceAdapterPosition(key)
            ?: RecyclerView.NO_POSITION
        if (position == RecyclerView.NO_POSITION) {
            // The visible list is rebuilt asynchronously when a group is expanded,
            // give it some time before giving up.
            if (attempt < MAX_POSITION_ATTEMPTS) {
                listView.postDelayed({
                    scrollToPreference(listView, key, viewModel, attempt + 1)
                }, POSITION_RETRY_DELAY_MS)
            } else {
                scrollingKey = null
            }
            return
        }
        listView.scrollToPosition(position)
        highlightWhenBound(
            listView,
            position,
            onHighlighted = {
                viewModel.consumePendingPreferenceScrollKey(key)
                scrollingKey = null
            },
            onFailed = {
                // Keep the request pending, a later attempt may still succeed.
                scrollingKey = null
            }
        )
    }

    private fun highlightWhenBound(
        listView: RecyclerView,
        position: Int,
        attempt: Int = 0,
        onHighlighted: () -> Unit,
        onFailed: () -> Unit
    ) {
        listView.postOnAnimation {
            val itemView = listView.findViewHolderForAdapterPosition(position)?.itemView
            if (itemView != null) {
                listView.postDelayed({
                    val boundView = listView.findViewHolderForAdapterPosition(position)?.itemView
                    if (boundView != null && boundView.isReadyForHighlight()) {
                        highlight(boundView)
                        onHighlighted()
                    } else {
                        retryHighlight(listView, position, attempt, onHighlighted, onFailed)
                    }
                }, HIGHLIGHT_START_DELAY_MS)
            } else {
                retryHighlight(listView, position, attempt, onHighlighted, onFailed)
            }
        }
    }

    private fun retryHighlight(
        listView: RecyclerView,
        position: Int,
        attempt: Int,
        onHighlighted: () -> Unit,
        onFailed: () -> Unit
    ) {
        if (attempt >= MAX_BIND_ATTEMPTS) {
            onFailed()
            return
        }
        listView.postDelayed({
            highlightWhenBound(listView, position, attempt + 1, onHighlighted, onFailed)
        }, HIGHLIGHT_RETRY_DELAY_MS)
    }

    /**
     * The highlight is drawn on the overlay of the view, it can only be drawn once the
     * view has been laid out and has a size.
     */
    private fun View.isReadyForHighlight(): Boolean =
        isAttachedToWindow && isLaidOut && width > 0 && height > 0

    private fun highlight(view: View) {
        val density = view.resources.displayMetrics.density
        val highlight = HighlightOutlineDrawable(
            strokeColor = view.context.styledColor(android.R.attr.colorAccent),
            cornerRadius = CORNER_RADIUS_DP * density,
            strokeSize = (STROKE_WIDTH_DP * density).roundToInt().coerceAtLeast(1),
            horizontalInset = HORIZONTAL_INSET_DP * density,
            verticalInset = VERTICAL_INSET_DP * density
        )
        // The drawable draws itself relative to its own bounds, always use the current
        // size of the view so that it is never drawn with an empty area.
        highlight.setBounds(0, 0, view.width, view.height)
        view.overlay.add(highlight)
        // A search result must remain visible even when system animations are disabled.
        // Start the hold after its first frame, so slow layout/rendering cannot consume
        // the whole indication before the user sees it.
        val removeHighlight = object : Runnable, View.OnAttachStateChangeListener {
            override fun run() {
                view.overlay.remove(highlight)
                view.removeCallbacks(this)
                view.removeOnAttachStateChangeListener(this)
            }

            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) = run()
        }
        view.addOnAttachStateChangeListener(removeHighlight)
        view.doOnPreDraw {
            view.postOnAnimation {
                if (view.isAttachedToWindow) {
                    view.postDelayed(removeHighlight, HIGHLIGHT_DURATION_MS)
                } else {
                    removeHighlight.run()
                }
            }
        }
    }

    /**
     * Rounded rectangle outline drawn relative to the bounds of the drawable, so that it always
     * matches the size of the view it highlights, no matter when it is added.
     *
     * Only the outline is painted on purpose. A filled shape would lay a translucent scrim
     * over the whole entry, which hides the content the user just jumped to.
     */
    private class HighlightOutlineDrawable(
        strokeColor: Int,
        private val cornerRadius: Float,
        strokeSize: Int,
        private val horizontalInset: Float,
        private val verticalInset: Float
    ) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            strokeWidth = strokeSize.toFloat()
        }

        /**
         * Alpha of the color of the outline. It has to be scaled by the animation instead of
         * being overwritten by it, [Paint.setAlpha] replaces the alpha of the color and would
         * therefore paint a translucent color as if it was opaque.
         */
        private val baseAlpha = strokePaint.alpha

        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val current = bounds
            if (current.isEmpty) return
            rect.set(
                current.left + horizontalInset,
                current.top + verticalInset,
                current.right - horizontalInset,
                current.bottom - verticalInset
            )
            if (rect.width() <= 0f || rect.height() <= 0f) return
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = baseAlpha * alpha / 255
            invalidateSelf()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun PreferenceFragmentCompat.safeListView(): RecyclerView? = try {
        if (view == null) null else listView
    } catch (e: IllegalStateException) {
        null
    }

    private const val MAX_POSITION_ATTEMPTS = 12
    private const val MAX_BIND_ATTEMPTS = 20
    private const val POSITION_RETRY_DELAY_MS = 50L
    private const val HIGHLIGHT_START_DELAY_MS = 300L
    private const val HIGHLIGHT_RETRY_DELAY_MS = 120L
    private const val HIGHLIGHT_DURATION_MS = 1400L
    private const val HORIZONTAL_INSET_DP = 8
    private const val VERTICAL_INSET_DP = 2
    private const val CORNER_RADIUS_DP = 8f
    private const val STROKE_WIDTH_DP = 2
}
