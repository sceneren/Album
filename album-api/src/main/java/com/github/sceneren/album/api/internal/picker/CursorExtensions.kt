package com.github.sceneren.album.api.internal.picker

import android.database.Cursor
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.resolveAlbumMediaSpecialFormat

/** 获取 `readOptionalMetadata` 所需的数据。 */
internal fun Cursor.readOptionalMetadata(
    type: AlbumMediaType,
    mimeType: String,
): OptionalMetadata {
    val displayName = stringOrNull(MediaStore.MediaColumns.DISPLAY_NAME)
    return OptionalMetadata(
        displayName = displayName,
        sizeBytes = positiveLongOrNull(MediaStore.MediaColumns.SIZE),
        width = positiveIntOrNull(MediaStore.MediaColumns.WIDTH),
        height = positiveIntOrNull(MediaStore.MediaColumns.HEIGHT),
        durationMillis = if (type == AlbumMediaType.VIDEO) {
            positiveLongOrNull(MediaStore.Video.VideoColumns.DURATION)
        } else {
            null
        },
        specialFormat = resolveAlbumMediaSpecialFormat(
            specialFormatCode = intOrNull(SPECIAL_FORMAT_COLUMN)
                ?: intOrNull(STANDARD_MIME_TYPE_EXTENSION_COLUMN),
            mimeType = mimeType,
            displayName = displayName,
            xmp = blobOrNull(MediaStore.MediaColumns.XMP),
        ),
    )
}

/** 执行 `stringOrNull` 方法定义的处理。 */
internal fun Cursor.stringOrNull(columnName: String): String? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getString(column)
}

/** 执行 `positiveLongOrNull` 方法定义的处理。 */
internal fun Cursor.positiveLongOrNull(columnName: String): Long? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getLong(column).takeIf { it > 0L }
}

/** 执行 `positiveIntOrNull` 方法定义的处理。 */
internal fun Cursor.positiveIntOrNull(columnName: String): Int? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getInt(column).takeIf { it > 0 }
}

private fun Cursor.intOrNull(columnName: String): Int? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getInt(column)
}

private fun Cursor.blobOrNull(columnName: String): ByteArray? {
    val column = getColumnIndex(columnName)
    return if (column < 0 || isNull(column)) null else getBlob(column)
}

private const val SPECIAL_FORMAT_COLUMN = "_special_format"
private const val STANDARD_MIME_TYPE_EXTENSION_COLUMN = "standard_mime_type_extension"
