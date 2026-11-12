package com.github.sceneren.album.api.internal.picker

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AndroidPickerAdaptersTest` 覆盖的行为。 */
class AndroidPickerAdaptersTest {
    @Test
    fun deletedPhotoCannotBeRecreatedFromCachedMimeAfterInvalidation() {
        val provider = PickerMetadataProvider()
        ShadowContentResolver.registerProviderInternal("picker", provider)
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )
        val imageUri = uri("image")
        reader.read(imageUri, reader.requiredType(imageUri))
        provider.deleted = true
        reader.invalidateCache()

        assertThrows(java.io.FileNotFoundException::class.java) {
            reader.read(imageUri, reader.requiredType(imageUri))
        }
        assertEquals(2, provider.typeQueries)
    }

    @Test
    fun revokedPhotoPermissionIsNotConvertedIntoAnEmptyMetadataItem() {
        val provider = PickerMetadataProvider()
        ShadowContentResolver.registerProviderInternal("picker", provider)
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )
        provider.denied = true
        assertThrows(SecurityException::class.java) {
            reader.read(uri("image"), AlbumMediaType.IMAGE)
        }
    }
    @Test
    /** 验证 `metadataReaderMapsImageVideoAndOptionalColumns` 所描述的场景。 */
    fun metadataReaderMapsImageVideoAndOptionalColumns() {
        ShadowContentResolver.registerProviderInternal("picker", PickerMetadataProvider())
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )

        val image = reader.read(uri("image"), reader.requiredType(uri("image")))
        val video = reader.read(uri("video"), reader.requiredType(uri("video")))
        val motion = reader.read(uri("motion"), reader.requiredType(uri("motion")))

        assertEquals(AlbumMediaType.IMAGE, image.mediaType)
        assertEquals("picked-image", image.displayName)
        assertNull(image.durationMillis)
        assertEquals(AlbumMediaType.VIDEO, video.mediaType)
        assertEquals(2_000L, video.durationMillis)
        assertEquals(AlbumMediaSpecialFormat.MOTION_PHOTO, motion.specialFormat)
    }

    @Test
    fun jpegHeicAndHeifMetadataNeverOpenImageContent() {
        val provider = PickerMetadataProvider().apply { exposesSpecialFormat = false }
        ShadowContentResolver.registerProviderInternal("picker", provider)
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )

        for (name in listOf("image", "heic", "heif")) {
            val imageUri = uri(name)
            val media = reader.read(imageUri, reader.requiredType(imageUri))
            assertEquals(100L, media.sizeBytes)
            assertEquals(AlbumMediaSpecialFormat.NONE, media.specialFormat)
            assertEquals(media, reader.readFresh(imageUri))
        }

        assertEquals(0, provider.contentOpens)
        assertEquals(false, provider.lastProjection!!.any {
            it in setOf(MediaStore.MediaColumns.XMP, "_special_format", "standard_mime_type_extension")
        })
    }

    @Test
    fun providerMotionMetadataCannotTagAnOrdinaryJpeg() {
        val provider = PickerMetadataProvider()
        ShadowContentResolver.registerProviderInternal("picker", provider)
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )

        val media = reader.read(uri("tagged-jpeg"), AlbumMediaType.IMAGE)

        assertEquals("picked-image.jpg", media.displayName)
        assertEquals(AlbumMediaSpecialFormat.NONE, media.specialFormat)
        assertEquals(0, provider.contentOpens)
    }

    @Test
    /** 验证 `requiredTypeRejectsUnsupportedMime` 所描述的场景。 */
    fun requiredTypeRejectsUnsupportedMime() {
        ShadowContentResolver.registerProviderInternal("picker", PickerMetadataProvider())
        val reader = ContentResolverUriMetadataReader(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        )

        try {
            reader.requiredType(uri("other"))
            fail("Unsupported MIME should fail")
        } catch (_: IllegalArgumentException) {
            // 预期会抛出异常。
        }
    }

    @Test
    /** 验证 `releaseReadNeverRequestsWritePermission` 所描述的场景。 */
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

    /** 负责 `PickerMetadataProvider` 相关的数据与行为。 */
    private class PickerMetadataProvider : ContentProvider() {
        var deleted = false
        var denied = false
        var typeQueries = 0
        var exposesSpecialFormat = true
        var contentOpens = 0
        var lastProjection: Array<out String>? = null
        /** 处理 `onCreate` 回调。 */
        override fun onCreate(): Boolean = true

        /** 获取 `getType` 所需的数据。 */
        override fun getType(uri: Uri): String {
            typeQueries++
            return when (uri.lastPathSegment) {
                "image", "motion", "tagged-jpeg" -> "image/jpeg"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                "video" -> "video/mp4"
                else -> "application/octet-stream"
            }
        }

        /** 获取 `query` 所需的数据。 */
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
                "standard_mime_type_extension",
                MediaStore.MediaColumns.XMP,
            ),
        ).apply {
            if (denied) throw SecurityException("permission revoked")
            if (deleted) return@apply
            lastProjection = projection?.copyOf()
            val video = uri.lastPathSegment == "video"
            val motion = uri.lastPathSegment in setOf("motion", "tagged-jpeg")
            addRow(
                arrayOf<Any?>(
                    when (uri.lastPathSegment) {
                        "video" -> "picked-video"
                        "motion" -> "picked-live.LIVP"
                        "tagged-jpeg" -> "picked-image.jpg"
                        "heic" -> "picked-image.heic"
                        "heif" -> "picked-image.heif"
                        else -> "picked-image"
                    },
                    100L,
                    10,
                    20,
                    if (video) 2_000L else 0L,
                    when {
                        !exposesSpecialFormat -> null
                        motion -> 2
                        else -> 0
                    },
                    if (motion) "<rdf GCamera:MotionPhoto='1' />".encodeToByteArray() else null,
                ),
            )
        }

        override fun openAssetFile(uri: Uri, mode: String): android.content.res.AssetFileDescriptor {
            contentOpens++
            throw java.io.FileNotFoundException("Image content must not be opened for format tags")
        }

        /** 执行 `insert` 方法定义的处理。 */
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        /** 清理 `delete` 对应的数据或资源。 */
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        /** 更新 `update` 对应的状态。 */
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    /** 执行 `uri` 方法定义的处理。 */
    private fun uri(name: String): Uri = Uri.parse("content://picker/$name")
}
