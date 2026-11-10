package com.github.sceneren.album.ui.compose

import androidx.compose.ui.graphics.Color
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource

/** 根据当前可选择的媒体类型返回权限提示文案。 */
internal fun AlbumMediaFilter.permissionUpgradeMessageRes(): Int = when (this) {
    AlbumMediaFilter.IMAGES -> R.string.auc_denied_permission
    AlbumMediaFilter.VIDEOS -> R.string.auc_denied_permission_videos
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> R.string.auc_denied_permission_media
}

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
