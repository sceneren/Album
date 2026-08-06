package com.github.sceneren.album.api

/** Outcome of applying the configured image compression policy to one result item. */
enum class AlbumCompressionStatus {
    DISABLED,
    SKIPPED_SIZE,
    COMPRESSED,
    NOT_APPLICABLE,
}
