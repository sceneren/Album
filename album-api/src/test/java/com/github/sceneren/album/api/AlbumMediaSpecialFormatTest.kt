package com.github.sceneren.album.api

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies suffix-only tags without guessing from ordinary image/video extensions. */
class AlbumMediaSpecialFormatTest {
    @Test
    fun gifExtensionIsCaseInsensitive() {
        for (name in listOf("image.gif", "IMAGE.GIF", "image.GiF")) {
            assertEquals(AlbumMediaSpecialFormat.GIF, resolveAlbumMediaSpecialFormat(name))
        }
    }

    @Test
    fun onlyDedicatedLivePhotoExtensionGetsMotionTag() {
        for (name in listOf("image.livp", "IMAGE.LIVP", "image.LiVp")) {
            assertEquals(AlbumMediaSpecialFormat.MOTION_PHOTO, resolveAlbumMediaSpecialFormat(name))
        }
    }

    @Test
    fun commonExtensionsAndNamePrefixesDoNotGuessMotionOrAnimation() {
        for (name in listOf(
            "image.jpg", "image.jpeg", "image.heic", "image.heif", "image.png", "image.webp",
            "image.avif", "video.mov", "video.mp4", "archive.zip", "MVIMG_001.jpg", "LIVE_001.heic",
        )) {
            assertEquals(name, AlbumMediaSpecialFormat.NONE, resolveAlbumMediaSpecialFormat(name))
        }
    }

    @Test
    fun missingOrNonFinalExtensionDoesNotCreateTag() {
        for (name in listOf(null, "", "image", "gif", "livp", "image.livp.jpg", "image.gif.backup", "image.livp.")) {
            assertEquals(name, AlbumMediaSpecialFormat.NONE, resolveAlbumMediaSpecialFormat(name))
        }
    }
}
