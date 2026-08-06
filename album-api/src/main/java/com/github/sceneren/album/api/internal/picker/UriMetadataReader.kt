package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

internal interface UriMetadataReader {
    fun requiredType(uri: Uri): AlbumMediaType

    fun read(uri: Uri, type: AlbumMediaType): PickedUriMetadata
}
