package com.github.sceneren.album.api

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import com.github.sceneren.album.api.internal.database.PickedMediaDraft
import com.github.sceneren.album.api.internal.database.PickedMediaEntity
import com.github.sceneren.album.api.internal.database.PickedMediaStore
import com.github.sceneren.album.api.internal.mediastore.MediaStoreDataSource
import com.github.sceneren.album.api.internal.permission.MediaAccessResolver
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import com.github.sceneren.album.api.internal.picker.PickerRegistrar
import com.github.sceneren.album.api.internal.picker.UriAccessChecker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumApiTest {
    private lateinit var permissions: FakeMediaAccessResolver
    private lateinit var mediaStore: FakeMediaStoreDataSource
    private lateinit var pickedStore: FakePickedMediaStore
    private lateinit var grants: FakeGrantManager
    private lateinit var accessChecker: FakeUriAccessChecker
    private lateinit var api: AlbumApi

    @Before
    fun setUp() {
        permissions = FakeMediaAccessResolver()
        mediaStore = FakeMediaStoreDataSource()
        pickedStore = FakePickedMediaStore()
        grants = FakeGrantManager()
        accessChecker = FakeUriAccessChecker()
        api = AlbumApi(
            accessResolver = permissions,
            mediaStore = mediaStore,
            pickedStore = pickedStore,
            pickerRegistrar = FakePickerRegistrar(),
            grantManager = grants,
            uriAccessChecker = accessChecker,
        )
    }

    @Test
    fun fullRoutesToMediaStoreButPartialRoutesToRoom() = runTest {
        permissions.result = MediaAccessStatus.FULL
        val full = api.getMediaFeed(AlbumMediaFilter.VIDEOS, pageSize = 25)
        assertEquals(AlbumMediaSource.MEDIA_STORE, full.source)

        permissions.result = MediaAccessStatus.PARTIAL
        val partial = api.getMediaFeed(AlbumMediaFilter.VIDEOS, pageSize = 25)
        assertEquals(AlbumMediaSource.PHOTO_PICKER, partial.source)
        partial.pagingData.asSnapshot()
        assertEquals(AlbumMediaFilter.VIDEOS, pickedStore.lastPagingFilter)
    }

    @Test
    fun deniedAlsoRoutesToPersistedPickerFeed() {
        permissions.result = MediaAccessStatus.DENIED

        val feed = api.getMediaFeed(AlbumMediaFilter.IMAGES)

        assertEquals(AlbumMediaSource.PHOTO_PICKER, feed.source)
        assertEquals(MediaAccessStatus.DENIED, feed.accessStatus)
    }

    @Test
    fun partialDirectoriesAreEmptyWithoutMediaStoreQuery() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL

        assertEquals(emptyList<AlbumDirectory>(), api.getMediaDirectories().getOrThrow())
        assertEquals(0, mediaStore.directoryCalls)
    }

    @Test
    fun fullDirectoriesUseMediaStore() = runTest {
        permissions.result = MediaAccessStatus.FULL

        api.getMediaDirectories(AlbumMediaFilter.VIDEOS).getOrThrow()

        assertEquals(1, mediaStore.directoryCalls)
        assertEquals(AlbumMediaFilter.VIDEOS, mediaStore.lastDirectoryFilter)
    }

    @Test
    fun invalidPageSizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            api.getMediaFeed(pageSize = 0)
        }
    }

    @Test
    fun removeReleasesOnlyLibraryOwnedGrant() = runTest {
        pickedStore.seed(entity("owned", ownsGrant = true))
        pickedStore.seed(entity("host", ownsGrant = false))

        assertTrue(api.removePersistedSelection(uri("owned")).getOrThrow())
        assertTrue(api.removePersistedSelection(uri("host")).getOrThrow())

        assertEquals(listOf(uri("owned")), grants.released)
        assertFalse(pickedStore.rows.containsKey(uri("owned").toString()))
    }

    @Test
    fun clearReturnsCountAndReleasesOwnedGrants() = runTest {
        pickedStore.seed(entity("owned", ownsGrant = true))
        pickedStore.seed(entity("host", ownsGrant = false))

        assertEquals(2, api.clearPersistedSelections().getOrThrow())

        assertTrue(pickedStore.rows.isEmpty())
        assertEquals(listOf(uri("owned")), grants.released)
    }

    @Test
    fun reconcileRemovesMissingAndUnreadableUris() = runTest {
        pickedStore.seed(entity("keep", ownsGrant = true))
        pickedStore.seed(entity("missing", ownsGrant = true))
        pickedStore.seed(entity("unreadable", ownsGrant = true))
        grants.persisted += uri("keep")
        grants.persisted += uri("unreadable")
        accessChecker.readable += uri("keep")

        assertEquals(2, api.reconcilePersistedSelections().getOrThrow())

        assertEquals(listOf("content://picker/keep"), pickedStore.rows.keys.toList())
        assertEquals(listOf(uri("unreadable")), grants.released)
    }

    private class FakeMediaAccessResolver : MediaAccessResolver {
        var result: MediaAccessStatus = MediaAccessStatus.DENIED

        override fun resolve(filter: AlbumMediaFilter): MediaAccessStatus = result
    }

    private class FakeMediaStoreDataSource : MediaStoreDataSource {
        var directoryCalls = 0
        var lastDirectoryFilter: AlbumMediaFilter? = null

        override suspend fun loadPage(
            mediaFilter: AlbumMediaFilter,
            bucketId: Long,
            offset: Int,
            limit: Int,
        ): List<AlbumMedia> = emptyList()

        override suspend fun getDirectories(
            mediaFilter: AlbumMediaFilter,
        ): List<AlbumDirectory> {
            directoryCalls++
            lastDirectoryFilter = mediaFilter
            return emptyList()
        }
    }

    private class FakePickedMediaStore : PickedMediaStore {
        val rows = linkedMapOf<String, PickedMediaEntity>()
        var lastPagingFilter: AlbumMediaFilter? = null

        fun seed(entity: PickedMediaEntity) {
            rows[entity.uri] = entity
        }

        override fun pagingSource(filter: AlbumMediaFilter): PagingSource<Int, PickedMediaEntity> {
            lastPagingFilter = filter
            return object : PagingSource<Int, PickedMediaEntity>() {
                override suspend fun load(
                    params: LoadParams<Int>,
                ): LoadResult<Int, PickedMediaEntity> = LoadResult.Page(
                    data = rows.values.toList(),
                    prevKey = null,
                    nextKey = null,
                )

                override fun getRefreshKey(
                    state: PagingState<Int, PickedMediaEntity>,
                ): Int? = null
            }
        }

        override suspend fun upsertBatch(
            drafts: List<PickedMediaDraft>,
        ): List<PickedMediaEntity> = error("Not used")

        override suspend fun find(uri: String): PickedMediaEntity? = rows[uri]

        override suspend fun remove(uri: String): PickedMediaEntity? = rows.remove(uri)

        override suspend fun clear(): List<PickedMediaEntity> = rows.values.toList().also {
            rows.clear()
        }

        override suspend fun all(): List<PickedMediaEntity> = rows.values.toList()
    }

    private class FakePickerRegistrar : PickerRegistrar {
        override fun register(
            activity: ComponentActivity,
            mediaFilter: AlbumMediaFilter,
            maxSelectionCount: Int?,
            onResult: (PhotoPickResult) -> Unit,
        ): AlbumPhotoPickerLauncher = object : AlbumPhotoPickerLauncher {
            override val mediaFilter: AlbumMediaFilter = mediaFilter

            override fun launch() = Unit
        }
    }

    private class FakeGrantManager : PersistableGrantManager {
        val persisted = linkedSetOf<Uri>()
        val released = mutableListOf<Uri>()

        override fun persistedReadUris(): Set<Uri> = persisted.toSet()

        override fun takeRead(uri: Uri) = Unit

        override fun releaseRead(uri: Uri) {
            released += uri
            persisted -= uri
        }
    }

    private class FakeUriAccessChecker : UriAccessChecker {
        val readable = mutableSetOf<Uri>()

        override fun canRead(uri: Uri): Boolean = uri in readable
    }

    private fun entity(name: String, ownsGrant: Boolean) = PickedMediaEntity(
        uri = uri(name).toString(),
        mediaType = AlbumMediaType.IMAGE.name,
        displayName = null,
        mimeType = "image/jpeg",
        sizeBytes = null,
        width = null,
        height = null,
        durationMillis = null,
        selectedAtEpochMillis = 1,
        sortOrder = 1,
        ownsPersistableGrant = ownsGrant,
    )

    private fun uri(name: String): Uri = Uri.parse("content://picker/$name")
}
