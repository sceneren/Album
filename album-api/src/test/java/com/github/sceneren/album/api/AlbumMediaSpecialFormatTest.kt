package com.github.sceneren.album.api

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies provider metadata and backwards-compatible special-format fallbacks. */
class AlbumMediaSpecialFormatTest {
    @Test
    fun providerCodeTakesPriority() {
        assertEquals(
            AlbumMediaSpecialFormat.MOTION_PHOTO,
            resolveAlbumMediaSpecialFormat(
                specialFormatCode = 2,
                mimeType = "image/gif",
                displayName = "image.gif",
                xmp = null,
            ),
        )
    }

    @Test
    fun gifUsesMimeTypeOrFileExtensionFallback() {
        assertEquals(
            AlbumMediaSpecialFormat.GIF,
            resolveAlbumMediaSpecialFormat(null, "image/gif", "image", null),
        )
        assertEquals(
            AlbumMediaSpecialFormat.GIF,
            resolveAlbumMediaSpecialFormat(null, null, "IMAGE.GIF", null),
        )
    }

    @Test
    fun indexedXmpRecognizesMotionPhoto() {
        val xmp = "<rdf GCamera:MotionPhoto=\"1\" />".encodeToByteArray()

        assertEquals(
            AlbumMediaSpecialFormat.MOTION_PHOTO,
            resolveAlbumMediaSpecialFormat(null, "image/jpeg", "image.jpg", xmp),
        )
    }

    @Test
    fun legacyPixelFileNameRecognizesMotionPhoto() {
        assertEquals(
            AlbumMediaSpecialFormat.MOTION_PHOTO,
            resolveAlbumMediaSpecialFormat(null, "image/jpeg", "MVIMG_001.jpg", null),
        )
        assertEquals(
            AlbumMediaSpecialFormat.NONE,
            resolveAlbumMediaSpecialFormat(null, "image/png", "MVIMG_001.png", null),
        )
    }
}
