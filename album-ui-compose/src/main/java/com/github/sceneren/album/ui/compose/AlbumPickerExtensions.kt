package com.github.sceneren.album.ui.compose

import androidx.compose.ui.graphics.Color
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource

/** 将当前对象转换为 `toCoverMedia` 对应的结果。 */
internal fun AlbumDirectory.toCoverMedia() = AlbumMedia(
    uri = coverUri,
    mediaType = coverMediaType,
    displayName = bucketName,
    mimeType = null,
    sizeBytes = null,
    dateAddedEpochSeconds = null,
    dateModifiedEpochSeconds = null,
    width = null,
    height = null,
    durationMillis = null,
    bucketId = bucketId,
    bucketName = bucketName,
    selectedAtEpochMillis = null,
    source = AlbumMediaSource.MEDIA_STORE,
)

/** 将当前对象转换为 `toColor` 对应的结果。 */
internal fun Int.toColor() = Color(this)
