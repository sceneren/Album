package com.github.sceneren.photopicker

import android.app.Application
import android.widget.ImageView
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.rememberAsyncImagePainter
import coil3.load
import coil3.request.ImageRequest
import coil3.memory.MemoryCache
import coil3.BitmapImage
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import coil3.size.Scale
import coil3.size.Precision
import coil3.request.allowHardware
import coil3.decode.BitmapFactoryDecoder
import android.util.Log
import coil3.video.VideoFrameDecoder
import com.github.sceneren.album.ui.compose.AlbumImageLoader as ComposeImageLoader
import com.github.sceneren.album.ui.compose.AlbumImageTarget as ComposeImageTarget
import com.github.sceneren.album.ui.compose.AlbumUi as ComposeUi
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
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
                    imageView.load(media.uri, imageLoader) {
                        listener(onError = { _, result ->
                            Log.e("AlbumImageLoader", "View image load failed", result.throwable)
                        })
                        if (target == ViewImageTarget.GRID_THUMBNAIL) {
                            val key = thumbnailCacheKey(media)
                            memoryCacheKey(key)
                            placeholderMemoryCacheKey(key)
                            precision(Precision.INEXACT)
                            scale(Scale.FIT)
                            fetcherFactory(SystemThumbnailFetcher.Factory())
                        } else if (media.source == AlbumMediaSource.PHOTO_PICKER &&
                            media.mediaType == AlbumMediaType.IMAGE) {
                            fetcherFactory(PhotoPickerImageFetcher.Factory())
                            decoderFactory(BitmapFactoryDecoder.Factory())
                            allowHardware(false)
                        }
                    }
                }

                /** 清理 `clear` 对应的数据或资源。 */
                override fun clear(imageView: ImageView) {
                    imageView.load(null, imageLoader)
                }
            },
        )
        ComposeUi.setImageLoader { media, target ->
            val isGridThumbnail = target == ComposeImageTarget.GRID_THUMBNAIL
            // Explicit bounded size survives painter restart/backgrounding; a draw-scope
            // resolver can otherwise wait for a new draw while its image is still empty.
            val config = LocalConfiguration.current
            val density = LocalDensity.current.density
            val edge = (config.screenWidthDp * density / 4).toInt().coerceIn(128, 512)
            val previewEdge = (maxOf(config.screenWidthDp, config.screenHeightDp) * density)
                .toInt().coerceIn(512, 2048)
            val requestEdge = if (isGridThumbnail) edge else previewEdge
            val cacheKey = if (isGridThumbnail) thumbnailCacheKey(media) else null
            val model = remember(media.uri, media.dateModifiedEpochSeconds, requestEdge, isGridThumbnail) {
                ImageRequest.Builder(this)
                    .data(media.uri)
                    .apply {
                        size(requestEdge, requestEdge)
                        listener(onError = { _, result ->
                            Log.e("AlbumImageLoader", "Compose image load failed", result.throwable)
                        })
                        if (isGridThumbnail) {
                            memoryCacheKey(cacheKey)
                            placeholderMemoryCacheKey(cacheKey)
                            // System thumbnails preserve aspect ratio and can be smaller on
                            // one axis. Reuse them as thumbnails rather than re-fetching to fill.
                            precision(Precision.INEXACT)
                            scale(Scale.FIT)
                            fetcherFactory(SystemThumbnailFetcher.Factory())
                        } else if (media.source == AlbumMediaSource.PHOTO_PICKER &&
                            media.mediaType == AlbumMediaType.IMAGE) {
                            fetcherFactory(PhotoPickerImageFetcher.Factory())
                            decoderFactory(BitmapFactoryDecoder.Factory())
                            allowHardware(false)
                        }
                    }
                    .build()
            }
            // Lazy cells discard painters when scrolled out. Seed the very first frame
            // synchronously from Coil's bounded cache instead of drawing an empty painter
            // until its coroutine reports Success on the following frame.
            val cachedBitmap = cacheKey?.let { key ->
                (imageLoader.memoryCache?.get(MemoryCache.Key(key))?.image as? BitmapImage)?.bitmap
            }
            val cachedPainter = remember(cachedBitmap) {
                cachedBitmap?.let { BitmapPainter(it.asImageBitmap()) }
            }
            val asyncPainter = rememberAsyncImagePainter(
                model = model,
                imageLoader = imageLoader,
                placeholder = cachedPainter,
                contentScale = if (isGridThumbnail) ContentScale.Crop else ContentScale.Fit,
            )
            if (cachedPainter != null) cachedPainter else asyncPainter
        }
    }
}
