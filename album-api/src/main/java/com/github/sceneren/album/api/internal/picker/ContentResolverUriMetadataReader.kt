package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.resolveAlbumMediaSpecialFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 负责 `ContentResolverUriMetadataReader` 相关的数据与行为。 */
internal class ContentResolverUriMetadataReader(
    private val resolver: ContentResolver,
    private val metadataCache: MutableMap<Uri, PickedUriMetadata> = ConcurrentHashMap(),
    private val cacheGeneration: AtomicLong = AtomicLong(),
) : UriMetadataReader {
    private val mimeCache = ConcurrentHashMap<Uri, Pair<Long, String>>()

    override fun invalidateCache() {
        synchronized(metadataCache) {
            cacheGeneration.incrementAndGet()
            metadataCache.clear()
            mimeCache.clear()
        }
    }

    /** 执行 `requiredType` 方法定义的处理。 */
    override fun requiredType(uri: Uri): AlbumMediaType {
        metadataCache[uri]?.let { cached -> return cached.mediaType }
        val mimeType = resolveMimeType(uri)
        return when {
            mimeType.startsWith(IMAGE_MIME_PREFIX) -> AlbumMediaType.IMAGE
            mimeType.startsWith(VIDEO_MIME_PREFIX) -> AlbumMediaType.VIDEO
            else -> throw IllegalArgumentException("Unsupported visual media MIME type: $mimeType")
        }
    }

    /** 获取 `read` 所需的数据。 */
    override fun read(
        uri: Uri,
        type: AlbumMediaType,
        allowStreamProbe: Boolean,
    ): PickedUriMetadata {
        val generation = cacheGeneration.get()
        metadataCache[uri]?.let { cached ->
            if (cached.mediaType == type) return cached
        }
        val mimeType = resolveMimeType(uri)
        val optional = queryOptionalMetadata(uri, type, mimeType)

        val resolvedSpecialFormat = when {
            optional != null &&
                (optional.specialFormat != AlbumMediaSpecialFormat.NONE || optional.hasProviderSpecialFormat) -> {
                optional.specialFormat
            }
            else -> resolveAlbumMediaSpecialFormat(
                specialFormatCode = null,
                mimeType = mimeType,
                displayName = optional?.displayName,
                xmp = if (allowStreamProbe) {
                    readMotionMetadataPrefix(
                        uri = uri,
                        mimeType = mimeType,
                    )
                } else {
                    null
                },
            )
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
            specialFormat = resolvedSpecialFormat,
        ).also { resolved ->
            synchronized(metadataCache) {
                if (cacheGeneration.get() == generation) metadataCache[uri] = resolved
            }
        }
    }

    private fun resolveMimeType(uri: Uri): String {
        val generation = cacheGeneration.get()
        mimeCache[uri]?.takeIf { it.first == generation }?.let { return it.second }
        return (resolver.getType(uri) ?: throw IllegalArgumentException("Unable to determine media MIME type"))
            .also { mime ->
                if (cacheGeneration.get() == generation) mimeCache[uri] = generation to mime
            }
    }

    private fun queryOptionalMetadata(
        uri: Uri,
        type: AlbumMediaType,
        mimeType: String,
    ): OptionalMetadata? {
        val projected = try {
            resolver.query(uri, METADATA_PROJECTION, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) throw FileNotFoundException("Media no longer exists: $uri")
                cursor.readOptionalMetadata(type, mimeType)
            }
        } catch (denied: SecurityException) {
            throw denied
        } catch (_: RuntimeException) {
            null
        }
        if (projected != null) return projected
        return try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) throw FileNotFoundException("Media no longer exists: $uri")
                cursor.readOptionalMetadata(type, mimeType)
            }
        } catch (denied: SecurityException) {
            throw denied
        } catch (_: RuntimeException) {
            null
        }
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
        val METADATA_PROJECTION = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION,
            "_special_format",
            "standard_mime_type_extension",
        )
    }
}
