package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.resolveAlbumMediaSpecialFormat
import java.io.ByteArrayOutputStream
import java.io.IOException

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

        val resolvedSpecialFormat = optional?.specialFormat?.takeIf { it != AlbumMediaSpecialFormat.NONE }
            ?: resolveAlbumMediaSpecialFormat(
                specialFormatCode = null,
                mimeType = mimeType,
                displayName = optional?.displayName,
                xmp = readMotionMetadataPrefix(
                    uri = uri,
                    mimeType = mimeType,
                ),
            )
        return PickedUriMetadata(
            uri = uri,
            mediaType = type,
            displayName = optional?.displayName,
            mimeType = mimeType,
            sizeBytes = optional?.sizeBytes,
            width = optional?.width,
            height = optional?.height,
            durationMillis = optional?.durationMillis,
            specialFormat = resolvedSpecialFormat,
        )
    }

    /**
     * 在提供方未返回实况标识时有限读取图片头部；读取受限不影响普通图片被选择。
     * @param uri 相册提供方返回的媒体地址。
     * @param mimeType 提供方声明的媒体 MIME。
     * @return 可用于识别 XMP 的有限字节；格式不适用或读取失败时返回 null。
     */
    private fun readMotionMetadataPrefix(uri: Uri, mimeType: String): ByteArray? {
        val supportsMotionMetadata = mimeType.equals(JPEG_MIME_TYPE, ignoreCase = true) ||
            mimeType.equals(HEIC_MIME_TYPE, ignoreCase = true) ||
            mimeType.equals(HEIF_MIME_TYPE, ignoreCase = true)
        if (!supportsMotionMetadata) return null
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(METADATA_BUFFER_BYTES)
                var remaining = MAX_METADATA_BYTES
                while (remaining > 0) {
                    val count = input.read(
                        buffer,
                        0,
                        minOf(buffer.size, remaining),
                    )
                    if (count <= 0) break
                    output.write(
                        buffer,
                        0,
                        count,
                    )
                    remaining -= count
                }
                output.toByteArray()
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `IMAGE_MIME_PREFIX` 对应的数据。 */
        const val IMAGE_MIME_PREFIX = "image/"
        /** 表示 `VIDEO_MIME_PREFIX` 对应的数据。 */
        const val VIDEO_MIME_PREFIX = "video/"
        /** JPEG 图片 MIME。 */
        const val JPEG_MIME_TYPE = "image/jpeg"
        /** HEIC 图片 MIME。 */
        const val HEIC_MIME_TYPE = "image/heic"
        /** HEIF 图片 MIME。 */
        const val HEIF_MIME_TYPE = "image/heif"
        /** 单次读取 XMP 候选数据的缓冲区大小。 */
        const val METADATA_BUFFER_BYTES = 16 * 1024
        /** 实况标识兜底探测的最大读取字节数。 */
        const val MAX_METADATA_BYTES = 1024 * 1024
    }
}
