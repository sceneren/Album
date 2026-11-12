package com.github.sceneren.album.api.internal.file

import com.github.sceneren.album.api.AlbumCompressionStatus
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerFileTooLargeException
import com.github.sceneren.album.api.AlbumPickerResult
import com.github.sceneren.album.api.AlbumPickerResultItem
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
class AlbumGeneratedMediaCacheTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun root() = File(context.cacheDir, "quota-${java.util.UUID.randomUUID()}")
    private fun file(root: File, name: String, size: Int, time: Long) = File(root, name).apply {
        parentFile!!.mkdirs()
        writeBytes(ByteArray(size))
        setLastModified(time)
    }
    private fun item(file: File) = AlbumPickerResultItem(Uri.parse("content://source/image"), Uri.fromFile(file),
        AlbumMediaType.IMAGE, AlbumCompressionStatus.COMPRESSED)

    private suspend fun write(cache: AlbumGeneratedMediaCache, root: File, name: String, bytes: Int): AlbumPickerResult =
        cache.withBudget { budget ->
            budget.checkFileSize(bytes.toLong())
            budget.reserve(bytes.toLong())
            val output = file(root, "luban/$name.jpg", bytes, System.currentTimeMillis())
            budget.protect(output.absolutePath)
            budget.publish(listOf(item(output)), listOf(output))
        }

    @Test fun budgetUsesHalfAvailableStorageAndEvictsOldestUnusedFileFirst() = runBlocking {
        val root = root()
        val oldest = file(root, "luban/old.jpg", 60, 1_000)
        val newer = file(root, "luban/new.jpg", 20, 2_000)
        val camera = file(root, "camera/original.mp4", 200, 0)
        val cache = AlbumGeneratedMediaCache(context, root) { 200L }
        cache.withBudget { assertEquals(100L, it.capacityBytes) }
        write(cache, root, "current", 30)
        assertFalse(oldest.exists())
        assertTrue(newer.exists())
        assertTrue(camera.exists())
        assertEquals(50L, File(root, "luban").listFiles()!!.sumOf { it.length() })
    }

    @Test fun publishedResultsRemainProtectedUntilEverySharingLeaseIsReleased() = runBlocking {
        val root = root()
        val cache = AlbumGeneratedMediaCache(context, root) { 200L }
        val first = write(cache, root, "shared", 20)
        val shared = File(root, "luban/shared.jpg")
        val second = cache.withBudget { budget ->
            budget.protect(shared.absolutePath)
            budget.publish(listOf(item(shared)), listOf(shared))
        }
        cache.release(first)
        cache.release(first)
        assertTrue(runCatching { write(cache, root, "blocked", 90) }.isFailure)
        assertTrue(shared.exists())
        // A different cache/client instance must see the durable lease too.
        val other = AlbumGeneratedMediaCache(context, root) { 200L }
        assertTrue(runCatching { write(other, root, "also-blocked", 90) }.isFailure)
        other.release(second)
        write(other, root, "allowed", 90)
        assertFalse(shared.exists())
        assertTrue(File(root, "luban/allowed.jpg").isFile)
    }

    @Test fun oversizedOutputFailsBeforeWritingAndDoesNotEvictAnUnusedFile() = runBlocking {
        val root = root()
        val old = file(root, "luban/keep.jpg", 20, 0)
        val cache = AlbumGeneratedMediaCache(context, root) { 200L }
        val failure = runCatching { write(cache, root, "too-large", 101) }.exceptionOrNull()
        assertTrue(failure is AlbumPickerFileTooLargeException)
        assertEquals("选择的文件太大", failure?.message)
        assertTrue(old.exists())
        assertFalse(File(root, "luban/too-large.jpg").exists())
    }

    @Test fun currentBatchOutputsAreNotEvictedWhenTheNextOutputNeedsSpace() = runBlocking {
        val root = root()
        val cache = AlbumGeneratedMediaCache(context, root) { 200L }
        cache.withBudget { budget ->
            budget.reserve(70)
            val current = file(root, "luban/current.jpg", 70, 0)
            budget.protect(current.absolutePath)
            assertTrue(runCatching { budget.reserve(40) }.isFailure)
            assertTrue(current.exists())
        }
    }

    @Test fun releasingAConsumedResultMakesItsLastUseNewerThanOlderIdleFiles() = runBlocking {
        val root = root()
        val cache = AlbumGeneratedMediaCache(context, root) { 200L }
        val result = write(cache, root, "used", 20)
        val used = File(root, "luban/used.jpg").apply { setLastModified(1_000) }
        val idle = file(root, "luban/idle.jpg", 20, 2_000)
        cache.release(result)
        write(cache, root, "next", 70)
        assertTrue(used.exists())
        assertFalse(idle.exists())
    }
}
