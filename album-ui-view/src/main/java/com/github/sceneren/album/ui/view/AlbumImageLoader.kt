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
    /** 获取 `load` 所需的数据。 */
    fun load(
        imageView: ImageView,
        media: AlbumMedia,
        target: AlbumImageTarget,
    )

    /**
     * 加载保持完整画幅的预览大图。宿主应读取原始 URI 并按显示尺寸采样，避免读取已裁剪缩略图。
     * Coil 可用网格请求的 `placeholderMemoryCacheKey` 占位，大图使用独立缓存并在成功后替换小图。
     * 默认沿用 [load] 的 PREVIEW_IMAGE 分支，兼容已有宿主实现。
     */
    fun loadPreview(imageView: ImageView, media: AlbumMedia) {
        load(imageView, media, AlbumImageTarget.PREVIEW_IMAGE)
    }

    /** RecyclerView 回收或重新绑定时清理旧请求；默认仅移除 Drawable。 */
    fun clear(imageView: ImageView) {
        imageView.setImageDrawable(null)
    }
}
