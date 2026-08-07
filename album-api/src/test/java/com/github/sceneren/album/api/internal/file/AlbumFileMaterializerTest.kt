package com.github.sceneren.album.api.internal.file

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumFileMaterializerTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun copiesSelectedUriToDeterministicPhotoPickerPath() = runTest {
        val source = sourceFile("photo")
        val root = externalRoot("copy")
        val result = materializer(root).copy(request(source, displayName = "portrait #1.jpg"))

        assertEquals("photo", File(result.filePath).readText(StandardCharsets.UTF_8))
        assertTrue(result.filePath.replace('\\', '/').contains("/photo_picker/"))
        assertTrue(File(result.filePath).name.matches(Regex("[0-9a-f]{64}\\.jpg")))
        assertEquals(result.filePath, result.originalFilePath)
        assertFalse(result.reused)
    }

    @Test
    fun repeatedRequestReusesCacheWithoutReadingDeletedSource() = runTest {
        val source = sourceFile("cached")
        val root = externalRoot("reuse")
        val materializer = materializer(root)
        val request = request(source)

        val first = materializer.copy(request)
        assertTrue(source.delete())
        val second = materializer.copy(request)

        assertEquals(first.filePath, second.filePath)
        assertTrue(second.reused)
        assertEquals("cached", File(second.filePath).readText(StandardCharsets.UTF_8))
    }

    @Test
    fun recreatedSameNameWithNewGenerationIsCopiedAgain() = runTest {
        val source = sourceFile("first")
        val root = externalRoot("generation")
        val materializer = materializer(root)
        val firstRequest = request(source, generationModified = 10L)
        val first = materializer.copy(firstRequest)

        source.writeText("other", StandardCharsets.UTF_8)
        val second = materializer.copy(request(source, generationModified = 11L))

        assertNotEquals(first.filePath, second.filePath)
        assertFalse(second.reused)
        assertEquals("other", File(second.filePath).readText(StandardCharsets.UTF_8))
    }

    @Test
    @Config(sdk = [29])
    fun changedDateModifiedInvalidatesCacheWhenGenerationIsUnavailable() = runTest {
        val source = sourceFile("first")
        source.setLastModified(10_000L)
        val root = externalRoot("date-modified")
        val materializer = materializer(root)
        val first = materializer.copy(request(source, dateModifiedEpochSeconds = 10L))

        source.writeText("other", StandardCharsets.UTF_8)
        source.setLastModified(20_000L)
        val second = materializer.copy(request(source, dateModifiedEpochSeconds = 20L))

        assertNotEquals(first.filePath, second.filePath)
        assertFalse(second.reused)
        assertEquals("other", File(second.filePath).readText(StandardCharsets.UTF_8))
    }

    @Test
    fun sameDisplayNameFromDifferentUrisCreatesDifferentCacheEntries() = runTest {
        val firstSource = sourceFile("one")
        val secondSource = sourceFile("two")
        val root = externalRoot("same-name")
        val materializer = materializer(root)

        val first = materializer.copy(request(firstSource, displayName = "same.jpg"))
        val second = materializer.copy(request(secondSource, displayName = "same.jpg"))

        assertNotEquals(first.filePath, second.filePath)
        assertEquals("one", File(first.filePath).readText(StandardCharsets.UTF_8))
        assertEquals("two", File(second.filePath).readText(StandardCharsets.UTF_8))
    }

    @Test
    fun deletedCacheFileIsCopiedAgainToTheSameDeterministicPath() = runTest {
        val source = sourceFile("restore")
        val root = externalRoot("deleted-cache")
        val materializer = materializer(root)
        val request = request(source)
        val first = materializer.copy(request)

        assertTrue(File(first.filePath).delete())
        val second = materializer.copy(request)

        assertEquals(first.filePath, second.filePath)
        assertFalse(second.reused)
        assertEquals("restore", File(second.filePath).readText(StandardCharsets.UTF_8))
    }

    @Test
    fun invalidCacheLengthIsReplaced() = runTest {
        val source = sourceFile("original")
        val root = externalRoot("invalid-cache")
        val materializer = materializer(root)
        val request = request(source)
        val first = materializer.copy(request)
        File(first.filePath).writeText("bad", StandardCharsets.UTF_8)

        val second = materializer.copy(request)

        assertEquals(first.filePath, second.filePath)
        assertFalse(second.reused)
        assertEquals("original", File(second.filePath).readText(StandardCharsets.UTF_8))
    }

    @Test
    fun concurrentRequestsCommitOneCacheFile() = runTest {
        val source = sourceFile("concurrent")
        val root = externalRoot("concurrent")
        val materializer = materializer(root)
        val request = request(source)

        val results = listOf(
            async { materializer.copy(request) },
            async { materializer.copy(request) },
        ).awaitAll()

        assertEquals(1, results.count { it.reused })
        assertEquals(1, results.count { !it.reused })
        assertEquals(1, results.map { it.filePath }.distinct().size)
        assertTrue(cacheFiles(root).none { it.name.endsWith(".part") })
    }

    @Test
    fun failedBatchKeepsCommittedCacheAndRemovesPartFiles() = runTest {
        val first = sourceFile("first")
        val missing = Uri.parse("content://missing/${UUID.randomUUID()}")
        val root = externalRoot("failed-batch")
        val materializer = materializer(root)

        val result = runCatching {
            materializer.copyAll(
                listOf(
                    request(first),
                    MaterializationRequest(
                        uri = missing,
                        displayName = "missing.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 7L,
                    ),
                ),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(listOf("first"), cacheFiles(root).map { it.readText() })
        assertTrue(cacheFiles(root).none { it.name.endsWith(".part") })
    }

    private fun materializer(root: File) = AlbumFileMaterializer(
        context = context,
        resolver = context.contentResolver,
        externalRoot = root,
    )

    private fun request(
        source: File,
        displayName: String = "picked.jpg",
        generationModified: Long? = null,
        dateModifiedEpochSeconds: Long? = null,
    ) = MaterializationRequest(
        uri = Uri.fromFile(source),
        displayName = displayName,
        mimeType = "image/jpeg",
        sizeBytes = source.length(),
        generationModified = generationModified,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    )

    private fun sourceFile(content: String) = File.createTempFile(
        "album-source-",
        ".jpg",
        context.cacheDir,
    ).apply {
        writeText(content, StandardCharsets.UTF_8)
    }

    private fun externalRoot(name: String) = File(
        context.filesDir,
        "external-$name-${UUID.randomUUID()}",
    )

    private fun cacheFiles(root: File): List<File> =
        File(root, AlbumFileMaterializer.PHOTO_PICKER_DIRECTORY)
            .walkTopDown()
            .filter(File::isFile)
            .toList()
}
