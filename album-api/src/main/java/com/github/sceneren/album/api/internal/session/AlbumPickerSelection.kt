package com.github.sceneren.album.api.internal.session

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

/** Session-local media selection, including camera file ownership metadata. */
internal data class AlbumPickerSelection(
    val uri: Uri,
    val mediaType: AlbumMediaType,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val source: AlbumPickerItemSource,
    val filePath: String? = null,
)
