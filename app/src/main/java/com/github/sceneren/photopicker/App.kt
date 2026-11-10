package com.github.sceneren.photopicker

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.BitmapImage
import coil3.Image
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.BitmapFactoryDecoder
import coil3.load
import coil3.memory.MemoryCache
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.target.ImageViewTarget
import coil3.size.Precision
import coil3.size.Scale
import coil3.video.VideoFrameDecoder
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.ui.compose.AlbumImageTarget as ComposeImageTarget
import com.github.sceneren.album.ui.compose.AlbumUi as ComposeUi
import com.github.sceneren.album.ui.view.AlbumImageLoader as ViewImageLoader
import com.github.sceneren.album.ui.view.AlbumImageTarget as ViewImageTarget
import com.github.sceneren.album.ui.view.AlbumUi as ViewUi
import com.github.sceneren.album.video.media3.Media3AlbumVideoPlayerFactory
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 负责宿主图片加载及可选视频引擎的初始化。 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val videoPlayerFactory = Media3AlbumVideoPlayerFactory()
        ViewUi.setVideoPlayerFactory(videoPlayerFactory)
        ComposeUi.setVideoPlayerFactory(videoPlayerFactory)
        val imageLoader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
        ViewUi.setImageLoader(createAlbumViewImageLoader(imageLoader))
        ComposeUi.setImageLoader { media, target ->
            // Fixed bounded dimensions also allow requests to start before a painter is drawn.
            val windowSize = LocalWindowInfo.current.containerSize
            val thumbnailEdge = (windowSize.width / 4).coerceIn(128, 512)
            val previewEdge = maxOf(windowSize.width, windowSize.height).coerceIn(512, 2048)
            rememberAlbumPainter(
                context = this,
                media = media,
                isThumbnail = target == ComposeImageTarget.GRID_THUMBNAIL,
                imageLoader = imageLoader,
                thumbnailEdge = thumbnailEdge,
                previewEdge = previewEdge,
            )
        }
    }
}

/** The same thumbnail key seeds grid cells, image previews and video covers. */
internal fun createAlbumViewImageLoader(imageLoader: ImageLoader): ViewImageLoader =
    object : ViewImageLoader {
        // Neither the registry keys nor values retain recycled Views/Activities.
        private val targets = WeakHashMap<ImageView, WeakReference<AlbumViewImageTarget>>()

        override fun load(imageView: ImageView, media: AlbumMedia, target: ViewImageTarget) {
            targets.remove(imageView)?.get()?.dispose()
            val cached = imageLoader.memoryCache?.get(MemoryCache.Key(thumbnailCacheKey(media)))?.image
            val isThumbnail = target == ViewImageTarget.GRID_THUMBNAIL
            val output = AlbumViewImageTarget(imageView, media, imageLoader, cached, !isThumbnail)
            targets[imageView] = WeakReference(output)
            // Coil starts asynchronously. Seed the target before even its onStart callback.
            imageView.setImageDrawable(cached?.asDrawable(imageView.resources))
            val metrics = imageView.resources.displayMetrics
            val viewportEdge = maxOf(imageView.rootView.width, imageView.rootView.height)
                .takeIf { it > 0 } ?: maxOf(metrics.widthPixels, metrics.heightPixels)
            // Cells retain Coil's View size resolver; previews get a bounded window-sized decode.
            val edge = if (isThumbnail) null else viewportEdge.coerceIn(512, 2048)
            val request = albumImageRequest(imageView.context, media, isThumbnail, edge, cached)
                .newBuilder().target(output).build()
            imageLoader.enqueue(request)
        }

        override fun clear(imageView: ImageView) {
            targets.remove(imageView)?.get()?.dispose()
            imageView.load(null, imageLoader)
            imageView.setImageDrawable(null)
        }
    }

/** Own the optional small request with the same View/lifecycle as the original. */
private class AlbumViewImageTarget(
    view: ImageView,
    private val media: AlbumMedia,
    private val imageLoader: ImageLoader,
    private var thumbnail: Image?,
    private val loadThumbnail: Boolean,
) : ImageViewTarget(view), View.OnAttachStateChangeListener {
    private var thumbnailRequest: Disposable? = null
    private var generation = 0
    private var originalLoaded = false
    private var disposed = false
    private var thumbnailLifecycle: Lifecycle? = null
    // Keep auxiliary ownership independent from Coil's observer registration. Coil can
    // replace/restart the ImageViewTarget delegate when the same View is reused.
    private val thumbnailObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { startThumbnail() }
        override fun onStop(owner: LifecycleOwner) { cancelThumbnail() }
        override fun onDestroy(owner: LifecycleOwner) { dispose() }
    }

    init { view.addOnAttachStateChangeListener(this) }

    private fun bindThumbnailLifecycle() {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle ?: view.context.findLifecycle()
        if (thumbnailLifecycle === lifecycle) return
        thumbnailLifecycle?.removeObserver(thumbnailObserver)
        thumbnailLifecycle = lifecycle
        lifecycle?.addObserver(thumbnailObserver)
    }

    override fun onStart(placeholder: Image?) {
        originalLoaded = false
        if (!disposed) {
            super.onStart(placeholder ?: thumbnail)
            bindThumbnailLifecycle()
            startThumbnail()
        }
    }

    private fun startThumbnail() {
        if (!loadThumbnail || thumbnail != null || thumbnailRequest != null || disposed || originalLoaded ||
            !view.isAttachedToWindow || thumbnailLifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == false) return
        val requestGeneration = ++generation
        val edge = (view.resources.displayMetrics.widthPixels / 4).coerceIn(128, 512)
        val request = albumImageRequest(view.context, media, isThumbnail = true, edge = edge)
            .newBuilder().target(onSuccess = { result ->
                if (!disposed && !originalLoaded && generation == requestGeneration) {
                    thumbnail = result
                    updateImage(result)
                }
            }).build()
        thumbnailRequest = imageLoader.enqueue(request)
    }

    override fun onSuccess(result: Image) {
        if (disposed) return
        originalLoaded = true
        cancelThumbnail()
        super.onSuccess(result)
    }

    override fun onError(error: Image?) {
        if (!disposed) super.onError(thumbnail ?: error)
    }

    override fun onViewAttachedToWindow(v: View) { bindThumbnailLifecycle() }
    override fun onViewDetachedFromWindow(v: View) { cancelThumbnail() }

    private fun cancelThumbnail() {
        generation++
        thumbnailRequest?.dispose()
        thumbnailRequest = null
    }

    fun dispose() {
        disposed = true
        cancelThumbnail()
        view.removeOnAttachStateChangeListener(this)
        thumbnailLifecycle?.removeObserver(thumbnailObserver)
        thumbnailLifecycle = null
    }
}

private fun Context.findLifecycle(): Lifecycle? {
    var current = this
    while (true) {
        if (current is LifecycleOwner) return current.lifecycle
        if (current !is ContextWrapper || current.baseContext === current) return null
        current = current.baseContext
    }
}

/** Keep preview cache validation separate: a thumbnail is a placeholder, never a full-size hit. */
@OptIn(ExperimentalCoilApi::class)
internal fun albumImageRequest(
    context: Context,
    media: AlbumMedia,
    isThumbnail: Boolean,
    edge: Int? = null,
    placeholder: Image? = null,
): ImageRequest = ImageRequest.Builder(context)
    .data(media.uri)
    .placeholderMemoryCacheKey(thumbnailCacheKey(media))
    .placeholder(placeholder)
    .error(placeholder)
    .apply {
        if (edge != null) size(edge, edge)
        listener(onError = { _, result ->
            Log.e("AlbumImageLoader", "Image load failed", result.throwable)
        })
        if (isThumbnail) {
            memoryCacheKey(thumbnailCacheKey(media))
            precision(Precision.INEXACT)
            scale(Scale.FIT)
            fetcherFactory(SystemThumbnailFetcher.Factory())
        } else {
            memoryCacheKey(previewCacheKey(media))
            scale(Scale.FIT)
            precision(Precision.INEXACT)
            if (media.mediaType == AlbumMediaType.IMAGE) {
                fetcherFactory(PhotoPickerImageFetcher.Factory(preferOriginal = true))
                if (media.source == AlbumMediaSource.PHOTO_PICKER) {
                    decoderFactory(BitmapFactoryDecoder.Factory())
                    allowHardware(false)
                }
            }
        }
    }
    .build()

/** Draw cached thumbnails immediately, but always promote successful preview originals. */
@Composable
internal fun rememberAlbumPainter(
    context: Context,
    media: AlbumMedia,
    isThumbnail: Boolean,
    imageLoader: ImageLoader,
    thumbnailEdge: Int,
    previewEdge: Int,
): Painter = key(thumbnailCacheKey(media), isThumbnail) {
    val cacheKey = thumbnailCacheKey(media)
    val cached = remember(imageLoader, cacheKey) {
        imageLoader.memoryCache?.get(MemoryCache.Key(cacheKey))?.image
    }
    val cachedPainter = remember(cached) {
        (cached as? BitmapImage)?.bitmap?.let { BitmapPainter(it.asImageBitmap()) }
    }
    val requestEdge = if (isThumbnail) thumbnailEdge else previewEdge
    val request = remember(context, media, isThumbnail, requestEdge, cached) {
        albumImageRequest(context, media, isThumbnail, requestEdge, cached)
    }
    val asyncPainter = rememberAsyncImagePainter(
        model = request,
        imageLoader = imageLoader,
        placeholder = cachedPainter,
        error = cachedPainter,
        contentScale = if (isThumbnail) ContentScale.Crop else ContentScale.Fit,
    )
    val state by asyncPainter.state.collectAsState()
    // Cache eviction or selected-only previews can lack a grid bitmap. Load a small
    // thumbnail concurrently, never blocking the full request or decoding an unbounded image.
    val fallback = if (!isThumbnail && cachedPainter == null && state !is AsyncImagePainter.State.Success) {
        val thumbnailRequest = remember(context, media, thumbnailEdge) {
            albumImageRequest(context, media, isThumbnail = true, edge = thumbnailEdge)
        }
        val thumbnailPainter = rememberAsyncImagePainter(thumbnailRequest, imageLoader)
        val thumbnailState by thumbnailPainter.state.collectAsState()
        thumbnailState.painter
    } else {
        cachedPainter
    }
    when {
        state is AsyncImagePainter.State.Success -> asyncPainter
        fallback != null -> fallback
        else -> asyncPainter
    }
}
