package com.github.sceneren.album.ui.compose

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread
import com.github.sceneren.album.video.AlbumVideoPlayerFactory

/** `album-ui-compose` 的进程级配置，建议在 Application.onCreate 中初始化。 */
object AlbumUi {
    @Volatile
    /** 表示 `configuredImageLoader` 对应的数据。 */
    private var configuredImageLoader: AlbumImageLoader? = null

    /** 更新 `setImageLoader` 对应的状态。 */
    fun setImageLoader(imageLoader: AlbumImageLoader) {
        configuredImageLoader = imageLoader
    }

    @Volatile
    private var configuredVideoPlayerFactory: AlbumVideoPlayerFactory? = null

    /** 可选的视频播放实现；传 null 恢复静态封面预览，不会要求宿主引入 Media3。 */
    fun setVideoPlayerFactory(factory: AlbumVideoPlayerFactory?) {
        configuredVideoPlayerFactory = factory
    }

    internal fun videoPlayerFactoryOrNull(): AlbumVideoPlayerFactory? = configuredVideoPlayerFactory

    @Volatile
    private var configuredToastHandler: AlbumToastHandler? = null

    /**
     * 可选的提示显示回调，建议在 Application.onCreate 中配置，避免捕获 Activity。
     * 完整页面及嵌入内容的 Toast 提示均在主线程将提示内容交给 [handler]；未设置或传 null 时使用系统短 Toast。
     */
    fun setToastHandler(handler: AlbumToastHandler?) {
        configuredToastHandler = handler
    }

    @MainThread
    internal fun showToast(context: Context, message: CharSequence): Toast? {
        val handler = configuredToastHandler
        if (handler != null) {
            handler.showToast(message)
            return null
        }
        return Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).also(Toast::show)
    }

    /** 执行 `requireImageLoader` 方法定义的处理。 */
    internal fun requireImageLoader(): AlbumImageLoader = checkNotNull(configuredImageLoader) {
        "请先调用 AlbumUi.setImageLoader 配置宿主图片加载器"
    }
}
