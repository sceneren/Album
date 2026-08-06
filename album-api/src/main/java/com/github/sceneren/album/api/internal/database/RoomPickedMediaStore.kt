package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.github.sceneren.album.api.AlbumMediaFilter

/** 负责 `RoomPickedMediaStore` 管理数据的持久化读写。 */
internal class RoomPickedMediaStore(
    private val database: AlbumDatabase,
) : PickedMediaStore {
    private val dao = database.pickedMediaDao()

    /** 执行 `pagingSource` 方法定义的处理。 */
    override fun pagingSource(
        filter: AlbumMediaFilter,
    ): PagingSource<Int, PickedMediaEntity> = dao.pagingSource(filter.databaseTypes())

    /** 获取 `loadPage` 所需的数据。 */
    override suspend fun loadPage(
        filter: AlbumMediaFilter,
        offset: Int,
        limit: Int,
    ): List<PickedMediaEntity> {
        require(offset >= 0) { "offset 不能小于 0" }
        require(limit > 0) { "limit 必须大于 0" }
        return dao.loadPage(filter.databaseTypes(), offset, limit)
    }

    /** 执行 `upsertBatch` 方法定义的处理。 */
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

    /** 获取 `find` 所需的数据。 */
    override suspend fun find(uri: String): PickedMediaEntity? =
        dao.findByUris(listOf(uri)).singleOrNull()

    /** 清理 `remove` 对应的数据或资源。 */
    override suspend fun remove(uri: String): PickedMediaEntity? = database.withTransaction {
        val existing = find(uri) ?: return@withTransaction null
        dao.deleteByUri(uri)
        existing
    }

    /** 清理 `clear` 对应的数据或资源。 */
    override suspend fun clear(): List<PickedMediaEntity> = database.withTransaction {
        val existing = dao.all()
        dao.deleteAll()
        existing
    }

    /** 执行 `all` 方法定义的处理。 */
    override suspend fun all(): List<PickedMediaEntity> = dao.all()

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `SQLITE_BIND_BATCH_SIZE` 对应的数据。 */
        const val SQLITE_BIND_BATCH_SIZE = 900
    }
}
