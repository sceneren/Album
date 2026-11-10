package com.github.sceneren.photopicker

import android.content.ContentResolver
import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.util.Size
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.toAndroidUri
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Grid only: ask the system for a thumbnail before considering the original content. */
internal class SystemThumbnailFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val width = (options.size.width as? Dimension.Pixels)?.px
        val height = (options.size.height as? Dimension.Pixels)?.px
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width != null && height != null) {
            val bitmap = loadThumbnail(options.context.contentResolver, data.toAndroidUri(), Size(width, height))
            if (bitmap != null) {
                return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
            }
        }
        // No thumbnail exists (or the provider doesn't implement thumbnails). Return the
        // size-bounded original stream for images; let Coil's video fetcher handle videos.
        return if (options.context.contentResolver.getType(data.toAndroidUri())?.startsWith("image/") == true) {
            PhotoPickerImageFetcher(data, options).fetch()
        } else {
            null
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "content") SystemThumbnailFetcher(data, options) else null
    }

    companion object {
        // Thumbnail Binder calls must never run on Main or compete with session cancellation.
        private val executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "album-system-thumbnail").apply { isDaemon = true }
        }

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
        internal suspend fun loadThumbnail(
            resolver: ContentResolver,
            uri: android.net.Uri,
            size: Size,
        ): Bitmap? = suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            val future = executor.submit {
                try {
                    if (!continuation.isActive) return@submit
                    val bitmap = try {
                        resolver.loadThumbnail(uri, size, signal)
                    } catch (_: IOException) {
                        null
                    } catch (_: SecurityException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    } catch (_: UnsupportedOperationException) {
                        null
                    } catch (_: NullPointerException) {
                        // The framework dereferences a null asset returned by some providers.
                        null
                    }
                    continuation.resume(bitmap) { _, value, _ -> value?.recycle() }
                } catch (exception: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }
            continuation.invokeOnCancellation {
                signal.cancel()
                future.cancel(true)
            }
        }
    }
}
