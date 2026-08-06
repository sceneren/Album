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
/** 验证 `AndroidPickerAdaptersTest` 覆盖的行为。 */
class AndroidPickerAdaptersTest {
    @Test
    /** 验证 `metadataReaderMapsImageVideoAndOptionalColumns` 所描述的场景。 */
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
        /** 处理 `onCreate` 回调。 */
        override fun onCreate(): Boolean = true

        /** 获取 `getType` 所需的数据。 */
        override fun getType(uri: Uri): String = when (uri.lastPathSegment) {
            "image" -> "image/jpeg"
            "video" -> "video/mp4"
            else -> "application/octet-stream"
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
