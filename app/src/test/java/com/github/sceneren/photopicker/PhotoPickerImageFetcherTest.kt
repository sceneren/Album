package com.github.sceneren.photopicker

import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Size
import coil3.toCoilUri
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 29, 34], manifest = Config.NONE, application = Application::class)
class PhotoPickerImageFetcherTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()
    private val uri = Uri.parse("content://image-provider/picked/image")
    private lateinit var provider: ImageProvider

    @Before
    fun registerProvider() {
        provider = ImageProvider(File.createTempFile("image-provider", ".bin", context.cacheDir).apply {
            writeText("prefix-selected-image-trailer")
        })
        provider.attachInfo(context, ProviderInfo().apply { authority = "image-provider" })
        ShadowContentResolver.registerProviderInternal("image-provider", provider)
    }

    @Test
    fun supportedSizeRequestDoesNotOpenOriginal() = runTest {
        val result = fetch()

        assertEquals("image/jpeg", result.mimeType)
        assertEquals(DataSource.DISK, result.dataSource)
        assertEquals(listOf(Point(128, 96)), provider.requestedSizes)
        assertEquals(listOf("image/*"), provider.mimeFilters)
        assertEquals(0, provider.originalRequests)
        assertTrue(provider.assets.single().parcelFileDescriptor.fileDescriptor.valid())
        assertContentAndClose(result)
        assertFalse(provider.assets.single().parcelFileDescriptor.fileDescriptor.valid())
    }

    @Test
    fun nullSizedAssetFallsBackToOriginal() = runTest {
        provider.sizedReturnsNull = true

        assertContentAndClose(fetch())

        assertOriginalFallback()
    }

    @Test
    fun missingSizedAssetFallsBackToOriginal() = runTest {
        provider.sizedFailure = FileNotFoundException("No thumbnail")

        assertContentAndClose(fetch())

        assertOriginalFallback()
    }

    @Test
    fun sizedIoFailureFallsBackToOriginal() = runTest {
        provider.sizedFailure = IOException("Thumbnail unavailable")

        assertContentAndClose(fetch())

        assertOriginalFallback()
    }

    @Test
    fun rejectedSizeFallsBackToOriginal() = runTest {
        provider.sizedFailure = IllegalArgumentException("Size hint unsupported")

        assertContentAndClose(fetch())

        assertOriginalFallback()
    }

    @Test
    fun unsupportedTypedRequestFallsBackToOriginal() = runTest {
        provider.sizedFailure = UnsupportedOperationException("Thumbnails unsupported")

        assertContentAndClose(fetch())

        assertOriginalFallback()
    }

    @Test
    @Config(shadows = [DirectAssetResolver::class])
    fun failedSizedStreamClosesAssetBeforeOpeningOriginal() = runTest {
        provider.sizedStreamFailure = IOException("Unable to create sized stream")

        assertContentAndClose(fetch())

        assertOriginalFallback()
        assertTrue(provider.closedBeforeOriginal)
        assertEquals(2, provider.assets.size)
        assertTrue(provider.assets.all { it.closed })
    }

    @Test
    fun originalSizeRequestSkipsSizeHint() = runTest {
        assertContentAndClose(fetch(Size.ORIGINAL))

        assertTrue(provider.requestedSizes.isEmpty())
        assertEquals(listOf("*/*"), provider.mimeFilters)
        assertEquals(1, provider.originalRequests)
    }

    @Test
    fun previewSkipsProviderThumbnailEvenWithSquareDecodeSize() = runTest {
        val options = options(Size(1024, 1024))
        assertContentAndClose(PhotoPickerImageFetcher(uri.toCoilUri(), options, preferOriginal = true).fetch())

        assertTrue(provider.requestedSizes.isEmpty())
        assertEquals(1, provider.originalRequests)
        assertEquals(Size(1024, 1024), options.size)
    }

    @Test
    fun permissionFailureIsNotRetriedAsOriginal() = runTest {
        val failure = SecurityException("Read grant revoked")
        provider.sizedFailure = failure

        assertSame(failure, runCatching { fetch() }.exceptionOrNull())
        assertEquals(0, provider.originalRequests)
    }

    @Test
    fun operationCancellationIsNotRetriedAsOriginal() = runTest {
        val failure = OperationCanceledException()
        provider.sizedFailure = failure

        assertSame(failure, runCatching { fetch() }.exceptionOrNull())
        assertEquals(0, provider.originalRequests)
    }

    @Test
    fun coroutineCancellationIsNotRetriedAsOriginal() = runTest {
        val failure = CancellationException("Request cancelled")
        provider.sizedFailure = failure

        assertSame(failure, runCatching { fetch() }.exceptionOrNull())
        assertEquals(0, provider.originalRequests)
    }

    @Test
    fun cancelledJobDoesNotStartOriginalAfterSizedFailure() = runTest {
        val request = async {
            val requestJob = currentCoroutineContext().job
            provider.onSizedOpen = { requestJob.cancel() }
            provider.sizedFailure = IOException("Size request interrupted")
            fetch()
        }

        assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
        assertEquals(0, provider.originalRequests)
        assertTrue(provider.assets.isEmpty())
    }

    @Test
    fun cancelledJobClosesSizedStreamBeforeHandoff() = runTest {
        val request = async {
            val requestJob = currentCoroutineContext().job
            provider.onSizedOpen = { requestJob.cancel() }
            fetch()
        }

        assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
        assertEquals(0, provider.originalRequests)
        assertFalse(provider.assets.single().parcelFileDescriptor.fileDescriptor.valid())
    }

    @Test
    fun cancelledJobClosesOriginalStreamBeforeHandoff() = runTest {
        provider.sizedReturnsNull = true
        val request = async {
            val requestJob = currentCoroutineContext().job
            provider.onOriginalOpen = { requestJob.cancel() }
            fetch()
        }

        assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, provider.originalRequests)
        assertFalse(provider.assets.single().parcelFileDescriptor.fileDescriptor.valid())
    }

    @Test
    @Config(shadows = [DirectAssetResolver::class])
    fun failedStreamKeepsPrimaryFailureWhenAssetCloseAlsoFails() = runTest {
        provider.sizedReturnsNull = true
        val failure = IOException("Unable to create original stream")
        val closeFailure = IOException("Asset close failed")
        provider.originalStreamFailure = failure
        provider.closeFailure = closeFailure

        assertSame(failure, runCatching { fetch() }.exceptionOrNull())
        assertEquals(listOf(closeFailure), failure.suppressed.toList())
        assertTrue(provider.assets.single().closed)
        assertFalse(provider.assets.single().parcelFileDescriptor.fileDescriptor.valid())
    }

    @Test
    fun unavailableOriginalFailsWithoutRepeatedRetries() = runTest {
        provider.sizedReturnsNull = true
        provider.originalReturnsNull = true

        assertTrue(runCatching { fetch() }.exceptionOrNull() is IOException)
        assertOriginalFallback()
    }

    @Test
    fun originalOpenFailureIsPropagated() = runTest {
        provider.sizedReturnsNull = true
        val failure = FileNotFoundException("Original removed")
        provider.originalFailure = failure

        assertSame(failure, runCatching { fetch() }.exceptionOrNull())
        assertOriginalFallback()
    }

    @Test
    @Config(shadows = [DirectAssetResolver::class])
    fun failedOriginalStreamClosesAsset() = runTest {
        provider.sizedReturnsNull = true
        val failure = IOException("Unable to create original stream")
        provider.originalStreamFailure = failure

        assertSame(failure, runCatching { fetch() }.exceptionOrNull())
        assertTrue(provider.assets.single().closed)
        assertOriginalFallback()
    }

    @Test
    fun systemThumbnailFailureStillReadsOriginalOnAllSupportedApiLevels() = runTest {
        provider.sizedFailure = UnsupportedOperationException("Size hint unsupported")
        val result = SystemThumbnailFetcher(uri.toCoilUri(), options()).fetch()

        assertTrue(result is SourceFetchResult)
        assertContentAndClose(result as SourceFetchResult)
        assertEquals(if (Build.VERSION.SDK_INT >= 29) 2 else 1, provider.requestedSizes.size)
        assertEquals(1, provider.originalRequests)
    }

    @Test
    fun nullSystemThumbnailStillReadsOriginal() = runTest {
        provider.sizedReturnsNull = true
        val result = SystemThumbnailFetcher(uri.toCoilUri(), options()).fetch()

        assertTrue(result is SourceFetchResult)
        assertContentAndClose(result as SourceFetchResult)
        assertEquals(1, provider.originalRequests)
    }

    @Test
    fun systemOriginalSizeRequestDoesNotSkipImageFallback() = runTest {
        val result = SystemThumbnailFetcher(uri.toCoilUri(), options(Size.ORIGINAL)).fetch()

        assertTrue(result is SourceFetchResult)
        assertContentAndClose(result as SourceFetchResult)
        assertTrue(provider.requestedSizes.isEmpty())
        assertEquals(1, provider.originalRequests)
    }

    @Test
    fun videoFallbackRemainsDelegatedToCoil() = runTest {
        provider.mimeType = "video/mp4"
        provider.sizedFailure = UnsupportedOperationException("No video thumbnail")

        assertNull(SystemThumbnailFetcher(uri.toCoilUri(), options()).fetch())
        assertEquals(0, provider.originalRequests)
    }

    private fun options(size: Size = Size(128, 96)) = Options(context = context, size = size)

    private suspend fun fetch(size: Size = Size(128, 96)): SourceFetchResult =
        PhotoPickerImageFetcher(uri.toCoilUri(), options(size)).fetch()

    private fun assertContentAndClose(result: SourceFetchResult) {
        result.source.use {
            // Both sized and original assets are subsections of a larger file.
            assertEquals("selected-image", it.source().readUtf8())
        }
    }

    private fun assertOriginalFallback() {
        assertEquals(listOf(Point(128, 96)), provider.requestedSizes)
        assertEquals(listOf("image/*", "*/*"), provider.mimeFilters)
        assertEquals(1, provider.originalRequests)
    }

    private class ImageProvider(private val file: File) : ContentProvider() {
        var mimeType = "image/jpeg"
        var sizedReturnsNull = false
        var originalReturnsNull = false
        var sizedFailure: Exception? = null
        var originalFailure: Exception? = null
        var sizedStreamFailure: IOException? = null
        var originalStreamFailure: IOException? = null
        var closeFailure: IOException? = null
        var onSizedOpen: (() -> Unit)? = null
        var onOriginalOpen: (() -> Unit)? = null
        var originalRequests = 0
        var closedBeforeOriginal = false
        val requestedSizes = mutableListOf<Point>()
        val mimeFilters = mutableListOf<String>()
        val assets = mutableListOf<TrackingAsset>()

        override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?): AssetFileDescriptor? {
            mimeFilters += mimeTypeFilter
            @Suppress("DEPRECATION")
            val size = opts?.getParcelable<Point>(ContentResolver.EXTRA_SIZE)
            if (size != null) {
                requestedSizes += size
                onSizedOpen?.invoke()
                sizedFailure?.let { throw it }
                if (sizedReturnsNull) return null
            } else {
                // The resolver's regular read API uses */* with no size options.
                assertNull(opts)
                originalRequests++
                closedBeforeOriginal = assets.all { it.closed }
                onOriginalOpen?.invoke()
                originalFailure?.let { throw it }
                if (originalReturnsNull) return null
            }
            return TrackingAsset(
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                failure = if (size != null) sizedStreamFailure else originalStreamFailure,
                closeFailure = closeFailure,
            ).also { assets += it }
        }

        override fun onCreate() = true
        override fun getType(uri: Uri) = mimeType
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    /** Bypass only the resolver's AFD re-wrapping to inject stream/close failures. */
    @Implements(ContentResolver::class)
    class DirectAssetResolver : ShadowContentResolver() {
        @Implementation
        fun openTypedAssetFileDescriptor(uri: Uri, mimeType: String, opts: Bundle?): AssetFileDescriptor? =
            getProvider(uri)!!.openTypedAssetFile(uri, mimeType, opts)

        @Implementation
        fun openAssetFileDescriptor(uri: Uri, mode: String): AssetFileDescriptor? {
            assertEquals("r", mode)
            return getProvider(uri)!!.openTypedAssetFile(uri, "*/*", null)
        }
    }

    private class TrackingAsset(
        descriptor: ParcelFileDescriptor,
        private val failure: IOException?,
        private val closeFailure: IOException?,
    ) : AssetFileDescriptor(descriptor, "prefix-".length.toLong(), "selected-image".length.toLong()) {
        var closed = false

        override fun createInputStream(): FileInputStream {
            failure?.let { throw it }
            return super.createInputStream()
        }

        override fun close() {
            closed = true
            super.close()
            closeFailure?.let { throw it }
        }
    }
}
