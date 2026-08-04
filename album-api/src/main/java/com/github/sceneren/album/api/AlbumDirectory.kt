package com.github.sceneren.album.api

import android.net.Uri

/** A MediaStore bucket summary for directory filtering. */
data class AlbumDirectory(
    val bucketId: Long,
    val bucketName: String?,
    val coverUri: Uri,
    val coverMediaType: AlbumMediaType,
    val mediaCount: Long,
) {
    companion object {
        /** Virtual bucket identifier representing all matching media. */
        const val ALL_BUCKET_ID: Long = Long.MIN_VALUE
    }
}
