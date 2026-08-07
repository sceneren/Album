package com.github.sceneren.album.api

import java.nio.charset.StandardCharsets
import java.util.Locale

/** Describes a visual media format that needs special handling beyond its MIME type. */
enum class AlbumMediaSpecialFormat {
    NONE,
    GIF,
    ANIMATED_WEBP,
    MOTION_PHOTO,
}

/** Resolves special-format metadata from MediaStore or a Photo Picker provider. */
internal fun resolveAlbumMediaSpecialFormat(
    specialFormatCode: Int?,
    mimeType: String?,
    displayName: String?,
    xmp: ByteArray?,
): AlbumMediaSpecialFormat {
    when (specialFormatCode) {
        SPECIAL_FORMAT_GIF -> return AlbumMediaSpecialFormat.GIF
        SPECIAL_FORMAT_MOTION_PHOTO -> return AlbumMediaSpecialFormat.MOTION_PHOTO
        SPECIAL_FORMAT_ANIMATED_WEBP -> return AlbumMediaSpecialFormat.ANIMATED_WEBP
    }

    val normalizedMimeType = mimeType?.lowercase(Locale.ROOT)
    if (normalizedMimeType == GIF_MIME_TYPE || displayName.hasExtension("gif")) {
        return AlbumMediaSpecialFormat.GIF
    }
    if (xmp.isMotionPhotoXmp() || displayName.isMotionPhotoFileName(normalizedMimeType)) {
        return AlbumMediaSpecialFormat.MOTION_PHOTO
    }
    return AlbumMediaSpecialFormat.NONE
}

private fun String?.hasExtension(extension: String): Boolean =
    this?.substringAfterLast('.', missingDelimiterValue = "")
        ?.equals(extension, ignoreCase = true) == true

private fun String?.isMotionPhotoFileName(mimeType: String?): Boolean =
    mimeType == JPEG_MIME_TYPE && this?.startsWith(MOTION_PHOTO_FILE_PREFIX, ignoreCase = true) == true

private fun ByteArray?.isMotionPhotoXmp(): Boolean {
    if (this == null || isEmpty()) return false
    val metadata = toString(StandardCharsets.UTF_8)
    return MOTION_PHOTO_XMP_MARKERS.any(metadata::contains)
}

private const val SPECIAL_FORMAT_GIF = 1
private const val SPECIAL_FORMAT_MOTION_PHOTO = 2
private const val SPECIAL_FORMAT_ANIMATED_WEBP = 3
private const val GIF_MIME_TYPE = "image/gif"
private const val JPEG_MIME_TYPE = "image/jpeg"
private const val MOTION_PHOTO_FILE_PREFIX = "MVIMG_"
private val MOTION_PHOTO_XMP_MARKERS = listOf(
    "MotionPhoto=\"1\"",
    "MotionPhoto>1<",
    "MicroVideo=\"1\"",
    "MicroVideo>1<",
)
