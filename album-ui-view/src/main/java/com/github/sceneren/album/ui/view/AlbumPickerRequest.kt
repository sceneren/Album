package com.github.sceneren.album.ui.view

import androidx.annotation.AnimRes
import androidx.annotation.StyleRes
import com.github.sceneren.album.api.AlbumPickerConfig

/**
 * View 相册选择器的一次启动请求。
 *
 * [animation] 默认使用组件自带的底部弹出动画；传入 `null` 会关闭全部 Activity 过渡动画。
 */
data class AlbumPickerRequest(
    /** 选择器会话配置。 */
    val config: AlbumPickerConfig,
    /** 表示 `themeResId` 对应的数据。 */
    @StyleRes val themeResId: Int = 0,
    /** 表示 `appearance` 对应的数据。 */
    val appearance: AlbumPickerAppearance = AlbumPickerAppearance(),
    /** 页面打开和关闭时使用的动画配置。 */
    val animation: AlbumPickerAnimation? = AlbumPickerAnimation(),
)

/**
 * 相册选择器打开与关闭时使用的 Activity 动画资源。
 *
 * 字段语义与 Android `windowAnimationStyle` 的四个 Activity 动画属性一致，宿主可为任一字段传
 * `0` 来关闭该阶段的动画。
 */
data class AlbumPickerAnimation(
    /** 表示 `openEnterResId` 对应的数据。 */
    @AnimRes val openEnterResId: Int = R.anim.auv_album_picker_enter,
    /** 表示 `openExitResId` 对应的数据。 */
    @AnimRes val openExitResId: Int = R.anim.auv_album_picker_hold,
    /** 表示 `closeEnterResId` 对应的数据。 */
    @AnimRes val closeEnterResId: Int = R.anim.auv_album_picker_hold,
    /** 表示 `closeExitResId` 对应的数据。 */
    @AnimRes val closeExitResId: Int = R.anim.auv_album_picker_exit,
)
