package com.github.sceneren.album.ui.compose

import androidx.paging.LoadState
import androidx.paging.PagingData
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFeed
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.MediaAccessStatus
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 Compose 媒体网格在首屏数据未确认前保持加载态。 */
class MediaGridLoadingStateTest {
    private val feed = AlbumMediaFeed(
        mediaFilter = AlbumMediaFilter.IMAGES,
        source = AlbumMediaSource.PHOTO_PICKER,
        accessStatus = MediaAccessStatus.DENIED,
        pagingData = emptyFlow<PagingData<AlbumMedia>>(),
    )

    @Test
    fun `没有数据源时保持加载态`() {
        assertTrue(
            isMediaGridInitialLoading(
                feed = null,
                refreshLoadState = LoadState.NotLoading(endOfPaginationReached = false),
            ),
        )
    }

    @Test
    fun `分页首刷完成前保持加载态`() {
        assertTrue(
            isMediaGridInitialLoading(
                feed = feed,
                refreshLoadState = LoadState.Loading,
            ),
        )
    }

    @Test
    fun `数据源和首刷完成后显示网格`() {
        assertFalse(
            isMediaGridInitialLoading(
                feed = feed,
                refreshLoadState = LoadState.NotLoading(endOfPaginationReached = false),
            ),
        )
    }
}
