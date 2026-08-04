package com.github.sceneren.album.api.internal.mediastore

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMediaStoreDataSourceTest {
    @Test
    fun api30UsesBundlePagingAndMapsMixedRows() = runTest {
        val provider = RecordingMediaProvider(
            rows = listOf(imageRow(id = 9), videoRow(id = 8, duration = 2_000)),
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        val source = AndroidMediaStoreDataSource(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = async {
            source.loadPage(
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
                AlbumDirectory.ALL_BUCKET_ID,
                offset = 10,
                limit = 20,
            )
        }
        advanceUntilIdle()

        assertEquals(
            listOf(AlbumMediaType.IMAGE, AlbumMediaType.VIDEO),
            result.await().map { it.mediaType },
        )
        assertEquals(10, provider.lastQueryArgs?.getInt(ContentResolver.QUERY_ARG_OFFSET))
        assertEquals(20, provider.lastQueryArgs?.getInt(ContentResolver.QUERY_ARG_LIMIT))
        assertEquals(
            "date_added DESC, _id DESC",
            provider.lastQueryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER),
        )
        assertEquals("content://media/external/images/media/9", result.await()[0].uri.toString())
        assertEquals("content://media/external/video/media/8", result.await()[1].uri.toString())
        assertEquals(null, result.await()[0].durationMillis)
        assertEquals(2_000L, result.await()[1].durationMillis)
    }

    @Test
    fun loadAllOmitsPagingArgumentsAndKeepsFilterSelection() = runTest {
        val provider = RecordingMediaProvider(
            rows = listOf(videoRow(id = 11, duration = 3_000)),
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        val source = AndroidMediaStoreDataSource(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = async { source.loadAll(AlbumMediaFilter.VIDEOS) }
        advanceUntilIdle()

        assertEquals(listOf("content://media/external/video/media/11"), result.await().map { it.uri.toString() })
        assertEquals(
            listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()),
            provider.lastQueryArgs?.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)?.toList(),
        )
        assertEquals(null, provider.lastQueryArgs?.getIntOrNull(ContentResolver.QUERY_ARG_LIMIT))
        assertEquals(null, provider.lastQueryArgs?.getIntOrNull(ContentResolver.QUERY_ARG_OFFSET))
        assertEquals(
            "date_added DESC, _id DESC",
            provider.lastQueryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER),
        )
    }

    @Test
    @Config(sdk = [29])
    fun api29UsesSqlLimitAndOffset() = runTest {
        val provider = RecordingMediaProvider(rows = listOf(imageRow(id = 9)))
        ShadowContentResolver.registerProviderInternal("media", provider)
        val source = AndroidMediaStoreDataSource(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = async {
            source.loadPage(
                AlbumMediaFilter.IMAGES,
                AlbumDirectory.ALL_BUCKET_ID,
                offset = 10,
                limit = 20,
            )
        }
        advanceUntilIdle()
        result.await()

        assertEquals("date_added DESC, _id DESC LIMIT 20 OFFSET 10", provider.lastSortOrder)
    }

    @Test
    fun directoriesIncludeVirtualAllAndMixedCovers() = runTest {
        val provider = RecordingMediaProvider(
            rows = listOf(
                imageRow(id = 9, bucketId = 2, bucketName = "Screenshots", dateAdded = 300),
                videoRow(id = 8, bucketId = 1, bucketName = "Camera", dateAdded = 200),
                imageRow(id = 7, bucketId = 2, bucketName = "Screenshots", dateAdded = 100),
            ),
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        val source = AndroidMediaStoreDataSource(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = async { source.getDirectories(AlbumMediaFilter.IMAGES_AND_VIDEOS) }
        advanceUntilIdle()
        val directories = result.await()

        assertEquals(
            listOf(AlbumDirectory.ALL_BUCKET_ID, 2L, 1L),
            directories.map { it.bucketId },
        )
        assertEquals(listOf(3L, 2L, 1L), directories.map { it.mediaCount })
        assertEquals(AlbumMediaType.IMAGE, directories[0].coverMediaType)
        assertEquals(AlbumMediaType.IMAGE, directories[1].coverMediaType)
        assertEquals(AlbumMediaType.VIDEO, directories[2].coverMediaType)
        assertEquals(null, directories[0].bucketName)
        assertEquals(
            listOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.Images.ImageColumns.BUCKET_ID,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
            ),
            provider.lastProjection?.toList(),
        )
    }

    private class RecordingMediaProvider(
        private val rows: List<Map<String, Any?>>,
    ) : ContentProvider() {
        var lastQueryArgs: Bundle? = null
        var lastSortOrder: String? = null
        var lastProjection: Array<out String>? = null

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            queryArgs: Bundle?,
            cancellationSignal: CancellationSignal?,
        ): Cursor {
            lastQueryArgs = queryArgs?.let(::Bundle)
            lastProjection = projection
            return cursor(projection)
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            lastSortOrder = sortOrder
            lastProjection = projection
            return cursor(projection)
        }

        private fun cursor(projection: Array<out String>?): Cursor {
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                rows.forEach { row -> addRow(columns.map(row::get).toTypedArray()) }
            }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private fun Bundle.getIntOrNull(key: String): Int? =
        if (containsKey(key)) getInt(key) else null

    private fun imageRow(
        id: Long,
        bucketId: Long = 1,
        bucketName: String = "Camera",
        dateAdded: Long = 100,
    ) = mediaRow(
        id = id,
        mediaType = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
        mimeType = "image/jpeg",
        bucketId = bucketId,
        bucketName = bucketName,
        dateAdded = dateAdded,
        duration = 0,
    )

    private fun videoRow(
        id: Long,
        duration: Long = 2_000,
        bucketId: Long = 1,
        bucketName: String = "Camera",
        dateAdded: Long = 100,
    ) = mediaRow(
        id = id,
        mediaType = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
        mimeType = "video/mp4",
        bucketId = bucketId,
        bucketName = bucketName,
        dateAdded = dateAdded,
        duration = duration,
    )

    private fun mediaRow(
        id: Long,
        mediaType: Int,
        mimeType: String,
        bucketId: Long,
        bucketName: String,
        dateAdded: Long,
        duration: Long,
    ): Map<String, Any?> = mapOf(
        MediaStore.Files.FileColumns._ID to id,
        MediaStore.Files.FileColumns.MEDIA_TYPE to mediaType,
        MediaStore.MediaColumns.DISPLAY_NAME to "media-$id",
        MediaStore.MediaColumns.SIZE to 4_096L,
        MediaStore.MediaColumns.DATE_ADDED to dateAdded,
        MediaStore.MediaColumns.DATE_MODIFIED to dateAdded - 1,
        MediaStore.MediaColumns.MIME_TYPE to mimeType,
        MediaStore.MediaColumns.WIDTH to 1_920,
        MediaStore.MediaColumns.HEIGHT to 1_080,
        MediaStore.Video.VideoColumns.DURATION to duration,
        MediaStore.Images.ImageColumns.BUCKET_ID to bucketId,
        MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME to bucketName,
    )
}
