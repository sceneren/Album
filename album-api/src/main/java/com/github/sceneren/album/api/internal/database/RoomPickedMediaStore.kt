package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.github.sceneren.album.api.AlbumMediaFilter

internal class RoomPickedMediaStore(
    private val database: AlbumDatabase,
) : PickedMediaStore {
    private val dao = database.pickedMediaDao()

    override fun pagingSource(
        filter: AlbumMediaFilter,
    ): PagingSource<Int, PickedMediaEntity> = dao.pagingSource(filter.databaseTypes())

    override suspend fun loadPage(
        filter: AlbumMediaFilter,
        offset: Int,
        limit: Int,
    ): List<PickedMediaEntity> {
        require(offset >= 0) { "offset 不能小于 0" }
        require(limit > 0) { "limit 必须大于 0" }
        return dao.loadPage(filter.databaseTypes(), offset, limit)
    }

    override suspend fun upsertBatch(
        drafts: List<PickedMediaDraft>,
    ): List<PickedMediaEntity> {
        if (drafts.isEmpty()) return emptyList()

        return database.withTransaction {
            val existingByUri = drafts
                .map(PickedMediaDraft::uri)
                .distinct()
                .chunked(SQLITE_BIND_BATCH_SIZE)
                .flatMap { uris -> dao.findByUris(uris) }
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

    private companion object {
        const val SQLITE_BIND_BATCH_SIZE = 900
    }
}
