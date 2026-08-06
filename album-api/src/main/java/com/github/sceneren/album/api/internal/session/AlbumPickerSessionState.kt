package com.github.sceneren.album.api.internal.session

import android.net.Uri
import com.github.sceneren.album.api.AlbumPickerConfig

/** Persisted state for an in-progress picker session. */
internal data class AlbumPickerSessionState(
    val sessionId: String,
    val config: AlbumPickerConfig,
    val selected: List<AlbumPickerSelection> = emptyList(),
    val cameraItems: List<AlbumPickerSelection> = emptyList(),
    val pendingCamera: AlbumPendingCameraCapture? = null,
    val bucketId: Long = Long.MIN_VALUE,
    val previewUri: Uri? = null,
)
