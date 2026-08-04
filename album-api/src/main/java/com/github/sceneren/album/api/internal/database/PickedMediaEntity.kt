package com.github.sceneren.album.api.internal.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "picked_media",
    indices = [Index(value = ["mediaType", "sortOrder", "uri"])],
)
internal data class PickedMediaEntity(
    @PrimaryKey val uri: String,
    val mediaType: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val selectedAtEpochMillis: Long,
    val sortOrder: Long,
    val ownsPersistableGrant: Boolean,
)
