package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

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

    private companion object {
        const val IMAGE_MIME_PREFIX = "image/"
        const val VIDEO_MIME_PREFIX = "video/"
    }
}
