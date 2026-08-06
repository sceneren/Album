package com.github.sceneren.album.api.internal.picker

import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.CancellationException

internal fun AlbumMediaFilter.allows(mediaType: AlbumMediaType): Boolean = when (this) {
    AlbumMediaFilter.IMAGES -> mediaType == AlbumMediaType.IMAGE
    AlbumMediaFilter.VIDEOS -> mediaType == AlbumMediaType.VIDEO
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> true
}

internal fun Exception.rethrowCancellation() {
    if (this is CancellationException) throw this
}

internal inline fun Exception.rethrowCancellationAfter(cleanup: () -> Unit) {
    if (this is CancellationException) {
        cleanup()
        throw this
    }
}
