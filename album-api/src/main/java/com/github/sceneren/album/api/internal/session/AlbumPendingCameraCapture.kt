package com.github.sceneren.album.api.internal.session

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

/** Output reserved for a camera request that has not returned yet. */
internal data class AlbumPendingCameraCapture(
    val uri: Uri,
    val filePath: String,
    val mediaType: AlbumMediaType,
)
