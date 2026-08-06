package com.github.sceneren.album.api.internal.mediastore

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumMediaType

/** 获取 `readMediaType` 所需的数据。 */
internal fun Cursor.readMediaType(column: Int): AlbumMediaType = when (getInt(column)) {
    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> AlbumMediaType.IMAGE
    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> AlbumMediaType.VIDEO
    else -> error("Unsupported MediaStore media type: ${getInt(column)}")
}

/** 执行 `stringOrNull` 方法定义的处理。 */
internal fun Cursor.stringOrNull(column: Int): String? =
    if (isNull(column)) null else getString(column)

/** 执行 `longOrNull` 方法定义的处理。 */
internal fun Cursor.longOrNull(column: Int): Long? =
    if (isNull(column)) null else getLong(column)

/** 执行 `positiveLongOrNull` 方法定义的处理。 */
internal fun Cursor.positiveLongOrNull(column: Int): Long? =
    longOrNull(column)?.takeIf { it > 0L }

/** 执行 `positiveIntOrNull` 方法定义的处理。 */
internal fun Cursor.positiveIntOrNull(column: Int): Int? =
    if (isNull(column)) null else getInt(column).takeIf { it > 0 }

/** 表示 `AlbumMediaType` 对应的数据。 */
internal val AlbumMediaType.contentUri: Uri
    get() = when (this) {
        AlbumMediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        AlbumMediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
