package com.github.sceneren.album

import android.app.Application
import android.widget.ImageView
import coil3.ImageLoader
import coil3.compose.rememberAsyncImagePainter
import coil3.load
import coil3.video.VideoFrameDecoder
import com.github.sceneren.album.ui.compose.AlbumImageLoader as ComposeImageLoader
import com.github.sceneren.album.ui.compose.AlbumUi as ComposeUi
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.ui.view.AlbumImageLoader as ViewImageLoader
import com.github.sceneren.album.ui.view.AlbumImageTarget as ViewImageTarget
import com.github.sceneren.album.ui.view.AlbumUi as ViewUi

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val imageLoader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
        ViewUi.setImageLoader(
            object : ViewImageLoader {
                override fun load(
                    imageView: ImageView,
                    media: AlbumMedia,
                    target: ViewImageTarget,
                ) {
                    imageView.load(media.uri, imageLoader)
                }

                override fun clear(imageView: ImageView) {
                    imageView.load(null, imageLoader)
                }
            },
        )
        ComposeUi.setImageLoader(
            ComposeImageLoader { media, _ ->
                rememberAsyncImagePainter(
                    model = media.uri,
                    imageLoader = imageLoader,
                )
            },
        )
    }
}
