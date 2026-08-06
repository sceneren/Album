package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

/** 描述 `PickedUriMetadata` 数据。 */
internal data class PickedUriMetadata(
    /** 媒体内容 URI。 */
    val uri: Uri,
    /** 媒体类型。 */
    val mediaType: AlbumMediaType,
    /** 媒体展示名称。 */
    val displayName: String?,
    /** 媒体的 MIME 类型。 */
    val mimeType: String,
    /** 文件大小，单位为字节。 */
    val sizeBytes: Long?,
    /** 媒体像素宽度。 */
    val width: Int?,
    /** 媒体像素高度。 */
    val height: Int?,
    /** 媒体时长，单位为毫秒。 */
    val durationMillis: Long?,
)
