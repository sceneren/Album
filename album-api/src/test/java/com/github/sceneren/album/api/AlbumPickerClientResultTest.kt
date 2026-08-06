package com.github.sceneren.album.api

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AlbumPickerClientResultTest` 覆盖的行为。 */
class AlbumPickerClientResultTest {
    @Test
    /** 验证 `confirmationReturnsCopiedPhotoPickerPathForEverySelectedFile` 所描述的场景。 */
    fun confirmationReturnsCopiedPhotoPickerPathForEverySelectedFile() {
        val context = RuntimeEnvironment.getApplication()
        val source = File.createTempFile("album-result", ".jpg", context.cacheDir).apply {
            writeText("picked")
        }
        val client = AlbumPickerClient(context)
        val snapshot = client.openSession(
            AlbumPickerConfig(AlbumMediaFilter.IMAGES, maxSelectionCount = 1),
        )

        val result = kotlinx.coroutines.runBlocking {
            client.toggleSelection(
                snapshot.sessionId,
                AlbumMedia(
                    uri = Uri.fromFile(source),
                    mediaType = AlbumMediaType.IMAGE,
                    displayName = "picked.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = source.length(),
                    dateAddedEpochSeconds = null,
                    dateModifiedEpochSeconds = null,
                    width = null,
                    height = null,
                    durationMillis = null,
                    bucketId = null,
                    bucketName = null,
                    selectedAtEpochMillis = null,
                    source = AlbumMediaSource.MEDIA_STORE,
                ),
            ).getOrThrow()
            client.confirm(snapshot.sessionId).getOrThrow()
        }
        val item = result.items.single()

        assertTrue(File(item.filePath).isFile)
        assertTrue(item.filePath.replace('\\', '/').contains("/photo_picker/"))
        assertEquals(item.filePath, item.originalFilePath)
        assertEquals("picked", File(item.filePath).readText())
    }
}
