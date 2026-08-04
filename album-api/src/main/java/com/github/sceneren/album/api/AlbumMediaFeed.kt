package com.github.sceneren.album.api

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

enum class MediaAccessStatus {
    FULL,
    PARTIAL,
    DENIED,
}

data class AlbumMediaFeed(
    val mediaFilter: AlbumMediaFilter,
    val source: AlbumMediaSource,
    val accessStatus: MediaAccessStatus,
    val pagingData: Flow<PagingData<AlbumMedia>>,
)
