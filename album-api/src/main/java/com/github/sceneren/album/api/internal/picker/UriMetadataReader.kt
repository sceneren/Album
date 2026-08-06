package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

/** 定义 `UriMetadataReader` 的能力边界。 */
internal interface UriMetadataReader {
    /** 执行 `requiredType` 方法定义的处理。 */
    fun requiredType(uri: Uri): AlbumMediaType

    /** 获取 `read` 所需的数据。 */
    fun read(uri: Uri, type: AlbumMediaType): PickedUriMetadata
}
