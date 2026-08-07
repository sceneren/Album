package com.github.sceneren.album.ui.view

import android.net.Uri
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies lightweight grid metadata presentation for the View picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaGridPresentationTest {
    @Test
    fun videoDurationUsesMinuteAndHourFormats() {
        assertEquals("01:05", media(AlbumMediaType.VIDEO, duration = 65_000).videoDurationLabel())
        assertEquals(
            "1:01:01",
            media(AlbumMediaType.VIDEO, duration = 3_661_000).videoDurationLabel(),
        )
        assertEquals("--:--", media(AlbumMediaType.VIDEO).videoDurationLabel())
        assertNull(media(AlbumMediaType.IMAGE).videoDurationLabel())
    }

    @Test
    fun imageBadgesUseLiveGifThenLongImageRules() {
        assertEquals(
            MediaGridBadge.LIVE_PHOTO,
            media(
                specialFormat = AlbumMediaSpecialFormat.MOTION_PHOTO,
                width = 100,
                height = 400,
            ).gridBadge(),
        )
        assertEquals(
            MediaGridBadge.GIF,
            media(mimeType = "image/gif", width = 100, height = 400).gridBadge(),
        )
        assertEquals(
            MediaGridBadge.LONG_IMAGE,
            media(width = 100, height = 300).gridBadge(),
        )
        assertNull(media(width = 100, height = 299).gridBadge())
    }

    private fun media(
        mediaType: AlbumMediaType = AlbumMediaType.IMAGE,
        duration: Long? = null,
        mimeType: String? = "image/jpeg",
        width: Int? = null,
        height: Int? = null,
        specialFormat: AlbumMediaSpecialFormat = AlbumMediaSpecialFormat.NONE,
    ) = AlbumMedia(
        uri = Uri.parse("content://media/test"),
        mediaType = mediaType,
        displayName = "test",
        mimeType = mimeType,
        sizeBytes = null,
        dateAddedEpochSeconds = null,
        dateModifiedEpochSeconds = null,
        width = width,
        height = height,
        durationMillis = duration,
        bucketId = null,
        bucketName = null,
        selectedAtEpochMillis = null,
        source = AlbumMediaSource.MEDIA_STORE,
        specialFormat = specialFormat,
    )
}
