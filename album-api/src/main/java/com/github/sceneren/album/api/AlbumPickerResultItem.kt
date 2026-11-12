package com.github.sceneren.album.api

import android.net.Uri

/** One URI result. Read/upload [resultUri] with ContentResolver; originals are never copied. */
data class AlbumPickerResultItem(
    /** 原始媒体内容 URI。 */
    val originalUri: Uri,
    /** 最终结果对应的 URI。 */
    val resultUri: Uri,
    /** 媒体类型。 */
    val mediaType: AlbumMediaType,
    /** 表示 `compressionStatus` 对应的数据。 */
    val compressionStatus: AlbumCompressionStatus,
)
