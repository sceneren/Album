package com.github.sceneren.album.api.internal.mediastore

import android.net.Uri
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.sceneren.album.api.*
import com.github.sceneren.album.api.internal.picker.AuthorizedMediaPagingSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Run the same offset contract against both production sources, not copied fake key logic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OffsetPagingRegressionTest {
    private fun items(count: Int) = List(count) { index ->
        AlbumMedia(Uri.parse("content://media/$index"), AlbumMediaType.IMAGE,
            null, "image/jpeg", null, null, null, null, null, null, null, null, null,
            AlbumMediaSource.MEDIA_STORE)
    }

    private fun sources(data: List<AlbumMedia>): List<PagingSource<Int, AlbumMedia>> {
        val source = object : MediaStoreDataSource {
            override suspend fun loadPage(mediaFilter: AlbumMediaFilter, bucketId: Long,
                offset: Int, limit: Int) = data.drop(offset).take(limit)
            override suspend fun getDirectories(mediaFilter: AlbumMediaFilter) = emptyList<AlbumDirectory>()
        }
        return listOf(
            MediaStoreMediaPagingSource(source, AlbumMediaFilter.IMAGES, AlbumDirectory.ALL_BUCKET_ID),
            AuthorizedMediaPagingSource { offset, limit -> data.drop(offset).take(limit) },
        )
    }

    @Test fun tailRefreshThenPrependReturnsEveryUriExactlyOnce() = runTest {
        val data = items(125)
        for (source in sources(data)) {
            val tail = source.load(PagingSource.LoadParams.Refresh(100, 50, false)).page()
            assertEquals(25, tail.data.size)
            val refreshKey = source.getRefreshKey(PagingState(
                pages = listOf(tail), anchorPosition = 12,
                config = PagingConfig(50, enablePlaceholders = false), leadingPlaceholderCount = 0,
            ))
            assertEquals(100, refreshKey)
            var page = source.load(PagingSource.LoadParams.Refresh(refreshKey, 50, false)).page()
            var combined = page.data
            while (page.prevKey != null) {
                page = source.load(PagingSource.LoadParams.Prepend(page.prevKey!!, 50, false)).page()
                combined = page.data + combined
            }
            assertEquals(data.map { it.uri }, combined.map { it.uri })
            assertEquals(combined.size, combined.map { it.uri }.distinct().size)
        }
    }

    @Test fun unalignedRefreshAndVariablePrependSizesNeverOverlap() = runTest {
        val data = items(125)
        for (source in sources(data)) {
            val middle = source.load(PagingSource.LoadParams.Refresh(75, 50, false)).page()
            val preceding = source.load(PagingSource.LoadParams.Prepend(middle.prevKey!!, 50, false)).page()
            assertEquals(25, preceding.itemsBefore)
            val first = source.load(PagingSource.LoadParams.Prepend(preceding.prevKey!!, 60, false)).page()
            assertEquals(25, first.data.size)
            assertNull(first.prevKey)
            assertEquals(25, first.nextKey)
            assertEquals(data, first.data + preceding.data + middle.data)
        }
    }

    @Test fun zeroPrependAndEmptyTailHaveValidBoundaries() = runTest {
        for (source in sources(items(10))) {
            val zero = source.load(PagingSource.LoadParams.Prepend(0, 50, false)).page()
            assertTrue(zero.data.isEmpty())
            assertNull(zero.prevKey)
            val empty = source.load(PagingSource.LoadParams.Refresh(20, 50, false)).page()
            assertEquals(20, empty.itemsBefore)
            assertNull(empty.nextKey)
        }
    }

    private fun PagingSource.LoadResult<Int, AlbumMedia>.page(): PagingSource.LoadResult.Page<Int, AlbumMedia> {
        assertTrue("Expected page, got $this", this is PagingSource.LoadResult.Page)
        return this as PagingSource.LoadResult.Page<Int, AlbumMedia>
    }
}
