package com.github.sceneren.album.api

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AlbumPickerIntentCodecTest` 覆盖的行为。 */
class AlbumPickerIntentCodecTest {
    @Test
    /** 验证 `roundTripsConfigUrisAndLeaseWithoutPaths` 所描述的场景。 */
    fun roundTripsConfigUrisAndLeaseWithoutPaths() {
        val config = AlbumPickerConfig(
            mediaFilter = AlbumMediaFilter.IMAGES_AND_VIDEOS,
            maxSelectionCount = 3,
            singleSelectionFinishMode = SingleSelectionFinishMode.IMMEDIATE,
            camera = AlbumCameraConfig(
                enabled = false,
                mixedMediaCaptureType = AlbumCameraCaptureType.VIDEO,
            ),
            compression = AlbumCompressionConfig(enabled = true, skipAtOrBelowKb = 256),
            showPermissionUpgrade = false,
        )
        val intent = AlbumPickerIntentCodec.putConfig(Intent(), config)
            .putExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID, "session")

        assertEquals(config, AlbumPickerIntentCodec.readConfig(intent))
        assertEquals("session", intent.getStringExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID))

        val result = AlbumPickerResult(
            listOf(
                AlbumPickerResultItem(
                    originalUri = Uri.parse("content://picked/1"),
                    resultUri = Uri.parse("content://copied/1"),
                    mediaType = AlbumMediaType.IMAGE,
                    compressionStatus = AlbumCompressionStatus.COMPRESSED,
                ),
            ),
            generatedMediaLeaseId = "lease-1",
        )

        val decoded = AlbumPickerIntentCodec.readResult(
            AlbumPickerIntentCodec.putResult(Intent(), result),
        )
        assertEquals(result, decoded)
        val encoded = AlbumPickerIntentCodec.putResult(Intent(), result)
        assertEquals("lease-1", decoded.generatedMediaLeaseId)
        assertTrue(encoded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(2, encoded.clipData!!.itemCount)
        assertTrue(encoded.extras!!.keySet().none { it.contains("paths") })
    }
}
