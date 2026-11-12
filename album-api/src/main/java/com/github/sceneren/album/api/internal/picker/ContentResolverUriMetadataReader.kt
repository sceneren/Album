package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType
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
    private val unavailableUris = ConcurrentHashMap.newKeySet<Uri>()

    override fun invalidateCache() {
        synchronized(metadataCache) {
            cacheGeneration.incrementAndGet()
            metadataCache.clear()
            mimeCache.clear()
            unavailableUris.clear()
        }
    }

    override fun invalidateCache(uris: Set<Uri>) {
        synchronized(metadataCache) {
            cacheGeneration.incrementAndGet()
            uris.forEach { uri -> metadataCache.remove(uri); mimeCache.remove(uri) }
        }
    }

    override fun readFresh(uri: Uri): PickedUriMetadata = checkAvailability(uri) {
        val fresh = ContentResolverUriMetadataReader(resolver)
        // A deleted provider URI may lose its MIME before losing its persisted grant. Query
        // existence before deciding whether a missing MIME is deletion or a temporary failure.
        fresh.resolveMimeType(uri, metadataCache[uri]?.mimeType)
        fresh.readMetadata(uri, fresh.requiredType(uri), requireMetadata = true)
            .also { unavailableUris.remove(uri) }
    }

    /** 执行 `requiredType` 方法定义的处理。 */
    override fun requiredType(uri: Uri): AlbumMediaType {
        // A MIME string may survive deletion. Recheck a known missing URI before trusting it;
        // this also allows a restored file to re-enter the feed without changing its grant.
        if (uri in unavailableUris) return readFresh(uri).mediaType
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
    ): PickedUriMetadata = checkAvailability(uri) {
        if (uri in unavailableUris) readFresh(uri)
        else readMetadata(uri, type, requireMetadata = false)
    }

    private inline fun <T> checkAvailability(uri: Uri, read: () -> T): T = try {
        read()
    } catch (missing: FileNotFoundException) {
        unavailableUris += uri
        throw missing
    } catch (denied: SecurityException) {
        unavailableUris += uri
        throw denied
    }

    private fun readMetadata(
        uri: Uri,
        type: AlbumMediaType,
        requireMetadata: Boolean,
    ): PickedUriMetadata {
        val generation = cacheGeneration.get()
        metadataCache[uri]?.let { cached ->
            if (cached.mediaType == type) return cached
        }
        val mimeType = resolveMimeType(uri)
        val optional = queryOptionalMetadata(uri, type)
        if (optional == null) {
            // Only ambiguous providers need a descriptor probe. No bytes are read or decoded,
            // and normal metadata-backed history never opens a descriptor on foreground return.
            confirmReadable(uri)
            if (requireMetadata) throw IOException("Media metadata temporarily unavailable: $uri")
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
            specialFormat = optional?.specialFormat ?: AlbumMediaSpecialFormat.NONE,
            dateModifiedEpochSeconds = optional?.dateModifiedEpochSeconds,
            generationModified = optional?.generationModified,
        ).also { resolved ->
            synchronized(metadataCache) {
                if (cacheGeneration.get() == generation) metadataCache[uri] = resolved
            }
        }
    }

    private fun resolveMimeType(uri: Uri, knownMimeType: String? = null): String {
        val generation = cacheGeneration.get()
        mimeCache[uri]?.takeIf { it.first == generation }?.let { return it.second }
        val reported = try {
            resolver.getType(uri)
        } catch (_: IllegalArgumentException) {
            null // Some providers throw for a URI which was deleted rather than returning null.
        }
        val resolved = reported ?: queryMimeType(uri, knownMimeType)
        if (resolved == null) {
            confirmReadable(uri)
            throw IOException("Unable to determine media MIME type: $uri")
        }
        return resolved
            .also { mime ->
                if (cacheGeneration.get() == generation) mimeCache[uri] = generation to mime
            }
    }

    private fun queryMimeType(uri: Uri, knownMimeType: String?): String? {
        fun query(projection: Array<String>?): String? =
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) throw FileNotFoundException("Media no longer exists: $uri")
                cursor.stringOrNull(MediaStore.MediaColumns.MIME_TYPE) ?: knownMimeType
            }

        val projected = try {
            query(arrayOf(MediaStore.MediaColumns.MIME_TYPE))
        } catch (denied: SecurityException) {
            throw denied
        } catch (_: RuntimeException) {
            null
        }
        if (projected != null) return projected
        return try {
            query(null)
        } catch (denied: SecurityException) {
            throw denied
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun confirmReadable(uri: Uri) {
        val descriptor = resolver.openAssetFileDescriptor(uri, "r")
            ?: throw IOException("Media provider temporarily unavailable: $uri")
        descriptor.use { }
    }

    private fun queryOptionalMetadata(
        uri: Uri,
        type: AlbumMediaType,
    ): OptionalMetadata? {
        val projected = try {
            resolver.query(uri, METADATA_PROJECTION, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) throw FileNotFoundException("Media no longer exists: $uri")
                cursor.readOptionalMetadata(type)
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
                cursor.readOptionalMetadata(type)
            }
        } catch (denied: SecurityException) {
            throw denied
        } catch (_: RuntimeException) {
            null
        }
    }

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `IMAGE_MIME_PREFIX` 对应的数据。 */
        const val IMAGE_MIME_PREFIX = "image/"
        /** 表示 `VIDEO_MIME_PREFIX` 对应的数据。 */
        const val VIDEO_MIME_PREFIX = "video/"
        val METADATA_PROJECTION = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.GENERATION_MODIFIED,
        )
    }
}
