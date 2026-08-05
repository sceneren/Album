package com.github.sceneren.album.ui.view

import android.widget.ImageView
import com.github.sceneren.album.api.AlbumMedia

/** View 相册需要加载图片的用途，宿主可据此选择缩略图尺寸或解码质量。 */
enum class AlbumImageTarget {
    GRID_THUMBNAIL,
    PREVIEW_IMAGE,
    VIDEO_COVER,
}

/**
 * 由宿主实现的 View 图片加载接口。
 *
 * `album-ui-view` 不绑定 Coil、Glide 等图片加载库。预览目标传入的 [ImageView] 实际为
 * ZoomImageView，宿主仍按普通 ImageView 设置 Drawable 即可。
 */
fun interface AlbumImageLoader {
    fun load(
        imageView: ImageView,
        media: AlbumMedia,
        target: AlbumImageTarget,
    )

    /** RecyclerView 回收或重新绑定时清理旧请求；默认仅移除 Drawable。 */
    fun clear(imageView: ImageView) {
        imageView.setImageDrawable(null)
    }
}

/** `album-ui-view` 的进程级配置，建议在 Application.onCreate 中初始化。 */
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
