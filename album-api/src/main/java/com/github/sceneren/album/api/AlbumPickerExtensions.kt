package com.github.sceneren.album.api

import com.github.sceneren.album.api.internal.session.AlbumPickerItemSource
import com.github.sceneren.album.api.internal.session.AlbumPickerSelection
import com.github.sceneren.album.api.internal.session.AlbumPickerSessionState

/** 将当前对象转换为 `toSnapshot` 对应的结果。 */
internal fun AlbumPickerSessionState.toSnapshot() = AlbumPickerSessionSnapshot(
    sessionId = sessionId,
    selectedItems = selected.map { it.toAlbumMedia() },
    cameraItems = cameraItems.map { it.toAlbumMedia() },
    selectedUris = selected.mapTo(linkedSetOf()) { it.uri },
    bucketId = bucketId,
    previewUri = previewUri,
    hasPendingCamera = pendingCamera != null,
)

/** 将当前对象转换为 `toSelection` 对应的结果。 */
internal fun AlbumMedia.toSelection() = AlbumPickerSelection(
    uri = uri,
    mediaType = mediaType,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    source = when (source) {
        AlbumMediaSource.MEDIA_STORE -> AlbumPickerItemSource.MEDIA_STORE
        AlbumMediaSource.PHOTO_PICKER -> AlbumPickerItemSource.PHOTO_PICKER
        AlbumMediaSource.CAMERA -> AlbumPickerItemSource.CAMERA
    },
)

/** 将当前对象转换为 `toAlbumMedia` 对应的结果。 */
internal fun AlbumPickerSelection.toAlbumMedia() = AlbumMedia(
    uri = uri,
    mediaType = mediaType,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateAddedEpochSeconds = null,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    width = width,
    height = height,
    durationMillis = durationMillis,
    bucketId = null,
    bucketName = null,
    selectedAtEpochMillis = null,
    source = when (source) {
        AlbumPickerItemSource.MEDIA_STORE -> AlbumMediaSource.MEDIA_STORE
        AlbumPickerItemSource.PHOTO_PICKER -> AlbumMediaSource.PHOTO_PICKER
        AlbumPickerItemSource.CAMERA -> AlbumMediaSource.CAMERA
    },
)
