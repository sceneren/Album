package com.github.sceneren.album.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.internal.mediastore.MediaStoreDataSource
import com.github.sceneren.album.api.internal.permission.MediaAccessResolver
import com.github.sceneren.album.api.internal.picker.ContentResolverUriMetadataReader
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import com.github.sceneren.album.api.internal.picker.PickedUriMetadata
import com.github.sceneren.album.api.internal.picker.PhotoPickerResultProcessor
import com.github.sceneren.album.api.internal.picker.PickerRegistrar
import com.github.sceneren.album.api.internal.picker.UriAccessChecker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Uses the production resolver reader: deleted providers need not retain MIME metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class AuthorizedMediaDeletionTest {
    @Test fun deletionWithMissingMimeRefreshesAndExcludesOnlyDeletedUris() = runTest {
        for (status in listOf(MediaAccessStatus.DENIED, MediaAccessStatus.PARTIAL)) {
            for (type in AlbumMediaType.entries) {
                Fixture(status, type).use { fixture ->
                    val tracker = fixture.api.createMediaChangeTracker(fixture.filter)
                    assertFalse(tracker.hasChanges().getOrThrow())
                    assertEquals(fixture.uris, fixture.page().map { it.uri })
                    fixture.provider.deleted += fixture.uris.first()
                    assertTrue(tracker.hasChanges().getOrThrow())
                    assertEquals(fixture.uris.drop(1), fixture.page().map { it.uri })
                    assertFalse(tracker.hasChanges().getOrThrow())
                    assertTrue(fixture.provider.opened.isEmpty())
                    assertTrue(fixture.grants.released.isEmpty())
                    fixture.provider.deleted.clear()
                    assertTrue(tracker.hasChanges().getOrThrow())
                    assertEquals(fixture.uris, fixture.page().map { it.uri })
                }
            }
        }
    }

    @Test fun deletingAllGrantedFilesProducesAnEmptyPageEvenThoughGrantsRemain() = runTest {
        Fixture().use { fixture ->
            val tracker = fixture.api.createMediaChangeTracker()
            tracker.hasChanges().getOrThrow()
            fixture.page()
            fixture.provider.deleted += fixture.uris
            assertTrue(tracker.hasChanges().getOrThrow())
            assertTrue(fixture.page().isEmpty())
            assertEquals(fixture.uris.toSet(), fixture.grants.persistedReadUris())
        }
    }

    @Test fun nullMetadataForDeletedUriFallsBackToDescriptorWithoutReadingSurvivingFiles() = runTest {
        Fixture().use { fixture ->
            val tracker = fixture.api.createMediaChangeTracker()
            tracker.hasChanges().getOrThrow()
            fixture.page()
            fixture.provider.keepDeletedMime = true
            fixture.provider.returnNullForDeleted = true
            fixture.provider.deleted += fixture.uris.first()
            assertTrue(tracker.hasChanges().getOrThrow())
            assertEquals(fixture.uris.drop(1), fixture.page().map { it.uri })
            assertTrue(fixture.uris.first() in fixture.provider.opened)
            assertTrue(fixture.uris.last() !in fixture.provider.opened)
            assertFalse(tracker.hasChanges().getOrThrow())
            fixture.provider.deleted.clear()
            assertTrue(tracker.hasChanges().getOrThrow())
            assertEquals(fixture.uris, fixture.page().map { it.uri })
        }
    }

    @Test fun temporaryMissingMimeWithExistingMetadataDoesNotRemoveOrRefreshTheFile() = runTest {
        Fixture().use { fixture ->
            val tracker = fixture.api.createMediaChangeTracker()
            tracker.hasChanges().getOrThrow()
            val original = fixture.page()
            fixture.provider.hideMime += fixture.uris.first()
            assertFalse(tracker.hasChanges().getOrThrow())
            assertEquals(original, fixture.page())
            assertTrue(fixture.provider.opened.isEmpty())
        }
    }

    @Test fun transientMetadataFailureRetainsCacheAndBaselineInsteadOfRemovingReadableFiles() = runTest {
        Fixture().use { fixture ->
            val tracker = fixture.api.createMediaChangeTracker()
            tracker.hasChanges().getOrThrow()
            val original = fixture.page()
            fixture.provider.failMetadata += fixture.uris.first()
            assertTrue(tracker.hasChanges().isFailure)
            assertEquals(original, fixture.page())
            fixture.provider.failMetadata.clear()
            assertFalse(tracker.hasChanges().getOrThrow())
            assertEquals(original, fixture.page())
        }
    }

    private class Fixture(
        status: MediaAccessStatus = MediaAccessStatus.DENIED,
        type: AlbumMediaType = AlbumMediaType.IMAGE,
    ) : AutoCloseable {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val backingFile = File.createTempFile("authorized-metadata", ".bin", context.cacheDir).apply { writeText("readable") }
        val uris = listOf(Uri.parse("content://gallery-delete/1"), Uri.parse("content://gallery-delete/2"))
        val filter = if (type == AlbumMediaType.IMAGE) AlbumMediaFilter.IMAGES else AlbumMediaFilter.VIDEOS
        val provider = DeletingProvider(uris.toSet(), type, backingFile).apply {
            attachInfo(context, ProviderInfo().apply { authority = "gallery-delete" })
            ShadowContentResolver.registerProviderInternal("gallery-delete", this)
        }
        val grants = Grants(uris)
        private val cache = ConcurrentHashMap<Uri, PickedUriMetadata>()
        private val generation = AtomicLong()
        private val reader = ContentResolverUriMetadataReader(context.contentResolver, cache, generation)
        val api = AlbumApi(
            accessResolver = object : MediaAccessResolver { override fun resolve(filter: AlbumMediaFilter) = status },
            mediaStore = object : MediaStoreDataSource {
                override suspend fun getChangeSignature(mediaFilter: AlbumMediaFilter) = "unchanged"
                override suspend fun findPositions(mediaFilter: AlbumMediaFilter, bucketId: Long, uris: Set<Uri>) = emptyMap<Uri, Int>()
                override suspend fun getDirectories(mediaFilter: AlbumMediaFilter) = emptyList<AlbumDirectory>()
                override suspend fun loadPage(mediaFilter: AlbumMediaFilter, bucketId: Long, offset: Int, limit: Int) = emptyList<AlbumMedia>()
            },
            pickerRegistrar = object : PickerRegistrar {
                override fun register(activity: ComponentActivity, mediaFilter: AlbumMediaFilter, maxSelectionCount: Int?, onResult: (PhotoPickResult) -> Unit): AlbumPhotoPickerLauncher = error("Unused")
            },
            photoPickerResultProcessor = PhotoPickerResultProcessor(grants, reader),
            grantManager = grants,
            metadataReader = reader,
            metadataCache = cache,
            cacheGeneration = generation,
            uriAccessChecker = object : UriAccessChecker { override fun canRead(uri: Uri): Boolean = error("No eager reconciliation") },
        )
        suspend fun page() = api.loadMediaPage(filter).getOrThrow()
        override fun close() { backingFile.delete() }
    }

    private class Grants(private val uris: List<Uri>) : PersistableGrantManager {
        val released = mutableListOf<Uri>()
        override fun persistedReadUris() = uris.toSet()
        override fun takeRead(uri: Uri) = Unit
        override fun releaseRead(uri: Uri) { released += uri }
    }

    private class DeletingProvider(
        private val uris: Set<Uri>,
        private val type: AlbumMediaType,
        private val file: File,
    ) : ContentProvider() {
        val deleted = mutableSetOf<Uri>()
        val hideMime = mutableSetOf<Uri>()
        val failMetadata = mutableSetOf<Uri>()
        val opened = mutableSetOf<Uri>()
        var keepDeletedMime = false
        var returnNullForDeleted = false
        private val mime get() = if (type == AlbumMediaType.IMAGE) "image/jpeg" else "video/mp4"
        override fun onCreate() = true
        override fun getType(uri: Uri) = mime.takeIf { uri !in hideMime && (uri !in deleted || keepDeletedMime) }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (uri in failMetadata) error("Provider temporarily unavailable")
            if (uri in deleted && returnNullForDeleted) return null
            val columns = projection ?: arrayOf(MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE)
            return MatrixCursor(columns).apply {
                if (uri in uris && uri !in deleted) addRow(columns.map { column -> when (column) {
                    MediaStore.MediaColumns.MIME_TYPE -> mime
                    MediaStore.MediaColumns.DISPLAY_NAME -> "${uri.lastPathSegment}.jpg"
                    MediaStore.MediaColumns.SIZE -> 100L
                    MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT -> 100
                    MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.GENERATION_MODIFIED -> 1L
                    MediaStore.Video.VideoColumns.DURATION -> if (type == AlbumMediaType.VIDEO) 1000L else null
                    "_special_format" -> 0
                    else -> null
                } }.toTypedArray<Any?>())
            }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            opened += uri
            if (uri !in uris || uri in deleted) throw FileNotFoundException("Deleted")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
