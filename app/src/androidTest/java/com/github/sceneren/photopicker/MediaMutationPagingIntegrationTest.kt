package com.github.sceneren.photopicker

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.test.platform.app.InstrumentationRegistry
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.MediaAccessStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Real MediaStore/Paging; needs existing image permission and only modifies its own temporary media. */
class MediaMutationPagingIntegrationTest {
    @Test fun insertAndDeleteBetweenPagesRetainsEveryRemainingUriExactlyOnce() = runBlocking {
        assumeTrue("Requires Android 10+ scoped MediaStore", Build.VERSION.SDK_INT >= 29)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        assumeTrue("Grant image-library permission before running this test", context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
        val created = mutableListOf<Uri>()
        var job: Job? = null
        try {
            val folder = "${Environment.DIRECTORY_PICTURES}/AlbumPagingRegression_${System.nanoTime()}"
            val image = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            val bytes = ByteArrayOutputStream().use { output ->
                image.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.toByteArray()
            }
            image.recycle()
            fun insert(index: Int): Uri {
                val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "album-paging-$index.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }))
                created += uri
                resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return uri
            }
            val original = (0 until 130).map(::insert)
            val bucket = resolver.query(original[0], arrayOf(MediaStore.Images.ImageColumns.BUCKET_ID), null, null, null)!!.use {
                check(it.moveToFirst()); it.getLong(0)
            }
            val api = AlbumApi.create(context)
            assertEquals(MediaAccessStatus.FULL, api.getMediaAccessStatus())
            val tracker = api.createMediaChangeTracker()
            assertFalse(tracker.hasChanges().getOrThrow())
            val differ = AsyncPagingDataDiffer(
                object : DiffUtil.ItemCallback<AlbumMedia>() {
                    override fun areItemsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia) = oldItem.uri == newItem.uri
                    override fun areContentsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia) = oldItem == newItem
                },
                object : ListUpdateCallback {
                    override fun onInserted(position: Int, count: Int) = Unit
                    override fun onRemoved(position: Int, count: Int) = Unit
                    override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
                    override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
                },
            )
            job = launch(Dispatchers.Main) {
                api.getMediaFeed(AlbumMediaFilter.IMAGES, bucket, pageSize = 20).pagingData.collectLatest { differ.submitData(it) }
            }
            withTimeout(30_000) {
                while (differ.itemCount < 40) {
                    withContext(Dispatchers.Main) { if (differ.itemCount > 0) differ.getItem(differ.itemCount - 1) }
                    delay(50)
                }
            }
            repeat(3) { assertFalse(tracker.hasChanges().getOrThrow()) }
            resolver.delete(original.last(), null, null) // Before the current append offset.
            resolver.delete(original.first(), null, null) // Beyond the loaded window.
            val additions = listOf(insert(130), insert(131))
            assertTrue(tracker.hasChanges().getOrThrow())
            assertFalse(tracker.hasChanges().getOrThrow())
            val expected = (original.drop(1).dropLast(1) + additions).map { it.toString() }.toSet()
            withTimeout(30_000) {
                while (true) {
                    val done = withContext(Dispatchers.Main) {
                        if (differ.itemCount > 0) {
                            differ.getItem(0)
                            differ.getItem(differ.itemCount - 1)
                        }
                        val state = differ.loadStateFlow.first()
                        state.refresh is LoadState.NotLoading && state.append.endOfPaginationReached &&
                            state.prepend.endOfPaginationReached && differ.itemCount == expected.size
                    }
                    if (done) break
                    delay(50)
                }
            }
            val actual = differ.snapshot().items.map { it.uri.toString() }
            assertEquals(expected, actual.toSet())
            assertEquals(expected.size, actual.size)
            assertEquals(actual.size, actual.distinct().size)
            val ids = differ.snapshot().items.map { ContentUris.parseId(it.uri) }
            assertEquals(ids.sortedDescending(), ids)
        } finally {
            job?.cancelAndJoin()
            created.forEach { resolver.delete(it, null, null) }
        }
    }
}
