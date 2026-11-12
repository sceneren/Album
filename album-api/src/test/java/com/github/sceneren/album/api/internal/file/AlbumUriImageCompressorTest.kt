package com.github.sceneren.album.api.internal.file

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import top.zibin.luban.compression.Compressor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumUriImageCompressorTest {
    @Test fun decodesProviderUriDirectlyAndPublishesOnlyASafeCompressedImage() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = RuntimeEnvironment.getApplication()
            val original = File(context.cacheDir, "original.jpg")
            val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
            original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
            val provider = object : ContentProvider() {
                override fun onCreate() = true
                override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)
                override fun getType(uri: Uri) = "image/jpeg"
                override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
                override fun insert(uri: Uri, values: ContentValues?): Uri? = null
                override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
                override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            }
            provider.attachInfo(context, ProviderInfo().apply {
                authority = "uri.image"
                exported = true
                grantUriPermissions = true
                applicationInfo = context.applicationInfo
            })
            ShadowContentResolver.registerProviderInternal("uri.image", provider)
            val uri = Uri.parse("content://uri.image/media/a%2Fb.jpg")
            val folder = File(context.cacheDir, "compressed").apply { mkdirs() }
            // Test the real URI decoder and output transaction with a platform test encoder;
            // the production encoder remains Luban's native TurboJPEG implementation.
            val compressor = AlbumUriImageCompressor(object : Compressor {
                override fun compress(bitmap: Bitmap, targetSizeKb: Int?, fixedQuality: Int?): ByteArray =
                    ByteArrayOutputStream().use { bytes ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, fixedQuality ?: 60, bytes)
                        bytes.toByteArray()
                    }
            })
            var reserved = 0L
            val result = compressor.compress(context, uri, folder) { reserved = it }
            assertEquals(reserved, result.length())
            assertEquals(folder.canonicalFile, result.canonicalFile.parentFile)
            assertEquals(1, folder.listFiles()!!.size)
            assertTrue(original.isFile)
            val decoded = BitmapFactory.decodeFile(result.absolutePath)
            assertEquals(64, decoded.width)
            assertEquals(32, decoded.height)
            decoded.recycle()
            result.delete()
            assertTrue(runCatching { compressor.compress(context, uri, folder) { error("quota rejected") } }.isFailure)
            assertTrue(folder.listFiles()!!.isEmpty())
        }
    }
}
