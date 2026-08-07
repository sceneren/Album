package com.github.sceneren.album.api.internal.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "picked_media",
    indices = [Index(value = ["mediaType", "sortOrder", "uri"])],
)
/** 描述 `PickedMediaEntity` 数据。 */
internal data class PickedMediaEntity(
    /** 媒体内容 URI。 */
    @PrimaryKey val uri: String,
    /** 媒体类型。 */
    val mediaType: String,
    /** 媒体展示名称。 */
    val displayName: String?,
    /** 媒体的 MIME 类型。 */
    val mimeType: String?,
    /** 文件大小，单位为字节。 */
    val sizeBytes: Long?,
    /** 媒体像素宽度。 */
    val width: Int?,
    /** 媒体像素高度。 */
    val height: Int?,
    /** 媒体时长，单位为毫秒。 */
    val durationMillis: Long?,
    /** Persisted [com.github.sceneren.album.api.AlbumMediaSpecialFormat] name. */
    @ColumnInfo(defaultValue = "'NONE'") val specialFormat: String = "NONE",
    /** 媒体选择时间，单位为 Unix 毫秒。 */
    val selectedAtEpochMillis: Long,
    /** 持久化记录的稳定排序值。 */
    val sortOrder: Long,
    /** 本库是否持有该 URI 的可持久化授权。 */
    val ownsPersistableGrant: Boolean,
)
