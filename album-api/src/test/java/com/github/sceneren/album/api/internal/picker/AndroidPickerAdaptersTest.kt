package com.github.sceneren.album.api.internal.picker

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPickerAdaptersTest {
    @Test
    fun metadataReaderMapsImageVideoAndOptionalColumns() {
        ShadowContentResolver.registerProviderInternal("picker", PickerMetadataProvider())
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )

        val image = reader.read(uri("image"), reader.requiredType(uri("image")))
        val video = reader.read(uri("video"), reader.requiredType(uri("video")))

        assertEquals(AlbumMediaType.IMAGE, image.mediaType)
        assertEquals("picked-image", image.displayName)
        assertNull(image.durationMillis)
        assertEquals(AlbumMediaType.VIDEO, video.mediaType)
        assertEquals(2_000L, video.durationMillis)
    }

    @Test
    fun requiredTypeRejectsUnsupportedMime() {
        ShadowContentResolver.registerProviderInternal("picker", PickerMetadataProvider())
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )

        try {
            reader.requiredType(uri("other"))
            fail("Unsupported MIME should fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun releaseReadNeverRequestsWritePermission() {
        var releasedFlags: Int? = null
        val resolver = ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .contentResolver
        val grants = AndroidPersistableGrantManager(
            resolver = resolver,
            releasePermission = { _, flags -> releasedFlags = flags },
        )

        grants.releaseRead(uri("image"))

        assertEquals(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION, releasedFlags)
    }

    private class PickerMetadataProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String = when (uri.lastPathSegment) {
            "image" -> "image/jpeg"
            "video" -> "video/mp4"
            else -> "application/octet-stream"
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.Video.VideoColumns.DURATION,
            ),
        ).apply {
            val video = uri.lastPathSegment == "video"
            addRow(
                arrayOf<Any?>(
                    if (video) "picked-video" else "picked-image",
                    100L,
                    10,
                    20,
                    if (video) 2_000L else 0L,
                ),
            )
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private fun uri(name: String): Uri = Uri.parse("content://picker/$name")
}
