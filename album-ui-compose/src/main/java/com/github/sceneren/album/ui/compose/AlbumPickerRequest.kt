package com.github.sceneren.album.ui.compose

import androidx.annotation.AnimRes
import androidx.annotation.StyleRes
import com.github.sceneren.album.api.AlbumPickerConfig

/**
 * Compose 相册选择器的一次启动请求。
 *
 * [animation] 默认使用组件自带的底部弹出动画；传入 `null` 会关闭全部 Activity 过渡动画。
 */
data class AlbumPickerRequest(
    val config: AlbumPickerConfig,
    @StyleRes val themeResId: Int = 0,
    val appearance: AlbumPickerAppearance = AlbumPickerAppearance(),
    val animation: AlbumPickerAnimation? = AlbumPickerAnimation(),
)

/**
 * 相册选择器打开与关闭时使用的 Activity 动画资源。
 *
 * 字段语义与 Android `windowAnimationStyle` 的四个 Activity 动画属性一致，宿主可为任一字段传
 * `0` 来关闭该阶段的动画。
 */
data class AlbumPickerAnimation(
    @AnimRes val openEnterResId: Int = R.anim.auc_album_picker_enter,
    @AnimRes val openExitResId: Int = R.anim.auc_album_picker_hold,
    @AnimRes val closeEnterResId: Int = R.anim.auc_album_picker_hold,
    @AnimRes val closeExitResId: Int = R.anim.auc_album_picker_exit,
)
