package com.github.sceneren.album.api

/** Host-controlled launcher registered through [AlbumApi.registerPhotoPicker]. */
interface AlbumPhotoPickerLauncher {
    val mediaFilter: AlbumMediaFilter

    fun launch()
}

/** Result of processing and persisting a Photo Picker request. */
sealed interface PhotoPickResult {
    data class Selected(val media: List<AlbumMedia>) : PhotoPickResult

    data object Cancelled : PhotoPickResult

    data class Failed(
        val reason: PhotoPickFailure,
        val cause: Throwable? = null,
    ) : PhotoPickResult
}

/** Stable failure categories reported while validating or persisting picker results. */
enum class PhotoPickFailure {
    SELECTION_LIMIT_EXCEEDED,
    MEDIA_TYPE_NOT_ALLOWED,
    PERSISTABLE_PERMISSION_FAILED,
    METADATA_READ_FAILED,
    DATABASE_WRITE_FAILED,
}
