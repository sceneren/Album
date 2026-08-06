package com.github.sceneren.album.api.internal.mediastore

import android.net.Uri
import androidx.paging.PagingSource
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `MediaStoreMediaPagingSourceTest` 覆盖的行为。 */
class MediaStoreMediaPagingSourceTest {
    @Test
    /** 验证 `nextPageUsesReturnedOffset` 所描述的场景。 */
    fun nextPageUsesReturnedOffset() = runTest {
        val fake = FakeMediaStoreDataSource(items = mediaItems(75))
        val source = MediaStoreMediaPagingSource(
            dataSource = fake,
            mediaFilter = AlbumMediaFilter.IMAGES,
            bucketId = AlbumDirectory.ALL_BUCKET_ID,
        )

        val first = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page<Int, AlbumMedia>
        val second = source.load(
            PagingSource.LoadParams.Append(
                key = requireNotNull(first.nextKey),
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page<Int, AlbumMedia>

        assertEquals(listOf(0, 50), fake.requestedOffsets)
        assertEquals(null, second.nextKey)
        assertEquals(0, second.prevKey)
    }

    @Test
    /** 验证 `dataSourceFailureBecomesLoadError` 所描述的场景。 */
    fun dataSourceFailureBecomesLoadError() = runTest {
        val failure = IllegalStateException("query failed")
        val source = MediaStoreMediaPagingSource(
            dataSource = FakeMediaStoreDataSource(failure = failure),
            mediaFilter = AlbumMediaFilter.VIDEOS,
            bucketId = AlbumDirectory.ALL_BUCKET_ID,
        )

        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Error<Int, AlbumMedia>

        assertSame(failure, result.throwable)
    }

    @Test
    /** 验证 `cancellationPropagates` 所描述的场景。 */
    fun cancellationPropagates() = runTest {
        val cancellation = CancellationException("cancel load")
        val source = MediaStoreMediaPagingSource(
            dataSource = FakeMediaStoreDataSource(failure = cancellation),
            mediaFilter = AlbumMediaFilter.IMAGES,
            bucketId = AlbumDirectory.ALL_BUCKET_ID,
        )
        var thrown: CancellationException? = null

        try {
            source.load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 20,
                    placeholdersEnabled = false,
                ),
            )
        } catch (exception: CancellationException) {
            thrown = exception
        }

        assertSame(cancellation, thrown)
    }

    /** 负责 `FakeMediaStoreDataSource` 相关的数据与行为。 */
    private class FakeMediaStoreDataSource(
        private val items: List<AlbumMedia> = emptyList(),
        private val failure: Throwable? = null,
    ) : MediaStoreDataSource {
        val requestedOffsets = mutableListOf<Int>()

        /** 获取 `loadAll` 所需的数据。 */
        override suspend fun loadAll(
            mediaFilter: AlbumMediaFilter,
        ): List<AlbumMedia> = emptyList()

        /** 获取 `loadPage` 所需的数据。 */
        override suspend fun loadPage(
            mediaFilter: AlbumMediaFilter,
            bucketId: Long,
            offset: Int,
            limit: Int,
        ): List<AlbumMedia> {
            failure?.let { throw it }
            requestedOffsets += offset
            return items.drop(offset).take(limit)
        }

        /** 获取 `getDirectories` 所需的数据。 */
        override suspend fun getDirectories(
            mediaFilter: AlbumMediaFilter,
        ): List<AlbumDirectory> = emptyList()
    }

    /** 执行 `mediaItems` 方法定义的处理。 */
    private fun mediaItems(count: Int): List<AlbumMedia> = List(count) { index ->
        AlbumMedia(
            uri = Uri.parse("content://media/$index"),
            mediaType = AlbumMediaType.IMAGE,
            displayName = null,
            mimeType = "image/jpeg",
            sizeBytes = null,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            width = null,
            height = null,
            durationMillis = null,
            bucketId = null,
            bucketName = null,
            selectedAtEpochMillis = null,
            source = AlbumMediaSource.MEDIA_STORE,
        )
    }
}
