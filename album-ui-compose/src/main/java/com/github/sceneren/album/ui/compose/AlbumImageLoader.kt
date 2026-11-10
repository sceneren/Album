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

    /**
     * 加载保持完整画幅的预览大图。宿主应读取原始 URI 并按显示尺寸采样，避免读取已裁剪缩略图。
     * Coil 可用网格请求的 `placeholderMemoryCacheKey` 占位，大图使用独立缓存并在成功后替换小图。
     * 默认沿用 [painter] 的 PREVIEW_IMAGE 分支，兼容已有宿主实现。
     */
    @Composable
    fun previewPainter(media: AlbumMedia): Painter = painter(media, AlbumImageTarget.PREVIEW_IMAGE)
}
