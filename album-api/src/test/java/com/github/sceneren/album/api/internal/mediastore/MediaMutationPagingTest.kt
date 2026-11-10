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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaMutationPagingTest {
    private val original = (0 until 237).map { id ->
        AlbumMedia(Uri.parse("content://media/$id"), AlbumMediaType.IMAGE,
            "$id.jpg", "image/jpeg", 100, 100, 100, 800, 600, null, 1, "Camera", null,
            AlbumMediaSource.MEDIA_STORE)
    }

    @Test fun insertDeleteAndReplaceBeforeAppendNeverSkipOrDuplicateUris() = runTest {
        for (authorized in listOf(false, true)) {
            for (mutation in listOf<(List<AlbumMedia>) -> List<AlbumMedia>>(
                { listOf(newItem(1)) + it },
                { it.drop(7) },
                { it.take(48) + listOf(newItem(2)) + it.drop(53) },
                { listOf(newItem(3), newItem(4)) + it.filterIndexed { i, _ -> i % 17 != 0 } },
            )) {
                val store = MutableStore(original)
                val old = source(store, authorized)
                val first = old.load(refresh(null)).page()
                val second = old.load(append(first.nextKey!!)).page()
                store.items = mutation(original)
                assertTrue(old.load(append(second.nextKey!!)) is PagingSource.LoadResult.Invalid)
                assertTrue(old.invalid)
                val updated = source(store, authorized)
                val key = updated.getRefreshKey(state(listOf(first, second), 48))
                val refreshed = updated.load(refresh(key)).page()
                val survivingRow = original[48].takeIf { it in store.items } ?: original[47]
                assertTrue("The surviving row spanning the page boundary must remain", survivingRow in refreshed.data)
                assertEveryUri(store.items, drain(updated, refreshed))
            }
        }
    }

    @Test fun largeInsertionBeforeDeepWindowRetainsAnchorAndAllPagesRemainReachable() = runTest {
        for (authorized in listOf(false, true)) {
            val store = MutableStore(original)
            val old = source(store, authorized)
            val window = old.load(refresh(100)).page()
            store.items = (1..90).map(::newItem) + original
            val updated = source(store, authorized)
            val key = updated.getRefreshKey(state(listOf(window), 22))
            val refreshed = updated.load(refresh(key)).page()
            assertEquals(190, refreshed.itemsBefore)
            assertEquals(original[122], refreshed.data[22])
            assertEveryUri(store.items, drain(updated, refreshed))
        }
    }

    @Test fun deletingAnchorOrEntireOldWindowStillAllowsCompleteTraversal() = runTest {
        for (authorized in listOf(false, true)) {
            for (deleteAll in listOf(false, true)) {
                val store = MutableStore(original)
                val old = source(store, authorized)
                val window = old.load(refresh(100)).page()
                store.items = original.filterIndexed { i, _ -> if (deleteAll) i !in 100..149 else i != 122 }
                val updated = source(store, authorized)
                val key = updated.getRefreshKey(state(listOf(window), 22))
                val refreshed = updated.load(refresh(key)).page()
                assertEveryUri(store.items, drain(updated, refreshed))
            }
        }
    }

    @Test fun mutationDuringQueryCannotPublishAMixedGeneration() = runTest {
        for (authorized in listOf(false, true)) {
            val store = MutableStore(original)
            val source = source(store, authorized)
            val first = source.load(refresh(null)).page()
            store.afterQuery = { store.items = store.items.drop(3) }
            assertTrue(source.load(append(first.nextKey!!)) is PagingSource.LoadResult.Invalid)
            assertTrue(source.invalid)
        }
    }

    private fun source(store: MutableStore, authorized: Boolean): PagingSource<Int, AlbumMedia> =
        if (authorized) AuthorizedMediaPagingSource(readSignature = { store.items.toString() }) { offset, limit ->
            store.loadPage(AlbumMediaFilter.IMAGES, -1, offset, limit)
        } else MediaStoreMediaPagingSource(store, AlbumMediaFilter.IMAGES, -1)

    private suspend fun drain(source: PagingSource<Int, AlbumMedia>, initial: PagingSource.LoadResult.Page<Int, AlbumMedia>): List<AlbumMedia> {
        var all = initial.data
        var page = initial
        var iterations = 0
        while (page.prevKey != null) {
            check(iterations++ < 30)
            page = source.load(PagingSource.LoadParams.Prepend(page.prevKey!!, 37, false)).page()
            all = page.data + all
        }
        page = initial
        while (page.nextKey != null) {
            check(iterations++ < 60)
            page = source.load(append(page.nextKey!!)).page()
            all = all + page.data
        }
        return all
    }

    private fun assertEveryUri(expected: List<AlbumMedia>, actual: List<AlbumMedia>) {
        assertEquals(expected.map { it.uri }, actual.map { it.uri })
        assertEquals(actual.size, actual.map { it.uri }.distinct().size)
    }
    private fun newItem(id: Int) = original[0].copy(uri = Uri.parse("content://media/new-$id"))
    private fun refresh(key: Int?) = PagingSource.LoadParams.Refresh(key, 50, false)
    private fun append(key: Int) = PagingSource.LoadParams.Append(key, 50, false)
    private fun state(pages: List<PagingSource.LoadResult.Page<Int, AlbumMedia>>, anchor: Int) =
        PagingState(pages, anchor, PagingConfig(50, enablePlaceholders = false), 0)
    private fun PagingSource.LoadResult<Int, AlbumMedia>.page(): PagingSource.LoadResult.Page<Int, AlbumMedia> {
        assertTrue("Expected page, got $this", this is PagingSource.LoadResult.Page)
        return this as PagingSource.LoadResult.Page
    }
    private class MutableStore(var items: List<AlbumMedia>) : MediaStoreDataSource {
        var afterQuery: (() -> Unit)? = null
        override suspend fun getChangeSignature(mediaFilter: AlbumMediaFilter) = items.toString()
        override suspend fun findPositions(mediaFilter: AlbumMediaFilter, bucketId: Long, uris: Set<Uri>) =
            items.withIndex().filter { it.value.uri in uris }.associate { it.value.uri to it.index }
        override suspend fun getDirectories(mediaFilter: AlbumMediaFilter) = emptyList<AlbumDirectory>()
        override suspend fun loadPage(mediaFilter: AlbumMediaFilter, bucketId: Long, offset: Int, limit: Int) =
            items.drop(offset).take(limit).also { afterQuery?.invoke(); afterQuery = null }
    }
}
