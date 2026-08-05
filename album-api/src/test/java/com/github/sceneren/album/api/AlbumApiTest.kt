package com.github.sceneren.album.api

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.internal.database.PickedMediaDraft
import com.github.sceneren.album.api.internal.database.PickedMediaEntity
import com.github.sceneren.album.api.internal.database.PickedMediaStore
import com.github.sceneren.album.api.internal.mediastore.MediaStoreDataSource
import com.github.sceneren.album.api.internal.permission.MediaAccessResolver
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import com.github.sceneren.album.api.internal.picker.PickerRegistrar
import com.github.sceneren.album.api.internal.picker.UriAccessChecker
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.UUID
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
    fun `未授权拍摄结果持久化并可在新会话展示`() = runTest {
        permissions.result = MediaAccessStatus.DENIED
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, maxSelectionCount = 3)
        val sessionId = "camera-${UUID.randomUUID()}"
        val firstClient = api.createPickerClient(context)
        val opened = firstClient.openSession(config, sessionId)
        assertEquals(sessionId, opened.sessionId)

        val capture = firstClient.prepareCamera(sessionId, AlbumMediaType.IMAGE).getOrThrow()
        val cameraFile = File(capture.filePath).apply { writeText("captured") }
        val recoveredClient = api.createPickerClient(context)

        try {
            assertTrue(recoveredClient.openSession(config, sessionId).hasPendingCamera)

            val completed = recoveredClient.completeCamera(sessionId, success = true).getOrThrow()
            assertEquals(listOf(capture.uri), completed.cameraItems.map(AlbumMedia::uri))
            assertEquals(listOf(capture.uri.toString()), pickedStore.rows.keys.toList())

            recoveredClient.cancel(sessionId)
            assertTrue(cameraFile.isFile)

            val nextSession = recoveredClient.openSession(
                config = config,
                sessionId = "next-${UUID.randomUUID()}",
            )
            assertTrue(nextSession.cameraItems.isEmpty())
            val persisted = api.getMediaFeed(AlbumMediaFilter.IMAGES)
                .pagingData
                .asSnapshot()
            assertEquals(listOf(capture.uri), persisted.map(AlbumMedia::uri))
        } finally {
            cameraFile.delete()
            recoveredClient.cancel(sessionId)
        }
    }

    @Test
    fun partialDirectoriesAreEmptyWithoutMediaStoreQuery() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL

        assertEquals(emptyList<AlbumDirectory>(), api.getMediaDirectories().getOrThrow())
        assertEquals(0, mediaStore.directoryCalls)
    }

    @Test
    fun partialSyncPersistsVisibleMediaWithoutOwningGrant() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL
        mediaStore.allMedia = listOf(
            media("partial-image", AlbumMediaType.IMAGE),
            media("partial-video", AlbumMediaType.VIDEO),
        )

        assertEquals(
            2,
            api.syncPartialSelections(AlbumMediaFilter.IMAGES_AND_VIDEOS).getOrThrow(),
        )

        assertEquals(1, mediaStore.loadAllCalls)
        assertEquals(AlbumMediaFilter.IMAGES_AND_VIDEOS, mediaStore.lastLoadAllFilter)
        assertEquals(
            listOf("content://media/partial-image", "content://media/partial-video"),
            pickedStore.upsertedDrafts.map(PickedMediaDraft::uri),
        )
        assertTrue(pickedStore.upsertedDrafts.none(PickedMediaDraft::ownsPersistableGrant))
    }

    @Test
    fun partialSyncDoesNotQueryWhenAccessIsFullOrDenied() = runTest {
        mediaStore.allMedia = listOf(media("ignored", AlbumMediaType.IMAGE))

        permissions.result = MediaAccessStatus.FULL
        assertEquals(0, api.syncPartialSelections().getOrThrow())
        permissions.result = MediaAccessStatus.DENIED
        assertEquals(0, api.syncPartialSelections().getOrThrow())

        assertEquals(0, mediaStore.loadAllCalls)
        assertTrue(pickedStore.upsertedDrafts.isEmpty())
    }

    @Test
    fun partialSyncKeepsExistingPhotoPickerGrantOwnership() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL
        val persistedUri = uri("owned-partial")
        pickedStore.seed(entity("owned-partial", ownsGrant = true))
        mediaStore.allMedia = listOf(media("owned-partial", AlbumMediaType.IMAGE))

        api.syncPartialSelections().getOrThrow()

        assertTrue(pickedStore.rows.getValue(persistedUri.toString()).ownsPersistableGrant)
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
    fun fullBoundedPageRoutesToMediaStoreWithBucketAndOffset() = runTest {
        permissions.result = MediaAccessStatus.FULL
        mediaStore.pageMedia = listOf(media("page-image", AlbumMediaType.IMAGE))

        val result = api.loadMediaPage(
            mediaFilter = AlbumMediaFilter.IMAGES,
            bucketId = 42L,
            offset = 25,
            limit = 10,
        ).getOrThrow()

        assertEquals(mediaStore.pageMedia, result)
        assertEquals(AlbumMediaFilter.IMAGES, mediaStore.lastPageFilter)
        assertEquals(42L, mediaStore.lastPageBucketId)
        assertEquals(25, mediaStore.lastPageOffset)
        assertEquals(10, mediaStore.lastPageLimit)
        assertEquals(0, pickedStore.pageCalls)
    }

    @Test
    fun partialBoundedPageRoutesToPersistedPickerList() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL
        pickedStore.seed(entity("first", ownsGrant = true))
        pickedStore.seed(entity("second", ownsGrant = false))

        val result = api.loadMediaPage(
            mediaFilter = AlbumMediaFilter.IMAGES,
            bucketId = 99L,
            offset = 1,
            limit = 1,
        ).getOrThrow()

        assertEquals(listOf(uri("second")), result.map(AlbumMedia::uri))
        assertEquals(AlbumMediaFilter.IMAGES, pickedStore.lastPageFilter)
        assertEquals(1, pickedStore.lastPageOffset)
        assertEquals(1, pickedStore.lastPageLimit)
        assertEquals(null, mediaStore.lastPageBucketId)
    }

    @Test
    fun boundedPageRejectsInvalidRangeAsFailure() = runTest {
        assertTrue(api.loadMediaPage(offset = -1).isFailure)
        assertTrue(api.loadMediaPage(limit = 0).isFailure)
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
    fun clearAttemptsEveryOwnedGrantWhenOneReleaseFails() = runTest {
        pickedStore.seed(entity("first", ownsGrant = true))
        pickedStore.seed(entity("second", ownsGrant = true))
        grants.releaseFailureFor = uri("first")

        val result = api.clearPersistedSelections()

        assertTrue(result.isFailure)
        assertEquals(listOf(uri("first"), uri("second")), grants.released)
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

    @Test
    fun reconcileKeepsReadableNonOwnedPartialRecordWithoutPersistedGrant() = runTest {
        pickedStore.seed(entity("partial", ownsGrant = false))
        accessChecker.readable += uri("partial")

        assertEquals(0, api.reconcilePersistedSelections().getOrThrow())
        assertTrue(pickedStore.rows.containsKey(uri("partial").toString()))
    }

    private class FakeMediaAccessResolver : MediaAccessResolver {
        var result: MediaAccessStatus = MediaAccessStatus.DENIED

        override fun resolve(filter: AlbumMediaFilter): MediaAccessStatus = result
    }

    private class FakeMediaStoreDataSource : MediaStoreDataSource {
        var directoryCalls = 0
        var lastDirectoryFilter: AlbumMediaFilter? = null
        var loadAllCalls = 0
        var lastLoadAllFilter: AlbumMediaFilter? = null
        var allMedia: List<AlbumMedia> = emptyList()
        var pageMedia: List<AlbumMedia> = emptyList()
        var lastPageFilter: AlbumMediaFilter? = null
        var lastPageBucketId: Long? = null
        var lastPageOffset: Int? = null
        var lastPageLimit: Int? = null

        override suspend fun loadAll(
            mediaFilter: AlbumMediaFilter,
        ): List<AlbumMedia> {
            loadAllCalls++
            lastLoadAllFilter = mediaFilter
            return allMedia
        }

        override suspend fun loadPage(
            mediaFilter: AlbumMediaFilter,
            bucketId: Long,
            offset: Int,
            limit: Int,
        ): List<AlbumMedia> {
            lastPageFilter = mediaFilter
            lastPageBucketId = bucketId
            lastPageOffset = offset
            lastPageLimit = limit
            return pageMedia
        }

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
        val upsertedDrafts = mutableListOf<PickedMediaDraft>()
        var lastPagingFilter: AlbumMediaFilter? = null
        var pageCalls = 0
        var lastPageFilter: AlbumMediaFilter? = null
        var lastPageOffset: Int? = null
        var lastPageLimit: Int? = null

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

        override suspend fun loadPage(
            filter: AlbumMediaFilter,
            offset: Int,
            limit: Int,
        ): List<PickedMediaEntity> {
            pageCalls++
            lastPageFilter = filter
            lastPageOffset = offset
            lastPageLimit = limit
            return rows.values
                .filter { entity ->
                    when (filter) {
                        AlbumMediaFilter.IMAGES -> entity.mediaType == AlbumMediaType.IMAGE.name
                        AlbumMediaFilter.VIDEOS -> entity.mediaType == AlbumMediaType.VIDEO.name
                        AlbumMediaFilter.IMAGES_AND_VIDEOS -> true
                    }
                }
                .drop(offset)
                .take(limit)
        }

        override suspend fun upsertBatch(
            drafts: List<PickedMediaDraft>,
        ): List<PickedMediaEntity> {
            upsertedDrafts += drafts
            return drafts.mapIndexed { index, draft ->
                val existing = rows[draft.uri]
                val entity = draft.toEntity(
                    sortOrder = drafts.size - index.toLong(),
                    ownsPersistableGrant = existing?.ownsPersistableGrant == true ||
                        draft.ownsPersistableGrant,
                )
                rows[entity.uri] = entity
                entity
            }
        }

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
        var releaseFailureFor: Uri? = null

        override fun persistedReadUris(): Set<Uri> = persisted.toSet()

        override fun takeRead(uri: Uri) = Unit

        override fun releaseRead(uri: Uri) {
            released += uri
            if (uri == releaseFailureFor) error("release failed")
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

    private fun media(name: String, mediaType: AlbumMediaType) = AlbumMedia(
        uri = Uri.parse("content://media/$name"),
        mediaType = mediaType,
        displayName = "$name.jpg",
        mimeType = if (mediaType == AlbumMediaType.IMAGE) "image/jpeg" else "video/mp4",
        sizeBytes = 4_096L,
        dateAddedEpochSeconds = 100L,
        dateModifiedEpochSeconds = 99L,
        width = 1_920,
        height = 1_080,
        durationMillis = if (mediaType == AlbumMediaType.VIDEO) 2_000L else null,
        bucketId = 1L,
        bucketName = "Camera",
        selectedAtEpochMillis = null,
        source = AlbumMediaSource.MEDIA_STORE,
    )
}
