package com.github.sceneren.album.api

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun selectionChangesOnlyCopyFinalSelectionOnConfirmation() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val outputRoot = File(context.cacheDir, "selection-output")
        val sources = List(3) { index ->
            File.createTempFile("album-selection-$index", ".jpg", context.cacheDir).apply {
                writeText("image-$index")
            }
        }
        val client = AlbumPickerClient(context, externalRootOverride = outputRoot)
        val session = client.openSession(
            AlbumPickerConfig(AlbumMediaFilter.IMAGES, maxSelectionCount = 3),
        )
        assertFalse(outputRoot.exists())

        client.toggleSelection(session.sessionId, sources[0].asMedia()).getOrThrow()
        client.toggleSelection(session.sessionId, sources[1].asMedia()).getOrThrow()
        client.toggleSelection(session.sessionId, sources[0].asMedia()).getOrThrow()
        assertEquals(setOf(Uri.fromFile(sources[1])), client.snapshot(session.sessionId).selectedUris)
        assertFalse("Selection changes must not copy media", outputRoot.exists())

        val result = client.confirm(session.sessionId).getOrThrow()

        assertEquals(listOf(Uri.fromFile(sources[1])), result.items.map { it.originalUri })
        assertEquals("image-1", File(result.items.single().filePath).readText())
        assertEquals(1, outputRoot.walkTopDown().count { it.isFile })
    }

    @Test
    fun cancellingSelectionDoesNotCreateMediaCopies() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val outputRoot = File(context.cacheDir, "cancel-output")
        val source = File.createTempFile("album-cancel", ".jpg", context.cacheDir).apply {
            writeText("original")
        }
        val client = AlbumPickerClient(context, externalRootOverride = outputRoot)
        val session = client.openSession(AlbumPickerConfig(AlbumMediaFilter.IMAGES, maxSelectionCount = 1))

        client.toggleSelection(session.sessionId, source.asMedia()).getOrThrow()
        client.cancel(session.sessionId)

        assertFalse(outputRoot.exists())
        assertEquals("original", source.readText())
    }

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

    @Test
    fun repeatedConfirmationReusesMaterializedFileAcrossSessions() {
        val context = RuntimeEnvironment.getApplication()
        val source = File.createTempFile("album-reused-result", ".jpg", context.cacheDir).apply {
            writeText("reused")
        }
        val client = AlbumPickerClient(context)
        val media = AlbumMedia(
            uri = Uri.fromFile(source),
            mediaType = AlbumMediaType.IMAGE,
            displayName = "same.jpg",
            mimeType = "image/jpeg",
            sizeBytes = source.length(),
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = source.lastModified() / 1_000L,
            width = null,
            height = null,
            durationMillis = null,
            bucketId = null,
            bucketName = null,
            selectedAtEpochMillis = null,
            source = AlbumMediaSource.MEDIA_STORE,
        )

        val paths = kotlinx.coroutines.runBlocking {
            List(2) {
                val session = client.openSession(
                    AlbumPickerConfig(AlbumMediaFilter.IMAGES, maxSelectionCount = 1),
                )
                client.toggleSelection(session.sessionId, media).getOrThrow()
                client.confirm(session.sessionId).getOrThrow().items.single().filePath
            }
        }

        assertEquals(paths.first(), paths.last())
        assertEquals("reused", File(paths.first()).readText())
    }

    private fun File.asMedia() = AlbumMedia(
        uri = Uri.fromFile(this),
        mediaType = AlbumMediaType.IMAGE,
        displayName = name,
        mimeType = "image/jpeg",
        sizeBytes = length(),
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
}
