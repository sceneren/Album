package com.github.sceneren.album.api.internal.database

import androidx.core.net.toUri
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType

/** 将当前对象转换为 `toAlbumMedia` 对应的结果。 */
internal fun PickedMediaEntity.toAlbumMedia(): AlbumMedia = AlbumMedia(
    uri = uri.toUri(),
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

/** 执行 `databaseTypes` 方法定义的处理。 */
internal fun AlbumMediaFilter.databaseTypes(): List<String> = when (this) {
    AlbumMediaFilter.IMAGES -> listOf(AlbumMediaType.IMAGE.name)
    AlbumMediaFilter.VIDEOS -> listOf(AlbumMediaType.VIDEO.name)
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> AlbumMediaType.entries.map(AlbumMediaType::name)
}
