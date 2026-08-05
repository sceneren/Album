package com.github.sceneren.album.api

import android.net.Uri

/** Media kinds supported by both Photo Picker and MediaStore queries. */
enum class AlbumMediaType {
    IMAGE,
    VIDEO,
}

/** Backing source selected for a feed. */
enum class AlbumMediaSource {
    MEDIA_STORE,
    PHOTO_PICKER,
    CAMERA,
}

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
