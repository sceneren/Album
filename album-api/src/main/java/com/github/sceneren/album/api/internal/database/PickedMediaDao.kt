package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
/** 定义 `PickedMediaDao` 的能力边界。 */
internal interface PickedMediaDao {
    @Query(
        "SELECT * FROM picked_media " +
            "WHERE mediaType IN (:mediaTypes) " +
            "ORDER BY sortOrder DESC, uri ASC",
    )
    /** 执行 `pagingSource` 方法定义的处理。 */
    fun pagingSource(mediaTypes: List<String>): PagingSource<Int, PickedMediaEntity>

    @Query(
        "SELECT * FROM picked_media " +
            "WHERE mediaType IN (:mediaTypes) " +
            "ORDER BY sortOrder DESC, uri ASC " +
            "LIMIT :limit OFFSET :offset",
    )
    /** 获取 `loadPage` 所需的数据。 */
    suspend fun loadPage(
        mediaTypes: List<String>,
        offset: Int,
        limit: Int,
    ): List<PickedMediaEntity>

    @Query("SELECT * FROM picked_media WHERE uri IN (:uris)")
    /** 获取 `findByUris` 所需的数据。 */
    suspend fun findByUris(uris: List<String>): List<PickedMediaEntity>

    @Query("SELECT * FROM picked_media ORDER BY sortOrder DESC, uri ASC")
    /** 执行 `all` 方法定义的处理。 */
    suspend fun all(): List<PickedMediaEntity>

    @Query("SELECT MAX(sortOrder) FROM picked_media")
    /** 执行 `maxSortOrder` 方法定义的处理。 */
    suspend fun maxSortOrder(): Long?

    @Upsert
    /** 执行 `upsertAll` 方法定义的处理。 */
    suspend fun upsertAll(items: List<PickedMediaEntity>)

    @Query("DELETE FROM picked_media WHERE uri = :uri")
    /** 清理 `deleteByUri` 对应的数据或资源。 */
    suspend fun deleteByUri(uri: String): Int

    @Query("DELETE FROM picked_media")
    /** 清理 `deleteAll` 对应的数据或资源。 */
    suspend fun deleteAll(): Int
}
