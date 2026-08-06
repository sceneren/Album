package com.github.sceneren.album.ui.view

import android.widget.ImageView
import com.github.sceneren.album.api.AlbumMedia

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
