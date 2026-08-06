package com.github.sceneren.album.api.internal.mediastore

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumMediaType

internal fun Cursor.readMediaType(column: Int): AlbumMediaType = when (getInt(column)) {
    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> AlbumMediaType.IMAGE
    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> AlbumMediaType.VIDEO
    else -> error("Unsupported MediaStore media type: ${getInt(column)}")
}

internal fun Cursor.stringOrNull(column: Int): String? =
    if (isNull(column)) null else getString(column)

internal fun Cursor.longOrNull(column: Int): Long? =
    if (isNull(column)) null else getLong(column)

internal fun Cursor.positiveLongOrNull(column: Int): Long? =
    longOrNull(column)?.takeIf { it > 0L }

internal fun Cursor.positiveIntOrNull(column: Int): Int? =
    if (isNull(column)) null else getInt(column).takeIf { it > 0 }

internal val AlbumMediaType.contentUri: Uri
    get() = when (this) {
        AlbumMediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        AlbumMediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
