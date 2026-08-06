package com.github.sceneren.album.api.internal.picker

import android.database.Cursor
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumMediaType

internal fun Cursor.readOptionalMetadata(type: AlbumMediaType) = OptionalMetadata(
    displayName = stringOrNull(MediaStore.MediaColumns.DISPLAY_NAME),
    sizeBytes = positiveLongOrNull(MediaStore.MediaColumns.SIZE),
    width = positiveIntOrNull(MediaStore.MediaColumns.WIDTH),
    height = positiveIntOrNull(MediaStore.MediaColumns.HEIGHT),
    durationMillis = if (type == AlbumMediaType.VIDEO) {
        positiveLongOrNull(MediaStore.Video.VideoColumns.DURATION)
    } else {
        null
    },
)

internal fun Cursor.stringOrNull(columnName: String): String? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getString(column)
}

internal fun Cursor.positiveLongOrNull(columnName: String): Long? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getLong(column).takeIf { it > 0L }
}

internal fun Cursor.positiveIntOrNull(columnName: String): Int? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getInt(column).takeIf { it > 0 }
}
