package com.github.sceneren.album.api

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/** Paging stream together with the access decision and backing source used to produce it. */
data class AlbumMediaFeed(
    val mediaFilter: AlbumMediaFilter,
    val source: AlbumMediaSource,
    val accessStatus: MediaAccessStatus,
    val pagingData: Flow<PagingData<AlbumMedia>>,
)
