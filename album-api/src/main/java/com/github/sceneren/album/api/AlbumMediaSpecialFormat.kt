package com.github.sceneren.album.api

import java.util.Locale

/** Describes a visual media format that needs special handling beyond its MIME type. */
enum class AlbumMediaSpecialFormat {
    NONE,
    GIF,
    ANIMATED_WEBP,
    MOTION_PHOTO,
}

/** Resolves only formats identifiable by their file extension, without inspecting media content. */
internal fun resolveAlbumMediaSpecialFormat(
    displayName: String?,
): AlbumMediaSpecialFormat = when (
    displayName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase(Locale.ROOT)
) {
    "gif" -> AlbumMediaSpecialFormat.GIF
    "livp" -> AlbumMediaSpecialFormat.MOTION_PHOTO
    else -> AlbumMediaSpecialFormat.NONE
}
