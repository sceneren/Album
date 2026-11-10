package com.github.sceneren.album.ui.compose

import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerResult
import com.github.sceneren.album.video.AlbumVideoPlayerFactory

/**
 * 嵌入已有页面的相册内容：目录切换、媒体网格、拍摄、添加及预览。
 * 不提供页面标题、返回/取消按钮、底部确认栏，不修改系统栏或消费系统栏边距。
 * 宿主通过 [state] 展示选择数量并执行确认、预览和取消，并负责限定组件高度。
 * 不播放页面进出动画；返回键仅在内部预览或目录展开时被处理。
 * [config] 的单选立即完成策略仍然生效；宿主自行确认时应使用 EXPLICIT_CONFIRM 模式。
 * [videoPlayerFactory] 默认跟随 [AlbumUi]；显式传 null 时回退为静态视频封面。
 */
@Composable
fun AlbumPickerContent(
    config: AlbumPickerConfig,
    state: AlbumPickerState,
    onResult: (AlbumPickerResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    appearance: AlbumPickerAppearance = AlbumPickerAppearance(),
    imageLoader: AlbumImageLoader? = null,
    videoPlayerFactory: AlbumVideoPlayerFactory? = AlbumUi.videoPlayerFactoryOrNull(),
) {
    key(config) {
        AlbumPickerHost(
            config = config,
            onResult = onResult,
            onCancel = onCancel,
            modifier = modifier,
            appearance = appearance,
            imageLoader = imageLoader,
            videoPlayerFactory = videoPlayerFactory,
            animation = null,
            embedded = true,
            state = state,
        )
    }
}
