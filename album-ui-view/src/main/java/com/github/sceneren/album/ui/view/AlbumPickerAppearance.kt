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
    /** 工具栏颜色。 */
    @ColorInt val toolbarColor: Int? = null,
    /** 底部操作栏颜色。 */
    @ColorInt val bottomBarColor: Int? = null,
    /** 预览界面的背景颜色。 */
    @ColorInt val previewBackgroundColor: Int? = null,
    /** 界面的强调色。 */
    @ColorInt val accentColor: Int? = null,
    /** 主要文字颜色。 */
    @ColorInt val primaryTextColor: Int? = null,
    /** 次要文字颜色。 */
    @ColorInt val secondaryTextColor: Int? = null,
    /** 遮罩层颜色。 */
    @ColorInt val scrimColor: Int? = null,
    /** 返回图标资源。 */
    @DrawableRes val backIconRes: Int? = null,
    /** 相机图标资源。 */
    @DrawableRes val cameraIconRes: Int? = null,
    /** 添加图标资源。 */
    @DrawableRes val addIconRes: Int? = null,
    /** 已选中图标资源。 */
    @DrawableRes val checkedIconRes: Int? = null,
    /** 未选中图标资源。 */
    @DrawableRes val uncheckedIconRes: Int? = null,
    /** 目录图标资源。 */
    @DrawableRes val folderIconRes: Int? = null,
    /** 完成图标资源。 */
    @DrawableRes val doneIconRes: Int? = null,
    /** 视频图标资源。 */
    @DrawableRes val videoIconRes: Int? = null,
    /** 网格单元间距，单位为 dp。 */
    @IntRange(from = 0) val gridItemSpacingDp: Int = 1,
    /** 网格每行的单元数量。 */
    @IntRange(from = 1, to = 100) val gridSpanCount: Int = 4,
) {
    init {
        require(gridItemSpacingDp >= 0) { "gridItemSpacingDp 不能小于 0" }
        require(gridSpanCount in 1..MAX_GRID_SPAN_COUNT) {
            "gridSpanCount 必须在 1..$MAX_GRID_SPAN_COUNT 之间"
        }
    }

    /** 提供类级共享常量与工厂能力。 */
    companion object {
        /** 表示 `MAX_GRID_SPAN_COUNT` 对应的数据。 */
        const val MAX_GRID_SPAN_COUNT: Int = 100
    }
}
