package com.github.sceneren.album.api

import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPickerClientResultTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun source(text: String = "original") = File.createTempFile("picker-source", ".jpg", context.cacheDir).apply { writeText(text) }
    private fun root() = File(context.cacheDir, "output-${java.util.UUID.randomUUID()}")

    @Test fun selectionReturnsOnlyFinalSourceUrisWithoutCreatingOriginalCopies() = runBlocking {
        val root = root()
        val client = AlbumPickerClient(context, root)
        val sources = List(3) { source("image-$it") }
        val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 3))
        client.toggleSelection(session.sessionId, sources[0].asPickerTestMedia()).getOrThrow()
        client.toggleSelection(session.sessionId, sources[1].asPickerTestMedia()).getOrThrow()
        client.toggleSelection(session.sessionId, sources[0].asPickerTestMedia()).getOrThrow()
        val result = client.confirm(session.sessionId).getOrThrow()
        assertEquals(listOf(Uri.fromFile(sources[1])), result.items.map { it.resultUri })
        assertEquals(result.items.single().originalUri, result.items.single().resultUri)
        assertNull(result.generatedMediaLeaseId)
        assertFalse(root.exists())
        assertEquals("image-1", context.contentResolver.openInputStream(result.items.single().resultUri)!!.bufferedReader().use { it.readText() })
    }

    @Test fun largeOriginalVideosAndImagesNeedNoDiskBudgetWhenNotCompressed() = runBlocking {
        for (type in AlbumMediaType.entries) {
            val root = root()
            val client = AlbumPickerClient(context, root, availableBytesOverride = { 0L })
            val original = source()
            val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES_AND_VIDEOS, 1))
            client.toggleSelection(session.sessionId, original.asPickerTestMedia(type, Long.MAX_VALUE)).getOrThrow()
            val result = client.confirm(session.sessionId).getOrThrow().items.single()
            assertEquals(Uri.fromFile(original), result.resultUri)
            assertFalse(root.exists())
            assertEquals(if (type == AlbumMediaType.VIDEO) AlbumCompressionStatus.NOT_APPLICABLE else AlbumCompressionStatus.DISABLED, result.compressionStatus)
        }
    }

    @Test fun compressionReadsTheSourceUriAndOnlyPublishesCompressedOutput() = runBlocking {
        val original = source()
        val root = root()
        var input: Uri? = null
        val client = AlbumPickerClient(context, root, availableBytesOverride = { 20L }, imageCompressor = { _, uri, directory, beforeWrite ->
            input = uri
            beforeWrite(4)
            File(directory, "compressed.jpg").apply { writeText("jpeg") }
        })
        val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1, compression = AlbumCompressionConfig(true, 0)))
        client.toggleSelection(session.sessionId, original.asPickerTestMedia()).getOrThrow()
        val result = client.confirm(session.sessionId).getOrThrow()
        assertEquals(Uri.fromFile(original), input)
        assertEquals(input, result.items.single().originalUri)
        assertNotEquals(input, result.items.single().resultUri)
        assertNotNull(result.generatedMediaLeaseId)
        assertEquals(listOf("jpeg"), root.walkTopDown().filter { it.isFile }.map { it.readText() }.toList())
        assertFalse(File(root, "photo_picker").exists())
        assertEquals("original", original.readText())
    }

    @Test fun oversizedCompressedImageIsRejectedBeforeWritingAndSessionCanRetry() = runBlocking {
        val original = source()
        val root = root()
        val client = AlbumPickerClient(context, root, availableBytesOverride = { 20L }, imageCompressor = { _, _, directory, beforeWrite ->
            beforeWrite(11)
            File(directory, "unexpected.jpg").apply { writeText("too large") }
        })
        val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1, compression = AlbumCompressionConfig(true, 0)))
        client.toggleSelection(session.sessionId, original.asPickerTestMedia()).getOrThrow()
        val failure = client.confirm(session.sessionId).exceptionOrNull()
        assertTrue(failure is AlbumPickerFileTooLargeException)
        assertEquals("选择的文件太大", failure?.message)
        assertTrue(root.walkTopDown().none { it.isFile })
        assertEquals(setOf(Uri.fromFile(original)), client.snapshotAsync(session.sessionId).selectedUris)
    }

    @Test fun nativeCodecFailureRemainsAResultFailureRatherThanCrashingTheCaller() = runBlocking {
        val original = source()
        val root = root()
        val client = AlbumPickerClient(context, root, availableBytesOverride = { 20L }, imageCompressor = { _, _, _, _ ->
            throw UnsatisfiedLinkError("test native codec unavailable")
        })
        val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1, compression = AlbumCompressionConfig(true, 0)))
        client.toggleSelection(session.sessionId, original.asPickerTestMedia()).getOrThrow()
        assertTrue(client.confirm(session.sessionId).exceptionOrNull() is UnsatisfiedLinkError)
        assertTrue(root.walkTopDown().none { it.isFile })
        assertEquals(setOf(Uri.fromFile(original)), client.snapshotAsync(session.sessionId).selectedUris)
    }

    @Test fun cancellingDoesNotCopyOrDeleteTheOriginal() = runBlocking {
        val original = source()
        val root = root()
        val client = AlbumPickerClient(context, root)
        val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1))
        client.toggleSelection(session.sessionId, original.asPickerTestMedia()).getOrThrow()
        client.cancel(session.sessionId)
        assertFalse(root.exists())
        assertEquals("original", original.readText())
        assertTrue(runCatching { client.snapshotAsync(session.sessionId) }.isFailure)
    }

    @Test fun cameraCaptureAndConfirmationReturnUrisWithoutExposingPathsOrCopying() = runBlocking {
        val client = AlbumPickerClient(context)
        val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1))
        val capture = client.prepareCamera(session.sessionId, AlbumMediaType.IMAGE).getOrThrow()
        context.contentResolver.openOutputStream(capture.uri)!!.use { it.write("camera".toByteArray()) }
        client.completeCamera(session.sessionId, true).getOrThrow()
        val result = client.confirm(session.sessionId).getOrThrow()
        assertEquals(capture.uri, result.items.single().resultUri)
        assertEquals("camera", context.contentResolver.openInputStream(capture.uri)!!.bufferedReader().use { it.readText() })
        assertFalse(File(context.getExternalFilesDir(null), "photo_picker").exists())
    }
}

internal fun File.asPickerTestMedia(type: AlbumMediaType = AlbumMediaType.IMAGE, bytes: Long? = length()) = AlbumMedia(
    uri = Uri.fromFile(this), mediaType = type, displayName = name,
    mimeType = if (type == AlbumMediaType.IMAGE) "image/jpeg" else "video/mp4", sizeBytes = bytes,
    dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null, width = null, height = null,
    durationMillis = null, bucketId = null, bucketName = null, selectedAtEpochMillis = null,
    source = AlbumMediaSource.MEDIA_STORE,
)
