package com.github.sceneren.album.ui.compose

import android.net.Uri
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumCameraConfig
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerSessionSnapshot
import com.github.sceneren.album.api.MediaAccessStatus
import com.github.sceneren.album.api.SingleSelectionFinishMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AlbumPickerTitleTest` 覆盖的行为。 */
class AlbumPickerTitleTest {
    private val cameraDirectory = AlbumDirectory(
        bucketId = 42L,
        bucketName = "Camera",
        coverUri = Uri.parse("content://media/42"),
        coverMediaType = AlbumMediaType.IMAGE,
        mediaCount = 3L,
    )

    @Test
    /** 验证 `incompleteAccessDoesNotUseDirectoryTitle` 所描述的场景。 */
    fun incompleteAccessDoesNotUseDirectoryTitle() {
        assertNull(
            selectedTitleDirectory(
                MediaAccessStatus.DENIED,
                cameraDirectory.bucketId,
                listOf(cameraDirectory),
            ),
        )
        assertNull(
            selectedTitleDirectory(
                MediaAccessStatus.PARTIAL,
                cameraDirectory.bucketId,
                listOf(cameraDirectory),
            ),
        )
    }

    @Test
    /** 验证 `fullAccessUsesSelectedDirectory` 所描述的场景。 */
    fun fullAccessUsesSelectedDirectory() {
        assertSame(
            cameraDirectory,
            selectedTitleDirectory(
                MediaAccessStatus.FULL,
                cameraDirectory.bucketId,
                listOf(cameraDirectory),
            ),
        )
    }

    @Test
    /** 验证 `allBucketUsesDefaultTitle` 所描述的场景。 */
    fun allBucketUsesDefaultTitle() {
        assertNull(
            selectedTitleDirectory(
                MediaAccessStatus.FULL,
                AlbumDirectory.ALL_BUCKET_ID,
                listOf(cameraDirectory),
            ),
        )
    }

    @Test
    /** 验证 `selectedDirectoryDoesNotTriggerAnotherUpdate` 所描述的场景。 */
    fun selectedDirectoryDoesNotTriggerAnotherUpdate() {
        assertFalse(shouldUpdateDirectory(cameraDirectory.bucketId, cameraDirectory.bucketId))
        assertTrue(shouldUpdateDirectory(AlbumDirectory.ALL_BUCKET_ID, cameraDirectory.bucketId))
    }

    @Test
    /** 验证 `mixedMediaCameraUsesConfiguredCaptureType` 所描述的场景。 */
    fun mixedMediaCameraUsesConfiguredCaptureType() {
        val photoConfig = AlbumPickerConfig(
            mediaFilter = AlbumMediaFilter.IMAGES_AND_VIDEOS,
            maxSelectionCount = 3,
            camera = AlbumCameraConfig(mixedMediaCaptureType = AlbumCameraCaptureType.PHOTO),
        )
        val videoConfig = photoConfig.copy(
            camera = AlbumCameraConfig(mixedMediaCaptureType = AlbumCameraCaptureType.VIDEO),
        )

        assertEquals(AlbumMediaType.IMAGE, photoConfig.cameraMediaType())
        assertEquals(AlbumMediaType.VIDEO, videoConfig.cameraMediaType())
    }

    @Test
    /** 验证 `immediateSingleSelectionAutoConfirmsOnlyAfterOneSelection` 所描述的场景。 */
    fun immediateSingleSelectionAutoConfirmsOnlyAfterOneSelection() {
        val config = AlbumPickerConfig(
            mediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount = 1,
            singleSelectionFinishMode = SingleSelectionFinishMode.IMMEDIATE,
        )
        val media = AlbumMedia(
            uri = Uri.parse("content://media/selected"),
            mediaType = AlbumMediaType.IMAGE,
            displayName = "selected.jpg",
            mimeType = "image/jpeg",
            sizeBytes = null,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            width = null,
            height = null,
            durationMillis = null,
            bucketId = null,
            bucketName = null,
            selectedAtEpochMillis = null,
            source = AlbumMediaSource.MEDIA_STORE,
        )
        val session = AlbumPickerSessionSnapshot(
            sessionId = "session",
            selectedItems = listOf(media),
            cameraItems = emptyList(),
            selectedUris = setOf(media.uri),
            bucketId = AlbumDirectory.ALL_BUCKET_ID,
            previewUri = media.uri,
            hasPendingCamera = false,
        )

        assertTrue(shouldAutoConfirm(config, session))
        assertFalse(
            shouldAutoConfirm(
                config.copy(singleSelectionFinishMode = SingleSelectionFinishMode.EXPLICIT_CONFIRM),
                session,
            ),
        )
    }
}
