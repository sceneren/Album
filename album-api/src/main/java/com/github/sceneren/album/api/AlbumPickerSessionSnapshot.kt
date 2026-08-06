package com.github.sceneren.album.api

import android.net.Uri

/** UI 模块共享的选择会话数据。 */
data class AlbumPickerSessionSnapshot(
    /** 选择器会话的唯一标识。 */
    val sessionId: String,
    /** 表示 `selectedItems` 对应的数据。 */
    val selectedItems: List<AlbumMedia>,
    /** 当前会话中的相机媒体列表。 */
    val cameraItems: List<AlbumMedia>,
    /** 表示 `selectedUris` 对应的数据。 */
    val selectedUris: Set<Uri>,
    /** 媒体目录标识。 */
    val bucketId: Long,
    /** 当前预览媒体的 URI。 */
    val previewUri: Uri?,
    /** 表示 `hasPendingCamera` 对应的数据。 */
    val hasPendingCamera: Boolean,
)
