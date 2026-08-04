package com.github.sceneren.album.api.internal.database

import android.net.Uri
import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType

internal class RoomPickedMediaStore(
    private val database: AlbumDatabase,
) : PickedMediaStore {
    private val dao = database.pickedMediaDao()

    override fun pagingSource(
        filter: AlbumMediaFilter,
    ): PagingSource<Int, PickedMediaEntity> = dao.pagingSource(filter.databaseTypes())

    override suspend fun upsertBatch(
        drafts: List<PickedMediaDraft>,
    ): List<PickedMediaEntity> {
        if (drafts.isEmpty()) return emptyList()

        return database.withTransaction {
            val existingByUri = dao.findByUris(drafts.map(PickedMediaDraft::uri))
                .associateBy(PickedMediaEntity::uri)
            val firstSortOrder = (dao.maxSortOrder() ?: 0L) + drafts.size
            val entities = drafts.mapIndexed { index, draft ->
                draft.toEntity(
                    sortOrder = firstSortOrder - index,
                    ownsPersistableGrant =
                        existingByUri[draft.uri]?.ownsPersistableGrant == true ||
                            draft.ownsPersistableGrant,
                )
            }
            dao.upsertAll(entities)
            entities
        }
    }

    override suspend fun find(uri: String): PickedMediaEntity? =
        dao.findByUris(listOf(uri)).singleOrNull()

    override suspend fun remove(uri: String): PickedMediaEntity? = database.withTransaction {
        val existing = find(uri) ?: return@withTransaction null
        dao.deleteByUri(uri)
        existing
    }

    override suspend fun clear(): List<PickedMediaEntity> = database.withTransaction {
        val existing = dao.all()
        dao.deleteAll()
        existing
    }

    override suspend fun all(): List<PickedMediaEntity> = dao.all()
}

internal fun PickedMediaEntity.toAlbumMedia(): AlbumMedia = AlbumMedia(
    uri = Uri.parse(uri),
    mediaType = when (mediaType) {
        AlbumMediaType.IMAGE.name -> AlbumMediaType.IMAGE
        AlbumMediaType.VIDEO.name -> AlbumMediaType.VIDEO
        else -> error("Unsupported stored media type: $mediaType")
    },
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateAddedEpochSeconds = null,
    dateModifiedEpochSeconds = null,
    width = width,
    height = height,
    durationMillis = durationMillis,
    bucketId = null,
    bucketName = null,
    selectedAtEpochMillis = selectedAtEpochMillis,
    source = AlbumMediaSource.PHOTO_PICKER,
)

private fun AlbumMediaFilter.databaseTypes(): List<String> = when (this) {
    AlbumMediaFilter.IMAGES -> listOf(AlbumMediaType.IMAGE.name)
    AlbumMediaFilter.VIDEOS -> listOf(AlbumMediaType.VIDEO.name)
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> AlbumMediaType.entries.map(AlbumMediaType::name)
}
