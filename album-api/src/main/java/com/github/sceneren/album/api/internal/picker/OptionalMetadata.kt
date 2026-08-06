package com.github.sceneren.album.api.internal.picker

/** Optional columns read from a picker URI when its provider exposes them. */
internal data class OptionalMetadata(
    val displayName: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
)
