package com.github.sceneren.album.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.github.sceneren.album.api.AlbumMedia

/** Compose 相册需要加载图片的用途，宿主可据此选择缩略图尺寸或解码质量。 */
enum class AlbumImageTarget {
    GRID_THUMBNAIL,
    PREVIEW_IMAGE,
    VIDEO_COVER,
}

/**
 * 由宿主实现的 Compose 图片加载接口。
 *
 * `album-ui-compose` 不绑定 Coil、Glide 等图片加载库，只把宿主返回的 [Painter] 交给
 * Image 或基础 ZoomImage 组件绘制。
 */
fun interface AlbumImageLoader {
    @Composable
    fun painter(
        media: AlbumMedia,
        target: AlbumImageTarget,
    ): Painter
}

/** `album-ui-compose` 的进程级配置，建议在 Application.onCreate 中初始化。 */
object AlbumUi {
    @Volatile
    private var configuredImageLoader: AlbumImageLoader? = null

    fun setImageLoader(imageLoader: AlbumImageLoader) {
        configuredImageLoader = imageLoader
    }

    internal fun requireImageLoader(): AlbumImageLoader = checkNotNull(configuredImageLoader) {
        "请先调用 AlbumUi.setImageLoader 配置宿主图片加载器"
    }
}
