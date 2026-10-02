/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ThemeListItemDecoration(val itemWidth: Int) :
    RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.setEmpty()
        val grid = parent.layoutManager as? GridLayoutManager ?: return
        val position = parent.getChildAdapterPosition(view)
        val itemCount = parent.adapter?.itemCount ?: return
        if (position !in 0 until itemCount) return
        val spanCount = grid.spanCount
        val width = parent.width - parent.paddingLeft - parent.paddingRight
        val columnWidth = width / spanCount
        val offset = ((width - itemWidth * spanCount) / (spanCount + 1)).coerceAtLeast(0)
        val halfOffset = offset / 2
        val lookup = grid.spanSizeLookup
        val row = lookup.getSpanGroupIndex(position, spanCount)
        val lastRow = lookup.getSpanGroupIndex(itemCount - 1, spanCount)
        val top = if (row == 0) offset else halfOffset
        val bottom = if (row == lastRow) offset else halfOffset
        if (lookup.getSpanSize(position) == spanCount) {
            outRect.set(offset, top, offset, bottom)
            return
        }
        val n = lookup.getSpanIndex(position, spanCount)

        when (parent.layoutDirection) {
            View.LAYOUT_DIRECTION_LTR -> {
                outRect.set(
                    (n + 1) * offset + n * (itemWidth - columnWidth),
                    top,
                    0, // (n + 1) * (columnWidth - itemWidth - offset)
                    bottom
                )
            }
            View.LAYOUT_DIRECTION_RTL -> {
                outRect.set(
                    0,
                    top,
                    (n + 1) * offset + n * (itemWidth - columnWidth),
                    bottom
                )
            }
        }
    }
}
