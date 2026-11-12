package com.github.sceneren.album.api

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPickerConcurrencyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun concurrentTogglesAcrossClientsAndBucketWritesLoseNoSelection() = runBlocking {
        val first = AlbumPickerClient(context)
        val second = AlbumPickerClient(context)
        val session = first.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 100))
        val media = List(80) { File(context.cacheDir, "selection-$it.jpg").asPickerTestMedia() }
        val changes = media.mapIndexed { index, item ->
            async(Dispatchers.Default) {
                (if (index % 2 == 0) first else second).toggleSelection(session.sessionId, item).getOrThrow()
            }
        }
        val bucket = async(Dispatchers.Default) { second.setBucket(session.sessionId, 123).getOrThrow() }
        changes.awaitAll()
        bucket.await()
        val snapshot = first.snapshotAsync(session.sessionId)
        assertEquals(media.map { it.uri }.toSet(), snapshot.selectedUris)
        assertEquals(123L, snapshot.bucketId)
    }

    @Test fun confirmSerializesWithToggleAndCancelAndNeverRevivesACompletedSession() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val original = File.createTempFile("confirm-source", ".jpg", context.cacheDir).apply { writeText("original") }
        val root = File(context.cacheDir, "concurrent-confirm")
        val first = AlbumPickerClient(context, root, availableBytesOverride = { 32_768L }, imageCompressor = { _, _, dir, beforeWrite ->
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            beforeWrite(4)
            File(dir, "compressed.jpg").apply { writeText("jpeg") }
        })
        val second = AlbumPickerClient(context, root)
        val session = first.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 2, compression = AlbumCompressionConfig(true, 0)))
        first.toggleSelection(session.sessionId, original.asPickerTestMedia()).getOrThrow()
        val confirming = async(Dispatchers.Default) { first.confirm(session.sessionId).getOrThrow() }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val toggling = async(start = CoroutineStart.UNDISPATCHED) {
                second.toggleSelection(session.sessionId, original.asPickerTestMedia())
            }
            val cancelling = async(start = CoroutineStart.UNDISPATCHED) { second.cancel(session.sessionId) }
            assertFalse(toggling.isCompleted)
            assertFalse(cancelling.isCompleted)
            release.countDown()
            assertEquals(1, confirming.await().items.size)
            assertTrue(toggling.await().isFailure)
            cancelling.await()
            assertTrue(runCatching { first.snapshotAsync(session.sessionId) }.isFailure)
        } finally {
            release.countDown()
        }
    }

    @Test fun cancellationAfterQueuedWritesLeavesNoSessionForLaterWrites() = runBlocking {
        val client = AlbumPickerClient(context)
        val session = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 100))
        val writes = List(60) { index -> async(start = CoroutineStart.UNDISPATCHED) {
            client.toggleSelection(session.sessionId, File(context.cacheDir, "$index.jpg").asPickerTestMedia())
        } }
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { client.cancel(session.sessionId) }
        writes.awaitAll()
        cancelled.await()
        assertTrue(client.toggleSelection(session.sessionId, File(context.cacheDir, "late.jpg").asPickerTestMedia()).isFailure)
        assertTrue(runCatching { client.snapshotAsync(session.sessionId) }.isFailure)
    }

    @Test fun sessionConstructionDoesNotTouchPreferencesAndAsyncReadsUseIo() = runBlocking {
        var reads = 0
        val offMain = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(Looper.myLooper() != Looper.getMainLooper()) { "Preferences must not be read on main" }
                reads++
                return super.getSharedPreferences(name, mode)
            }
        }
        val client = AlbumPickerClient(offMain)
        assertEquals(0, reads)
        val opened = client.openSessionAsync(AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1))
        assertEquals(opened, client.snapshotAsync(opened.sessionId))
        assertTrue(reads > 0)
    }
}
