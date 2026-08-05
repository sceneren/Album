package com.github.sceneren.album.api.internal.file

import android.net.Uri
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumFileMaterializerTest {
    @Test
    fun copiesSelectedUriToPhotoPickerDirectoryAndReturnsRealPath() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val source = File.createTempFile("album-source", ".jpg", context.cacheDir).apply {
            writeText("photo", StandardCharsets.UTF_8)
        }
        val materializer = AlbumFileMaterializer(
            context = context,
            resolver = context.contentResolver,
            externalRoot = File(context.filesDir, "external-files"),
        )

        val result = materializer.copy(
            uri = Uri.fromFile(source),
            displayName = "portrait.jpg",
        )

        assertEquals("photo", File(result.filePath).readText(StandardCharsets.UTF_8))
        assertTrue(result.filePath.replace('\\', '/').contains("/photo_picker/"))
        assertEquals(result.filePath, result.originalFilePath)
    }

    @Test
    fun failedBatchRemovesEveryPartFileAndCommittedFile() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val first = File.createTempFile("album-first", ".jpg", context.cacheDir).apply {
            writeText("first", StandardCharsets.UTF_8)
        }
        val missing = Uri.parse("content://missing/media")
        val materializer = AlbumFileMaterializer(
            context = context,
            resolver = context.contentResolver,
            externalRoot = File(context.filesDir, "external-files-batch"),
        )

        val directory = File(context.filesDir, "external-files-batch/photo_picker")
        runCatching {
            materializer.copyAll(
                listOf(
                    Uri.fromFile(first) to "first.jpg",
                    missing to "missing.jpg",
                ),
            )
        }

        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertFalse(directory.resolve("missing.jpg").exists())
    }
}
