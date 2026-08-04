package com.github.sceneren.album.api

import android.net.Uri

data class AlbumDirectory(
    val bucketId: Long,
    val bucketName: String?,
    val coverUri: Uri,
    val coverMediaType: AlbumMediaType,
    val mediaCount: Long,
) {
    companion object {
        const val ALL_BUCKET_ID: Long = Long.MIN_VALUE
    }
}
