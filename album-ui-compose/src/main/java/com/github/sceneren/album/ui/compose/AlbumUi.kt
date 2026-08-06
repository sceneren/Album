package com.github.sceneren.album.ui.compose

/** `album-ui-compose` 的进程级配置，建议在 Application.onCreate 中初始化。 */
object AlbumUi {
    @Volatile
    /** 表示 `configuredImageLoader` 对应的数据。 */
    private var configuredImageLoader: AlbumImageLoader? = null

    /** 更新 `setImageLoader` 对应的状态。 */
    fun setImageLoader(imageLoader: AlbumImageLoader) {
        configuredImageLoader = imageLoader
    }

    /** 执行 `requireImageLoader` 方法定义的处理。 */
    internal fun requireImageLoader(): AlbumImageLoader = checkNotNull(configuredImageLoader) {
        "请先调用 AlbumUi.setImageLoader 配置宿主图片加载器"
    }
}
