package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumMediaType

internal data class PickedUriMetadata(
    val uri: Uri,
    val mediaType: AlbumMediaType,
    val displayName: String?,
    val mimeType: String,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
)

internal interface UriMetadataReader {
    fun requiredType(uri: Uri): AlbumMediaType

    fun read(uri: Uri, type: AlbumMediaType): PickedUriMetadata
}

internal class ContentResolverUriMetadataReader(
    private val resolver: ContentResolver,
) : UriMetadataReader {
    override fun requiredType(uri: Uri): AlbumMediaType {
        val mimeType = resolver.getType(uri)
            ?: throw IllegalArgumentException("Unable to determine media MIME type")
        return when {
            mimeType.startsWith(IMAGE_MIME_PREFIX) -> AlbumMediaType.IMAGE
            mimeType.startsWith(VIDEO_MIME_PREFIX) -> AlbumMediaType.VIDEO
            else -> throw IllegalArgumentException("Unsupported visual media MIME type: $mimeType")
        }
    }

    override fun read(uri: Uri, type: AlbumMediaType): PickedUriMetadata {
        val mimeType = resolver.getType(uri)
            ?: throw IllegalArgumentException("Unable to determine media MIME type")
        val optional = resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.readOptionalMetadata(type)
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
        )
    }

    private fun Cursor.readOptionalMetadata(type: AlbumMediaType) = OptionalMetadata(
        displayName = stringOrNull(MediaStore.MediaColumns.DISPLAY_NAME),
        sizeBytes = positiveLongOrNull(MediaStore.MediaColumns.SIZE),
        width = positiveIntOrNull(MediaStore.MediaColumns.WIDTH),
        height = positiveIntOrNull(MediaStore.MediaColumns.HEIGHT),
        durationMillis = if (type == AlbumMediaType.VIDEO) {
            positiveLongOrNull(MediaStore.Video.VideoColumns.DURATION)
        } else {
            null
        },
    )

    private fun Cursor.stringOrNull(columnName: String): String? {
        val column = getColumnIndex(columnName)
        return if (column < 0 || isNull(column)) null else getString(column)
    }

    private fun Cursor.positiveLongOrNull(columnName: String): Long? {
        val column = getColumnIndex(columnName)
        return if (column < 0 || isNull(column)) null else getLong(column).takeIf { it > 0L }
    }

    private fun Cursor.positiveIntOrNull(columnName: String): Int? {
        val column = getColumnIndex(columnName)
        return if (column < 0 || isNull(column)) null else getInt(column).takeIf { it > 0 }
    }

    private data class OptionalMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
        val width: Int?,
        val height: Int?,
        val durationMillis: Long?,
    )

    private companion object {
        const val IMAGE_MIME_PREFIX = "image/"
        const val VIDEO_MIME_PREFIX = "video/"
    }
}
