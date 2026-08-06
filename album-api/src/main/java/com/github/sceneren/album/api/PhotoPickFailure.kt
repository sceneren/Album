package com.github.sceneren.album.api

/** Stable failure categories reported while validating or persisting picker results. */
enum class PhotoPickFailure {
    SELECTION_LIMIT_EXCEEDED,
    MEDIA_TYPE_NOT_ALLOWED,
    PERSISTABLE_PERMISSION_FAILED,
    METADATA_READ_FAILED,
    DATABASE_WRITE_FAILED,
}
