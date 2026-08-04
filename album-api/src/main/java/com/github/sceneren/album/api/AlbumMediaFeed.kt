package com.github.sceneren.album.api

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/** Effective host access to the device media library for a requested filter. */
enum class MediaAccessStatus {
    FULL,
    PARTIAL,
    DENIED,
}

/** Paging stream together with the access decision and backing source used to produce it. */
data class AlbumMediaFeed(
    val mediaFilter: AlbumMediaFilter,
    val source: AlbumMediaSource,
    val accessStatus: MediaAccessStatus,
    val pagingData: Flow<PagingData<AlbumMedia>>,
)
