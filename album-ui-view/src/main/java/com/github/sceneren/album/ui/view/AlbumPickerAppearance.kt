package com.github.sceneren.album.ui.view

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange

/**
 * View 实现的外观和网格布局配置；未设置的颜色、图标从 Theme 属性读取。
 *
 * @property gridItemSpacingDp RecyclerView item 之间的间距，单位 dp，不包含网格外边缘，默认 1dp。
 * @property gridSpanCount RecyclerView 每行展示的 item 数量，范围为 1..100，默认 4 个。
 */
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
    @IntRange(from = 0) val gridItemSpacingDp: Int = 1,
    @IntRange(from = 1, to = 100) val gridSpanCount: Int = 4,
) {
    init {
        require(gridItemSpacingDp >= 0) { "gridItemSpacingDp 不能小于 0" }
        require(gridSpanCount in 1..MAX_GRID_SPAN_COUNT) {
            "gridSpanCount 必须在 1..$MAX_GRID_SPAN_COUNT 之间"
        }
    }

    companion object {
        const val MAX_GRID_SPAN_COUNT: Int = 100
    }
}
