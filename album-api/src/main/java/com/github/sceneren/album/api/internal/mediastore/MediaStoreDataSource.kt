package com.github.sceneren.album.api.internal.mediastore

import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter

internal interface MediaStoreDataSource {
    suspend fun loadAll(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumMedia>

    suspend fun loadPage(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
        offset: Int,
        limit: Int,
    ): List<AlbumMedia>

    suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumDirectory>
}
