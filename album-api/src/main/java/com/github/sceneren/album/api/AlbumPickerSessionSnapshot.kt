package com.github.sceneren.album.api

import android.net.Uri

/** UI 模块共享的选择会话数据。 */
data class AlbumPickerSessionSnapshot(
    val sessionId: String,
    val selectedItems: List<AlbumMedia>,
    val cameraItems: List<AlbumMedia>,
    val selectedUris: Set<Uri>,
    val bucketId: Long,
    val previewUri: Uri?,
    val hasPendingCamera: Boolean,
)
