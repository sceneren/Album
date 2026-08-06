package com.github.sceneren.album.ui.compose

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange

/** Compose 实现的颜色和图标覆盖项。 */
data class AlbumPickerAppearance(
    @ColorInt val toolbarColor: Int? = null,
    @ColorInt val bottomBarColor: Int? = null,
    @ColorInt val previewBackgroundColor: Int? = null,
    @ColorInt val accentColor: Int? = null,
    @ColorInt val primaryTextColor: Int? = null,
    @ColorInt val secondaryTextColor: Int? = null,
    @ColorInt val scrimColor: Int? = null,
    @DrawableRes val backIconRes: Int? = null,
    @DrawableRes val cameraIconRes: Int? = null,
    @DrawableRes val addIconRes: Int? = null,
    @DrawableRes val checkedIconRes: Int? = null,
    @DrawableRes val uncheckedIconRes: Int? = null,
    @DrawableRes val folderIconRes: Int? = null,
    @DrawableRes val doneIconRes: Int? = null,
    @DrawableRes val videoIconRes: Int? = null,
    /** RecyclerView/XML implementation-compatible spacing between grid cells, in dp. */
    @IntRange(from = 0) val gridItemSpacingDp: Int = 1,
    /** RecyclerView/XML implementation-compatible number of cells per row. */
    @IntRange(from = 1, to = 100) val gridSpanCount: Int = 4,
) {
    init {
        require(gridItemSpacingDp >= 0) { "gridItemSpacingDp must be >= 0" }
        require(gridSpanCount in 1..MAX_GRID_SPAN_COUNT) {
            "gridSpanCount must be in 1..$MAX_GRID_SPAN_COUNT"
        }
    }

    companion object {
        const val MAX_GRID_SPAN_COUNT: Int = 100
    }
}
