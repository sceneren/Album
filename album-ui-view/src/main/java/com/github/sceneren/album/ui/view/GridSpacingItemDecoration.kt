package com.github.sceneren.album.ui.view

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** 仅在 item 之间增加间距，不给 RecyclerView 外边缘增加额外留白。 */
internal class GridSpacingItemDecoration(
    private val metrics: GridMetrics,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.set(0, 0, 0, 0)
        if (metrics.spacingPx == 0) return

        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % metrics.spanCount
        outRect.left = ((column.toLong() * metrics.spacingPx) / metrics.spanCount).toInt()
        val nextColumnOffset = (((column + 1L) * metrics.spacingPx) / metrics.spanCount).toInt()
        outRect.right = metrics.spacingPx - nextColumnOffset
        if (position >= metrics.spanCount) outRect.top = metrics.spacingPx
    }
}
