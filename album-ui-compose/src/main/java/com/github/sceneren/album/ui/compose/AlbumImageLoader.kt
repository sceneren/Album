package com.github.sceneren.album.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.github.sceneren.album.api.AlbumMedia

/**
 * 由宿主实现的 Compose 图片加载接口。
 *
 * `album-ui-compose` 不绑定 Coil、Glide 等图片加载库，只把宿主返回的 [Painter] 交给
 * Image 或基础 ZoomImage 组件绘制。
 */
fun interface AlbumImageLoader {
    @Composable
    /** 执行 `painter` 方法定义的处理。 */
    fun painter(
        media: AlbumMedia,
        target: AlbumImageTarget,
    ): Painter
}
