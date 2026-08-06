package com.github.sceneren.album.api

/** Result of processing and persisting a Photo Picker request. */
sealed interface PhotoPickResult {
    data class Selected(val media: List<AlbumMedia>) : PhotoPickResult

    data object Cancelled : PhotoPickResult

    data class Failed(
        val reason: PhotoPickFailure,
        val cause: Throwable? = null,
    ) : PhotoPickResult
}
