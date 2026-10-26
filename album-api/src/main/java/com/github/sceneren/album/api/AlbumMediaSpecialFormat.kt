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
    if (xmp.isMotionPhotoXmp(normalizedMimeType)) {
        return AlbumMediaSpecialFormat.MOTION_PHOTO
    }
    return AlbumMediaSpecialFormat.NONE
}

private fun String?.hasExtension(extension: String): Boolean =
    this?.substringAfterLast('.', missingDelimiterValue = "")
        ?.equals(extension, ignoreCase = true) == true

private fun ByteArray?.isMotionPhotoXmp(mimeType: String?): Boolean {
    if (mimeType != JPEG_MIME_TYPE && mimeType != HEIC_MIME_TYPE && mimeType != HEIF_MIME_TYPE) return false
    if (this == null || isEmpty()) return false
    val metadata = toString(StandardCharsets.UTF_8)
    return MOTION_PHOTO_XMP_ATTRIBUTE.containsMatchIn(metadata) ||
        MOTION_PHOTO_XMP_ELEMENT.containsMatchIn(metadata)
}

private const val SPECIAL_FORMAT_GIF = 1
private const val SPECIAL_FORMAT_MOTION_PHOTO = 2
private const val SPECIAL_FORMAT_ANIMATED_WEBP = 3
private const val GIF_MIME_TYPE = "image/gif"
private const val JPEG_MIME_TYPE = "image/jpeg"
private const val HEIC_MIME_TYPE = "image/heic"
private const val HEIF_MIME_TYPE = "image/heif"
private val MOTION_PHOTO_XMP_ATTRIBUTE = Regex(
    "(?:^|[\\s:<])(?:MotionPhoto|MicroVideo)(?:Version)?\\s*=\\s*(['\\\"]?)1\\1(?:\\s|/?>)",
    RegexOption.IGNORE_CASE,
)
private val MOTION_PHOTO_XMP_ELEMENT = Regex(
    "<(?:[\\w.-]+:)?(?:MotionPhoto|MicroVideo)(?:Version)?\\s*>\\s*1\\s*</",
    RegexOption.IGNORE_CASE,
)
