package com.github.sceneren.album

import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaFeed
import com.github.sceneren.album.api.AlbumMediaFilter

internal interface AlbumDataClient {
    fun getFeed(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
    ): AlbumMediaFeed

    suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): Result<List<AlbumDirectory>>
}

internal class AlbumApiDataClient(
    private val api: AlbumApi,
) : AlbumDataClient {
    override fun getFeed(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
    ): AlbumMediaFeed = api.getMediaFeed(mediaFilter, bucketId)

    override suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): Result<List<AlbumDirectory>> = api.getMediaDirectories(mediaFilter)
}
