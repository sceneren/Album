package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

/** 定义 `UriMetadataReader` 的能力边界。 */
internal interface UriMetadataReader {
    /** 丢弃旧的 MIME 与元数据，下次分页按需重新查询，不打开历史媒体内容流。 */
    fun invalidateCache() {}

    /** 执行 `requiredType` 方法定义的处理。 */
    fun requiredType(uri: Uri): AlbumMediaType

    /** 获取 `read` 所需的数据。 */
    fun read(
        uri: Uri,
        type: AlbumMediaType,
        allowStreamProbe: Boolean = true,
    ): PickedUriMetadata
}
