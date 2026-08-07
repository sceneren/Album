package com.github.sceneren.album.api.internal.picker

import com.github.sceneren.album.api.AlbumMediaSpecialFormat

/** 描述选择器 URI 的提供方可用时读取到的可选字段。 */
internal data class OptionalMetadata(
    /** 媒体展示名称。 */
    val displayName: String?,
    /** 文件大小，单位为字节。 */
    val sizeBytes: Long?,
    /** 媒体像素宽度。 */
    val width: Int?,
    /** 媒体像素高度。 */
    val height: Int?,
    /** 媒体时长，单位为毫秒。 */
    val durationMillis: Long?,
    /** Provider-reported special visual format. */
    val specialFormat: AlbumMediaSpecialFormat,
)
