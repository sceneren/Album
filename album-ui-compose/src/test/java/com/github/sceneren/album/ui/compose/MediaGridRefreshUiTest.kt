package com.github.sceneren.album.ui.compose

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.sceneren.album.api.AlbumCameraConfig
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerSessionSnapshot
import com.github.sceneren.album.api.MediaAccessStatus
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaGridRefreshUiTest {
    @get:Rule val compose = createComposeRule()
    private val original = (1..60).map(::media)
    private val refreshStarted = CompletableDeferred<Unit>()
    private val refreshResult = CompletableDeferred<PagingSource.LoadResult<Int, AlbumMedia>>()
    private lateinit var pagingItems: LazyPagingItems<AlbumMedia>
    private val disposed = mutableSetOf<Uri>()

    @Test
    fun refreshKeepsVisibleCellsAndScrollUntilNewPageIsReady() {
        showGrid()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(24)
        val before = compose.onNodeWithContentDescription("image-25").fetchSemanticsNode()
        compose.runOnIdle { disposed.clear(); pagingItems.refresh() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { refreshStarted.isCompleted }
        compose.runOnIdle {
            assertTrue(pagingItems.loadState.refresh is LoadState.Loading)
            assertEquals(60, pagingItems.itemCount)
            assertTrue(original[24].uri !in disposed)
        }
        val during = compose.onNodeWithContentDescription("image-25").fetchSemanticsNode()
        assertEquals(before.id, during.id)
        assertEquals(before.boundsInRoot, during.boundsInRoot)
        refreshResult.complete(PagingSource.LoadResult.Page(listOf(media(0)) + original, null, null))
        compose.waitForIdle()
        compose.waitUntil(5_000) { pagingItems.itemCount == 61 }
        compose.onNodeWithContentDescription("image-25").assertExists()
        compose.runOnIdle {
            assertEquals(61, pagingItems.itemSnapshotList.items.map { it.uri }.distinct().size)
        }
    }

    @Test
    fun refreshFailureKeepsOldCellsAndShowsRetry() {
        showGrid()
        compose.runOnIdle { disposed.clear(); pagingItems.refresh() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { refreshStarted.isCompleted }
        refreshResult.complete(PagingSource.LoadResult.Error(IllegalStateException("Provider unavailable")))
        compose.waitForIdle()
        compose.waitUntil(5_000) { pagingItems.loadState.refresh is LoadState.Error }
        compose.onNodeWithText("加载失败，点击重试").assertExists()
        compose.onNodeWithContentDescription("image-1").assertExists()
        compose.runOnIdle {
            assertEquals(60, pagingItems.itemCount)
            assertTrue(original[0].uri !in disposed)
        }
    }

    @Test
    fun removalBeforeViewportRetainsVisibleUriAndPixelOffset() {
        showGrid()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(24)
        val before = compose.onNodeWithContentDescription("image-25").fetchSemanticsNode()
        compose.runOnIdle { disposed.clear(); pagingItems.refresh() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { refreshStarted.isCompleted }
        refreshResult.complete(PagingSource.LoadResult.Page(original.drop(4), null, null))
        compose.waitForIdle()
        compose.waitUntil(5_000) { pagingItems.itemCount == 56 }
        compose.waitForIdle()
        val after = compose.onNodeWithContentDescription("image-25").fetchSemanticsNode()
        assertEquals(before.id, after.id)
        assertEquals(before.boundsInRoot.top, after.boundsInRoot.top)
        compose.runOnIdle { assertTrue(original[24].uri !in disposed) }
    }

    @Test
    fun editingOneFileDoesNotRestartUnchangedVisibleTiles() {
        showGrid()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(24)
        val before = compose.onNodeWithContentDescription("image-25").fetchSemanticsNode()
        compose.runOnIdle { disposed.clear(); pagingItems.refresh() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { refreshStarted.isCompleted }
        val updated = original.map { if (it == original[25]) it.copy(generationModified = 2) else it }
        refreshResult.complete(PagingSource.LoadResult.Page(updated, null, null))
        compose.waitForIdle()
        compose.waitUntil(5_000) { pagingItems.itemSnapshotList.items == updated }
        compose.waitForIdle()
        val after = compose.onNodeWithContentDescription("image-25").fetchSemanticsNode()
        assertEquals(before.id, after.id)
        assertEquals(before.boundsInRoot, after.boundsInRoot)
        compose.runOnIdle {
            assertTrue(original[24].uri !in disposed)
            assertTrue(original[25].uri in disposed)
        }
    }

    @Test
    fun successfulEmptyRefreshRemovesStaleMediaButKeepsAddAction() {
        showGrid()
        compose.runOnIdle { pagingItems.refresh() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { refreshStarted.isCompleted }
        refreshResult.complete(PagingSource.LoadResult.Page(emptyList(), null, null))
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading
        }
        compose.onNodeWithContentDescription("image-1").assertDoesNotExist()
        compose.onNodeWithText("添加更多").assertExists()
    }

    private fun showGrid() {
        var generation = 0
        val flow = Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) {
            val isRefresh = generation++ > 0
            object : PagingSource<Int, AlbumMedia>() {
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AlbumMedia> {
                    if (isRefresh) {
                        refreshStarted.complete(Unit)
                        return refreshResult.await()
                    }
                    return LoadResult.Page(original, null, null)
                }
                override fun getRefreshKey(state: PagingState<Int, AlbumMedia>): Int = 0
            }
        }.flow
        compose.setContent {
            val items = flow.collectAsLazyPagingItems()
            SideEffect { pagingItems = items }
            MaterialTheme {
                AlbumPickerScreen(
                    embedded = true, systemBarStyle = null, modifier = Modifier,
                    config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 9, camera = AlbumCameraConfig(enabled = false)),
                    appearance = AlbumPickerAppearance(),
                    session = AlbumPickerSessionSnapshot("refresh", emptyList(), emptyList(), emptySet(), AlbumDirectory.ALL_BUCKET_ID, null, false),
                    accessStatus = MediaAccessStatus.DENIED, areMediaPermissionsDeclared = false,
                    directories = emptyList(), pagingItems = items, preview = null,
                    imageLoader = { item, _ ->
                        DisposableEffect(item) { onDispose { disposed += item.uri } }
                        ColorPainter(Color.Gray)
                    },
                    videoPlayerFactory = null, onBack = {}, onDirectory = {}, onRequestPermission = {},
                    onAddMore = {}, onCamera = {}, onToggle = {}, onGridPreview = { _, _ -> },
                    onSelectedPreview = {}, onPreviewLoadMore = {}, onClosePreview = {}, onConfirm = {}, isProcessing = false,
                )
            }
        }
        compose.waitUntil(5_000) { ::pagingItems.isInitialized && pagingItems.itemCount == 60 }
    }

    private fun media(id: Int) = AlbumMedia(
        uri = Uri.parse("content://refresh-test/$id"), mediaType = AlbumMediaType.IMAGE,
        displayName = "image-$id", mimeType = "image/jpeg", sizeBytes = null,
        dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null,
        width = 800, height = 600, durationMillis = null, bucketId = null,
        bucketName = null, selectedAtEpochMillis = null, source = AlbumMediaSource.PHOTO_PICKER,
    )
}
