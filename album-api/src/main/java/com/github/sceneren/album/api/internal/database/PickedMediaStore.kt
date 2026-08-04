package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import com.github.sceneren.album.api.AlbumMediaFilter

internal data class PickedMediaDraft(
    val uri: String,
    val mediaType: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val selectedAtEpochMillis: Long,
    val ownsPersistableGrant: Boolean,
) {
    fun toEntity(
        sortOrder: Long,
        ownsPersistableGrant: Boolean,
    ) = PickedMediaEntity(
        uri = uri,
        mediaType = mediaType,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMillis = durationMillis,
        selectedAtEpochMillis = selectedAtEpochMillis,
        sortOrder = sortOrder,
        ownsPersistableGrant = ownsPersistableGrant,
    )
}

internal interface PickedMediaStore {
    fun pagingSource(filter: AlbumMediaFilter): PagingSource<Int, PickedMediaEntity>

    suspend fun upsertBatch(drafts: List<PickedMediaDraft>): List<PickedMediaEntity>

    suspend fun find(uri: String): PickedMediaEntity?

    suspend fun remove(uri: String): PickedMediaEntity?

    suspend fun clear(): List<PickedMediaEntity>

    suspend fun all(): List<PickedMediaEntity>
}
