package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import com.github.sceneren.album.api.AlbumMediaFilter

internal interface PickedMediaStore {
    fun pagingSource(filter: AlbumMediaFilter): PagingSource<Int, PickedMediaEntity>

    suspend fun loadPage(
        filter: AlbumMediaFilter,
        offset: Int,
        limit: Int,
    ): List<PickedMediaEntity>

    suspend fun upsertBatch(drafts: List<PickedMediaDraft>): List<PickedMediaEntity>

    suspend fun find(uri: String): PickedMediaEntity?

    suspend fun remove(uri: String): PickedMediaEntity?

    suspend fun clear(): List<PickedMediaEntity>

    suspend fun all(): List<PickedMediaEntity>
}
