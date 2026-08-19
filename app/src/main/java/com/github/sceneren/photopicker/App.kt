package com.github.sceneren.photopicker

import android.app.Application
import android.widget.ImageView
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.rememberDrawScopeSizeResolver
import coil3.compose.rememberAsyncImagePainter
import coil3.load
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.github.sceneren.album.ui.compose.AlbumImageLoader as ComposeImageLoader
import com.github.sceneren.album.ui.compose.AlbumImageTarget as ComposeImageTarget
import com.github.sceneren.album.ui.compose.AlbumUi as ComposeUi
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.ui.view.AlbumImageLoader as ViewImageLoader
import com.github.sceneren.album.ui.view.AlbumImageTarget as ViewImageTarget
import com.github.sceneren.album.ui.view.AlbumUi as ViewUi

/** 负责 `App` 相关的数据与行为。 */
class App : Application() {
    @OptIn(ExperimentalCoilApi::class)
    /** 处理 `onCreate` 回调。 */
    override fun onCreate() {
        super.onCreate()
        val imageLoader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
        ViewUi.setImageLoader(
            object : ViewImageLoader {
                /** 获取 `load` 所需的数据。 */
                override fun load(
                    imageView: ImageView,
                    media: AlbumMedia,
                    target: ViewImageTarget,
                ) {
                    imageView.load(media.uri, imageLoader)
                }

                /** 清理 `clear` 对应的数据或资源。 */
                override fun clear(imageView: ImageView) {
                    imageView.load(null, imageLoader)
                }
            },
        )
        ComposeUi.setImageLoader { media, target ->
            val isGridThumbnail = target == ComposeImageTarget.GRID_THUMBNAIL
            val model = if (isGridThumbnail) {
                val sizeResolver = rememberDrawScopeSizeResolver()
                remember(media.uri, media.dateModifiedEpochSeconds, sizeResolver) {
                    ImageRequest.Builder(this)
                        .data(media.uri)
                        .size(sizeResolver)
                        .build()
                }
            } else {
                media.uri
            }
            rememberAsyncImagePainter(
                model = model,
                imageLoader = imageLoader,
                contentScale = if (isGridThumbnail) ContentScale.Crop else ContentScale.Fit,
            )
        }
    }
}
