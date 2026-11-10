package com.github.sceneren.photopicker

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import coil3.ImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.SuccessResult
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.ui.view.AlbumImageTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 34], manifest = Config.NONE, application = Application::class)
class PreviewPlaceholderTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val media = AlbumMedia(
        uri = Uri.parse("content://preview-test/1"), mediaType = AlbumMediaType.IMAGE,
        displayName = "photo.jpg", mimeType = "image/jpeg", sizeBytes = 100,
        dateAddedEpochSeconds = null, dateModifiedEpochSeconds = 1,
        width = 800, height = 600, durationMillis = null, bucketId = null,
        bucketName = null, selectedAtEpochMillis = null, source = AlbumMediaSource.PHOTO_PICKER,
    )

    @Test
    fun previewUsesThumbnailOnlyAsPlaceholderAndKeepsIndependentCacheValidation() {
        val thumbnail = albumImageRequest(context, media, isThumbnail = true, edge = 128)
        val preview = albumImageRequest(context, media, isThumbnail = false, edge = 1024)
        assertEquals(thumbnailCacheKey(media), thumbnail.memoryCacheKey)
        assertEquals(thumbnail.memoryCacheKey, preview.placeholderMemoryCacheKey?.key)
        assertEquals(previewCacheKey(media), preview.memoryCacheKey)
        assertEquals(coil3.size.Scale.FIT, preview.scale)
        assertNotEquals(thumbnail.memoryCacheKey, preview.memoryCacheKey)
        assertNotEquals(thumbnailCacheKey(media), thumbnailCacheKey(media.copy(sizeBytes = 101)))
    }

    @Test
    fun viewImageAndVideoPreviewTargetsAreSeededSynchronouslyAndNeverReuseAnotherUri() {
        val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
        val loader = ImageLoader.Builder(context).build()
        try {
            loader.memoryCache!![MemoryCache.Key(thumbnailCacheKey(media))] = MemoryCache.Value(bitmap.asImage())
            val hostLoader = createAlbumViewImageLoader(loader)
            val view = ImageView(context)
            for (target in listOf(AlbumImageTarget.GRID_THUMBNAIL, AlbumImageTarget.PREVIEW_IMAGE, AlbumImageTarget.VIDEO_COVER)) {
                hostLoader.load(view, media, target)
                assertSame(bitmap, (view.drawable as BitmapDrawable).bitmap)
            }
            hostLoader.load(view, media.copy(uri = Uri.parse("content://preview-test/2")), AlbumImageTarget.PREVIEW_IMAGE)
            assertNull(view.drawable)
            hostLoader.clear(view)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun delayedOriginalStartsWithThumbnailAndCompletesWithFullImage() = runTest {
        val thumbnail = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).asImage()
        val original = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).asImage()
        val releaseOriginal = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                started.complete(Unit)
                releaseOriginal.await()
                SuccessResult(original, chain.request)
            })
        }.build()
        try {
            loader.memoryCache!![MemoryCache.Key(thumbnailCacheKey(media))] = MemoryCache.Value(thumbnail)
            var placeholder: coil3.Image? = null
            val request = albumImageRequest(context, media, isThumbnail = false, edge = 1024)
                .newBuilder().target(onStart = { placeholder = it }).build()
            val pending = async { loader.execute(request) }
            started.await()
            assertSame(thumbnail, placeholder)
            releaseOriginal.complete(Unit)
            assertSame(original, (pending.await() as SuccessResult).image)
        } finally {
            loader.shutdown()
        }
    }
}
