package com.github.sceneren.album.api.internal.session

import android.net.Uri
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumCameraConfig
import com.github.sceneren.album.api.AlbumCompressionConfig
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AlbumPickerSessionStoreTest` 覆盖的行为。 */
class AlbumPickerSessionStoreTest {
    @Test
    /** 验证 `persistsSelectionOrderCameraItemAndPendingCaptureAcrossStoreInstances` 所描述的场景。 */
    fun persistsSelectionOrderCameraItemAndPendingCaptureAcrossStoreInstances() {
        val context = RuntimeEnvironment.getApplication()
        val config = AlbumPickerConfig(
            mediaFilter = AlbumMediaFilter.IMAGES_AND_VIDEOS,
            maxSelectionCount = 3,
            camera = AlbumCameraConfig(
                enabled = true,
                mixedMediaCaptureType = AlbumCameraCaptureType.VIDEO,
            ),
            compression = AlbumCompressionConfig(enabled = true, skipAtOrBelowKb = 200),
        )
        val state = AlbumPickerSessionState(
            sessionId = "session-test",
            config = config,
            selected = listOf(
                AlbumPickerSelection(
                    uri = Uri.parse("content://selected/1"),
                    mediaType = AlbumMediaType.IMAGE,
                    displayName = "one.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 123,
                    width = 10,
                    height = 20,
                    durationMillis = null,
                    source = AlbumPickerItemSource.PHOTO_PICKER,
                ),
            ),
            cameraItems = emptyList(),
            pendingCamera = AlbumPendingCameraCapture(
                uri = Uri.parse("content://camera/1"),
                filePath = "/sdcard/Android/data/test/files/camera/1.mp4",
                mediaType = AlbumMediaType.VIDEO,
            ),
            bucketId = 42L,
            previewUri = Uri.parse("content://selected/1"),
        )

        AlbumPickerSessionStore(context).save(state)

        val restored = AlbumPickerSessionStore(context).load("session-test")
        assertNotNull(restored)
        assertEquals(state, restored)
    }
}
