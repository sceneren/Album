package com.github.sceneren.album.ui.view

/** 封装所有网格单元适配器和条目装饰器共享的像素尺寸。 */
internal data class GridMetrics(
    /** 网格每行的单元数量。 */
    val spanCount: Int,
    /** 网格单元间距，单位为像素。 */
    val spacingPx: Int,
)
