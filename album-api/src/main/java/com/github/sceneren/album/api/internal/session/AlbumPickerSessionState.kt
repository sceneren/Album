package com.github.sceneren.album.api.internal.session

import android.net.Uri
import com.github.sceneren.album.api.AlbumPickerConfig

/** 描述正在进行的选择器会话所持久化的状态。 */
internal data class AlbumPickerSessionState(
    /** 选择器会话的唯一标识。 */
    val sessionId: String,
    /** 选择器会话配置。 */
    val config: AlbumPickerConfig,
    /** 当前已选择的媒体列表。 */
    val selected: List<AlbumPickerSelection> = emptyList(),
    /** 当前会话中的相机媒体列表。 */
    val cameraItems: List<AlbumPickerSelection> = emptyList(),
    /** 尚未完成的相机拍摄请求。 */
    val pendingCamera: AlbumPendingCameraCapture? = null,
    /** 媒体目录标识。 */
    val bucketId: Long = Long.MIN_VALUE,
    /** 当前预览媒体的 URI。 */
    val previewUri: Uri? = null,
)
