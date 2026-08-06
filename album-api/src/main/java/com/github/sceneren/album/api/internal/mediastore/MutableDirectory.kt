package com.github.sceneren.album.api.internal.mediastore

import android.net.Uri
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaType

/** Mutable accumulator used while streaming directory rows from MediaStore. */
internal data class MutableDirectory(
    val bucketId: Long,
    val bucketName: String?,
    val coverUri: Uri,
    val coverMediaType: AlbumMediaType,
    val firstMediaDate: Long,
    var mediaCount: Long = 0,
) {
    fun toAlbumDirectory() = AlbumDirectory(
        bucketId = bucketId,
        bucketName = bucketName,
        coverUri = coverUri,
        coverMediaType = coverMediaType,
        mediaCount = mediaCount,
    )
}
