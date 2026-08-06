package com.github.sceneren.album.ui.compose

import android.net.Uri
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.MediaAccessStatus
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
class AlbumPickerTitleTest {
    private val cameraDirectory = AlbumDirectory(
        bucketId = 42L,
        bucketName = "Camera",
        coverUri = Uri.parse("content://media/42"),
        coverMediaType = AlbumMediaType.IMAGE,
        mediaCount = 3L,
    )

    @Test
    fun incompleteAccessDoesNotUseDirectoryTitle() {
        assertNull(
            selectedTitleDirectory(MediaAccessStatus.DENIED, cameraDirectory.bucketId, listOf(cameraDirectory)),
        )
        assertNull(
            selectedTitleDirectory(MediaAccessStatus.PARTIAL, cameraDirectory.bucketId, listOf(cameraDirectory)),
        )
    }

    @Test
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
    fun selectedDirectoryDoesNotTriggerAnotherUpdate() {
        assertFalse(shouldUpdateDirectory(cameraDirectory.bucketId, cameraDirectory.bucketId))
        assertTrue(shouldUpdateDirectory(AlbumDirectory.ALL_BUCKET_ID, cameraDirectory.bucketId))
    }
}
