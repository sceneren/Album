package com.github.sceneren.album.api

import android.net.Uri

/** Metadata for one image or video URI; nullable fields were unavailable from its source. */
data class AlbumMedia(
    val uri: Uri,
    val mediaType: AlbumMediaType,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val dateAddedEpochSeconds: Long?,
    val dateModifiedEpochSeconds: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val bucketId: Long?,
    val bucketName: String?,
    val selectedAtEpochMillis: Long?,
    val source: AlbumMediaSource,
)
