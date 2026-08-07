package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType

/** 负责 `ContentResolverUriMetadataReader` 相关的数据与行为。 */
internal class ContentResolverUriMetadataReader(
    private val resolver: ContentResolver,
) : UriMetadataReader {
    /** 执行 `requiredType` 方法定义的处理。 */
    override fun requiredType(uri: Uri): AlbumMediaType {
        val mimeType = resolver.getType(uri)
            ?: throw IllegalArgumentException("Unable to determine media MIME type")
        return when {
            mimeType.startsWith(IMAGE_MIME_PREFIX) -> AlbumMediaType.IMAGE
            mimeType.startsWith(VIDEO_MIME_PREFIX) -> AlbumMediaType.VIDEO
            else -> throw IllegalArgumentException("Unsupported visual media MIME type: $mimeType")
        }
    }

    /** 获取 `read` 所需的数据。 */
    override fun read(uri: Uri, type: AlbumMediaType): PickedUriMetadata {
        val mimeType = resolver.getType(uri)
            ?: throw IllegalArgumentException("Unable to determine media MIME type")
        val optional = resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.readOptionalMetadata(type, mimeType)
        }

        return PickedUriMetadata(
            uri = uri,
            mediaType = type,
            displayName = optional?.displayName,
            mimeType = mimeType,
            sizeBytes = optional?.sizeBytes,
            width = optional?.width,
            height = optional?.height,
            durationMillis = optional?.durationMillis,
            specialFormat = optional?.specialFormat
                ?: AlbumMediaSpecialFormat.NONE,
        )
    }

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `IMAGE_MIME_PREFIX` 对应的数据。 */
        const val IMAGE_MIME_PREFIX = "image/"
        /** 表示 `VIDEO_MIME_PREFIX` 对应的数据。 */
        const val VIDEO_MIME_PREFIX = "video/"
    }
}
