package com.github.sceneren.album.api

interface AlbumPhotoPickerLauncher {
    val mediaFilter: AlbumMediaFilter

    fun launch()
}

sealed interface PhotoPickResult {
    data class Selected(val media: List<AlbumMedia>) : PhotoPickResult

    data object Cancelled : PhotoPickResult

    data class Failed(
        val reason: PhotoPickFailure,
        val cause: Throwable? = null,
    ) : PhotoPickResult
}

enum class PhotoPickFailure {
    SELECTION_LIMIT_EXCEEDED,
    MEDIA_TYPE_NOT_ALLOWED,
    PERSISTABLE_PERMISSION_FAILED,
    METADATA_READ_FAILED,
    DATABASE_WRITE_FAILED,
}
