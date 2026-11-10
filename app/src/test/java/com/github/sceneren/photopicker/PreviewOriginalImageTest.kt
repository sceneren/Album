package com.github.sceneren.photopicker

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import coil3.request.SuccessResult
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34], manifest = Config.NONE, application = Application::class)
class PreviewOriginalImageTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun rectangularOriginalReplacesSquarePlaceholderWithoutLosingEdges() = runTest {
        for ((width, height) in listOf(1200 to 600, 600 to 1200)) {
            val original = File.createTempFile("preview-original", ".png", context.cacheDir)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
                setPixel(0, 0, Color.GREEN)
                setPixel(width - 1, height - 1, Color.BLUE)
            }
            original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val provider = CroppingProvider(original)
            provider.attachInfo(context, ProviderInfo().apply { authority = "preview-original" })
            ShadowContentResolver.registerProviderInternal("preview-original", provider)
            val media = AlbumMedia(
                uri = Uri.parse("content://preview-original/$width-$height"), mediaType = AlbumMediaType.IMAGE,
                displayName = "rectangle.png", mimeType = "image/png", sizeBytes = original.length(),
                dateAddedEpochSeconds = null, dateModifiedEpochSeconds = 1,
                width = width, height = height, durationMillis = null, bucketId = null,
                bucketName = null, selectedAtEpochMillis = null, source = AlbumMediaSource.PHOTO_PICKER,
            )
            val loader = ImageLoader.Builder(context).build()
            val thumbnail = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).asImage()
            try {
                loader.memoryCache!![MemoryCache.Key(thumbnailCacheKey(media))] = MemoryCache.Value(thumbnail)
                var placeholder: coil3.Image? = null
                val request = albumImageRequest(context, media, isThumbnail = false, edge = 2048)
                    .newBuilder().target(onStart = { placeholder = it }).build()
                val result = loader.execute(request)
                assertTrue(result.toString(), result is SuccessResult)
                val output = (result as SuccessResult).image as BitmapImage
                assertSame(thumbnail, placeholder)
                assertEquals(width, output.width)
                assertEquals(height, output.height)
                assertEquals(Color.GREEN, output.bitmap.getPixel(0, 0))
                assertEquals(Color.BLUE, output.bitmap.getPixel(output.width - 1, output.height - 1))
                assertEquals(0, provider.thumbnailRequests)
                assertEquals(1, provider.originalRequests)
            } finally {
                loader.shutdown()
                original.delete()
            }
        }
    }

    /** Any sized provider asset is deliberately a square crop. Only the original has the full frame. */
    private class CroppingProvider(private val original: File) : ContentProvider() {
        var thumbnailRequests = 0
        var originalRequests = 0
        override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?): AssetFileDescriptor {
            val file = if (opts != null) {
                thumbnailRequests++
                File.createTempFile("cropped-thumbnail", ".png", context!!.cacheDir).apply {
                    outputStream().use { output ->
                        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also {
                            it.compress(Bitmap.CompressFormat.PNG, 100, output)
                            it.recycle()
                        }
                    }
                }
            } else {
                originalRequests++
                original
            }
            return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
        }
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
