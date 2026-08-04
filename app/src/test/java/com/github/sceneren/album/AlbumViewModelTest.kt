package com.github.sceneren.album

import android.net.Uri
import androidx.paging.PagingData
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaFeed
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.MediaAccessStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeClient = FakeAlbumDataClient()

    @Test
    fun changingFilterRebuildsFeedAndResetsDirectory() = runTest {
        val viewModel = AlbumViewModel(fakeClient)
        viewModel.selectDirectory(bucketId = 99)
        viewModel.setMediaFilter(AlbumMediaFilter.VIDEOS)
        advanceUntilIdle()

        assertEquals(AlbumMediaFilter.VIDEOS, viewModel.uiState.value.mediaFilter)
        assertEquals(AlbumDirectory.ALL_BUCKET_ID, viewModel.uiState.value.selectedBucketId)
        assertEquals(AlbumMediaFilter.VIDEOS, fakeClient.lastFeedFilter)
    }

    @Test
    fun partialSourceClearsDirectories() = runTest {
        fakeClient.directories = listOf(directory())
        val viewModel = AlbumViewModel(fakeClient)
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.directories.isNotEmpty())

        fakeClient.feedSource = AlbumMediaSource.PHOTO_PICKER
        fakeClient.accessStatus = MediaAccessStatus.PARTIAL
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.directories.isEmpty())
    }

    @Test
    fun changingFromMediaStoreToPickerResetsDirectory() = runTest {
        val viewModel = AlbumViewModel(fakeClient)
        viewModel.selectDirectory(99)
        fakeClient.feedSource = AlbumMediaSource.PHOTO_PICKER
        fakeClient.accessStatus = MediaAccessStatus.DENIED

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(AlbumDirectory.ALL_BUCKET_ID, viewModel.uiState.value.selectedBucketId)
    }

    private class FakeAlbumDataClient : AlbumDataClient {
        var feedSource = AlbumMediaSource.MEDIA_STORE
        var accessStatus = MediaAccessStatus.FULL
        var directories: List<AlbumDirectory> = emptyList()
        var lastFeedFilter: AlbumMediaFilter? = null
        var lastFeedBucket: Long? = null

        override fun getFeed(
            mediaFilter: AlbumMediaFilter,
            bucketId: Long,
        ): AlbumMediaFeed {
            lastFeedFilter = mediaFilter
            lastFeedBucket = bucketId
            return AlbumMediaFeed(
                mediaFilter = mediaFilter,
                source = feedSource,
                accessStatus = accessStatus,
                pagingData = flowOf(PagingData.empty()),
            )
        }

        override suspend fun getDirectories(
            mediaFilter: AlbumMediaFilter,
        ): Result<List<AlbumDirectory>> = Result.success(directories)
    }

    private fun directory() = AlbumDirectory(
        bucketId = 99,
        bucketName = "Camera",
        coverUri = Uri.parse("content://media/cover"),
        coverMediaType = AlbumMediaType.IMAGE,
        mediaCount = 1,
    )
}
