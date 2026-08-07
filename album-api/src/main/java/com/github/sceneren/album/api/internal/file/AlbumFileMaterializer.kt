package com.github.sceneren.album.api.internal.file

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Materializes selected content URIs as reusable app-owned files. */
internal class AlbumFileMaterializer(
    context: Context,
    private val resolver: ContentResolver,
    private val externalRoot: File? = context.applicationContext.getExternalFilesDir(null),
) {
    suspend fun copy(request: MaterializationRequest): MaterializedMedia =
        withContext(Dispatchers.IO) {
            copyOnIo(request)
        }

    suspend fun copyAll(items: List<MaterializationRequest>): List<MaterializedMedia> =
        withContext(Dispatchers.IO) {
            items.map { request -> copyOnIo(request) }
        }

    private suspend fun copyOnIo(request: MaterializationRequest): MaterializedMedia {
        val root = externalRoot ?: throw IOException("App-specific external storage is unavailable")
        val metadata = resolveSourceMetadata(request)
        val cacheKey = cacheKey(request.uri, metadata)
        val directory = File(File(root, PHOTO_PICKER_DIRECTORY), cacheKey.take(SHARD_LENGTH)).apply {
            if (!isDirectory && !mkdirs() && !isDirectory) {
                throw IOException("Unable to create $absolutePath")
            }
        }
        val target = File(directory, "$cacheKey.${safeExtension(request)}")
        val lockIndex = (cacheKey.hashCode() and Int.MAX_VALUE) % CACHE_LOCK_COUNT

        return cacheLocks[lockIndex].withLock {
            materializeLocked(request, metadata, target)
        }
    }

    private fun materializeLocked(
        request: MaterializationRequest,
        metadata: SourceMetadata,
        target: File,
    ): MaterializedMedia {
        if (isReusable(target, metadata.sizeBytes)) {
            return target.toMaterializedMedia(request.uri, reused = true)
        }

        val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
        return try {
            openInput(request.uri).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                    output.fd.sync()
                }
            }
            if (temporary.length() <= 0L) {
                throw IOException("Copied media is empty: ${request.uri}")
            }

            // Another writer outside this process may have committed while this copy was running.
            if (isReusable(target, metadata.sizeBytes)) {
                temporary.delete()
                return target.toMaterializedMedia(request.uri, reused = true)
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Unable to replace invalid cache file ${target.absolutePath}")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Unable to commit cache file ${target.absolutePath}")
            }
            target.toMaterializedMedia(request.uri, reused = false)
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun resolveSourceMetadata(request: MaterializationRequest): SourceMetadata {
        val queried = querySourceMetadata(request.uri)
        return SourceMetadata(
            generationModified = queried?.generationModified
                ?: request.generationModified?.takeIf { it > 0L },
            dateModifiedEpochSeconds = queried?.dateModifiedEpochSeconds
                ?: request.dateModifiedEpochSeconds?.takeIf { it > 0L },
            sizeBytes = queried?.sizeBytes ?: request.sizeBytes?.takeIf { it > 0L },
        )
    }

    private fun querySourceMetadata(uri: Uri): SourceMetadata? {
        val preferredProjection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.GENERATION_MODIFIED,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
        } else {
            arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED)
        }
        return querySourceMetadata(uri, preferredProjection)
            ?: querySourceMetadata(uri, projection = null)
    }

    private fun querySourceMetadata(uri: Uri, projection: Array<String>?): SourceMetadata? =
        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.readSourceMetadata().takeIf(SourceMetadata::hasVersionData)
            }
        } catch (_: RuntimeException) {
            // Providers may reject optional MediaStore columns; the caller tries a fallback.
            null
        }

    private fun Cursor.readSourceMetadata() = SourceMetadata(
        generationModified = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            positiveLongOrNull(MediaStore.MediaColumns.GENERATION_MODIFIED)
        } else {
            null
        },
        dateModifiedEpochSeconds = positiveLongOrNull(MediaStore.MediaColumns.DATE_MODIFIED),
        sizeBytes = positiveLongOrNull(MediaStore.MediaColumns.SIZE),
    )

    private fun Cursor.positiveLongOrNull(columnName: String): Long? {
        val column = getColumnIndex(columnName)
        return if (column < 0 || isNull(column)) null else getLong(column).takeIf { it > 0L }
    }

    private fun cacheKey(uri: Uri, metadata: SourceMetadata): String {
        val version = metadata.generationModified?.let { "generation:$it" }
            ?: metadata.dateModifiedEpochSeconds?.let { "modified:$it" }
            ?: "unversioned"
        val input = "$CACHE_FORMAT_VERSION\n$uri\n$version\n${metadata.sizeBytes ?: -1L}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    private fun safeExtension(request: MaterializationRequest): String {
        val mimeExtension = request.mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
            ?.takeIf(::isSafeExtension)
        if (mimeExtension != null) return mimeExtension.lowercase(Locale.ROOT)

        return request.displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.substringAfterLast('.', "")
            ?.takeIf(::isSafeExtension)
            ?.lowercase(Locale.ROOT)
            ?: DEFAULT_EXTENSION
    }

    private fun isSafeExtension(extension: String): Boolean =
        extension.length in 1..MAX_EXTENSION_LENGTH && extension.all { character ->
            character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9'
        }

    private fun isReusable(file: File, expectedSize: Long?): Boolean =
        file.isFile && file.length() > 0L && (expectedSize == null || file.length() == expectedSize)

    private fun File.toMaterializedMedia(uri: Uri, reused: Boolean) = MaterializedMedia(
        originalUri = uri,
        originalFilePath = absolutePath,
        filePath = absolutePath,
        sizeBytes = length(),
        reused = reused,
    )

    private fun openInput(uri: Uri) = resolver.openInputStream(uri)
        ?: throw IOException("Unable to read media URI: $uri")

    private data class SourceMetadata(
        val generationModified: Long?,
        val dateModifiedEpochSeconds: Long?,
        val sizeBytes: Long?,
    ) {
        fun hasVersionData(): Boolean =
            generationModified != null || dateModifiedEpochSeconds != null || sizeBytes != null
    }

    companion object {
        const val PHOTO_PICKER_DIRECTORY: String = "photo_picker"
        private const val BUFFER_SIZE = 64 * 1024
        private const val CACHE_FORMAT_VERSION = 1
        private const val CACHE_LOCK_COUNT = 64
        private const val SHARD_LENGTH = 2
        private const val MAX_EXTENSION_LENGTH = 10
        private const val DEFAULT_EXTENSION = "bin"
        private const val HEX_DIGITS = "0123456789abcdef"
        private val cacheLocks = Array(CACHE_LOCK_COUNT) { Mutex() }
    }
}
