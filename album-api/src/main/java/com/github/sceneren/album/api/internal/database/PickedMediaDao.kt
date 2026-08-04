package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface PickedMediaDao {
    @Query(
        "SELECT * FROM picked_media " +
            "WHERE mediaType IN (:mediaTypes) " +
            "ORDER BY sortOrder DESC, uri ASC",
    )
    fun pagingSource(mediaTypes: List<String>): PagingSource<Int, PickedMediaEntity>

    @Query("SELECT * FROM picked_media WHERE uri IN (:uris)")
    suspend fun findByUris(uris: List<String>): List<PickedMediaEntity>

    @Query("SELECT * FROM picked_media ORDER BY sortOrder DESC, uri ASC")
    suspend fun all(): List<PickedMediaEntity>

    @Query("SELECT MAX(sortOrder) FROM picked_media")
    suspend fun maxSortOrder(): Long?

    @Upsert
    suspend fun upsertAll(items: List<PickedMediaEntity>)

    @Query("DELETE FROM picked_media WHERE uri = :uri")
    suspend fun deleteByUri(uri: String): Int

    @Query("DELETE FROM picked_media")
    suspend fun deleteAll(): Int
}
