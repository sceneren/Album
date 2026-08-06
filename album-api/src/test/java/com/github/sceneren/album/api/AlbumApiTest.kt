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
import com.github.sceneren.album.api.internal.picker.PhotoPickerResultProcessor
import com.github.sceneren.album.api.internal.picker.PickedUriMetadata
import com.github.sceneren.album.api.internal.picker.PickerRegistrar
import com.github.sceneren.album.api.internal.picker.UriAccessChecker
import com.github.sceneren.album.api.internal.picker.UriMetadataReader
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
/** 验证 `AlbumApiTest` 覆盖的行为。 */
class AlbumApiTest {
    private lateinit var permissions: FakeMediaAccessResolver
    private lateinit var mediaStore: FakeMediaStoreDataSource
    private lateinit var pickedStore: FakePickedMediaStore
    private lateinit var grants: FakeGrantManager
    private lateinit var accessChecker: FakeUriAccessChecker
    private lateinit var api: AlbumApi

    @Before
    /** 更新 `setUp` 对应的状态。 */
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
            photoPickerResultProcessor = PhotoPickerResultProcessor(
                grantManager = grants,
                metadataReader = FakeUriMetadataReader(),
                store = pickedStore,
            ),
            grantManager = grants,
            uriAccessChecker = accessChecker,
        )
    }

    @Test
    /** 验证 `fullRoutesToMediaStoreButPartialRoutesToRoom` 所描述的场景。 */
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
    /** 验证 `deniedAlsoRoutesToPersistedPickerFeed` 所描述的场景。 */
    fun deniedAlsoRoutesToPersistedPickerFeed() {
        permissions.result = MediaAccessStatus.DENIED

        val feed = api.getMediaFeed(AlbumMediaFilter.IMAGES)

        assertEquals(AlbumMediaSource.PHOTO_PICKER, feed.source)
        assertEquals(MediaAccessStatus.DENIED, feed.accessStatus)
    }

    @Test
    /** 验证 `hostManagedPhotoPickerResultUsesTheSharedPersistencePipeline` 所描述的场景。 */
    fun hostManagedPhotoPickerResultUsesTheSharedPersistencePipeline() = runTest {
        val pickedUri = uri("compose")

        val result = api.processPhotoPickerResult(
            uris = listOf(pickedUri),
            mediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount = 3,
        )

        assertEquals(
            listOf(pickedUri),
            (result as PhotoPickResult.Selected).media.map(AlbumMedia::uri),
        )
        assertTrue(pickedUri.toString() in pickedStore.rows)
    }

    @Test
    /** 验证 `persistedPickerFeedRemovesDeletedMediaBeforePaging` 所描述的场景。 */
    fun persistedPickerFeedRemovesDeletedMediaBeforePaging() = runTest {
        permissions.result = MediaAccessStatus.DENIED
        pickedStore.seed(entity("keep", ownsGrant = false))
        pickedStore.seed(entity("deleted", ownsGrant = false))
        accessChecker.readable += uri("keep")

        val items = api.getMediaFeed(AlbumMediaFilter.IMAGES)
            .pagingData
            .asSnapshot()

        assertEquals(listOf(uri("keep")), items.map(AlbumMedia::uri))
        assertEquals(listOf("content://picker/keep"), pickedStore.rows.keys.toList())
    }

    @Test
    /** 验证 `未授权拍摄结果持久化并可在新会话展示` 所描述的场景。 */
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
            accessChecker.readable += capture.uri

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
    /** 验证 `partialDirectoriesAreEmptyWithoutMediaStoreQuery` 所描述的场景。 */
    fun partialDirectoriesAreEmptyWithoutMediaStoreQuery() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL

        assertEquals(emptyList<AlbumDirectory>(), api.getMediaDirectories().getOrThrow())
        assertEquals(0, mediaStore.directoryCalls)
    }

    @Test
    /** 验证 `partialSyncPersistsVisibleMediaWithoutOwningGrant` 所描述的场景。 */
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
    /** 验证 `partialSyncDoesNotQueryWhenAccessIsFullOrDenied` 所描述的场景。 */
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
    /** 验证 `partialSyncKeepsExistingPhotoPickerGrantOwnership` 所描述的场景。 */
    fun partialSyncKeepsExistingPhotoPickerGrantOwnership() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL
        val persistedUri = uri("owned-partial")
        pickedStore.seed(entity("owned-partial", ownsGrant = true))
        mediaStore.allMedia = listOf(media("owned-partial", AlbumMediaType.IMAGE))

        api.syncPartialSelections().getOrThrow()

        assertTrue(pickedStore.rows.getValue(persistedUri.toString()).ownsPersistableGrant)
    }

    @Test
    /** 验证 `fullDirectoriesUseMediaStore` 所描述的场景。 */
    fun fullDirectoriesUseMediaStore() = runTest {
        permissions.result = MediaAccessStatus.FULL

        api.getMediaDirectories(AlbumMediaFilter.VIDEOS).getOrThrow()

        assertEquals(1, mediaStore.directoryCalls)
        assertEquals(AlbumMediaFilter.VIDEOS, mediaStore.lastDirectoryFilter)
    }

    @Test
    /** 验证 `invalidPageSizeIsRejected` 所描述的场景。 */
    fun invalidPageSizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            api.getMediaFeed(pageSize = 0)
        }
    }

    @Test
    /** 验证 `fullBoundedPageRoutesToMediaStoreWithBucketAndOffset` 所描述的场景。 */
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
    /** 验证 `partialBoundedPageRoutesToPersistedPickerList` 所描述的场景。 */
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
    /** 验证 `boundedPageRejectsInvalidRangeAsFailure` 所描述的场景。 */
    fun boundedPageRejectsInvalidRangeAsFailure() = runTest {
        assertTrue(api.loadMediaPage(offset = -1).isFailure)
        assertTrue(api.loadMediaPage(limit = 0).isFailure)
    }

    @Test
    /** 验证 `removeReleasesOnlyLibraryOwnedGrant` 所描述的场景。 */
    fun removeReleasesOnlyLibraryOwnedGrant() = runTest {
        pickedStore.seed(entity("owned", ownsGrant = true))
        pickedStore.seed(entity("host", ownsGrant = false))

        assertTrue(api.removePersistedSelection(uri("owned")).getOrThrow())
        assertTrue(api.removePersistedSelection(uri("host")).getOrThrow())

        assertEquals(listOf(uri("owned")), grants.released)
        assertFalse(pickedStore.rows.containsKey(uri("owned").toString()))
    }

    @Test
    /** 验证 `clearReturnsCountAndReleasesOwnedGrants` 所描述的场景。 */
    fun clearReturnsCountAndReleasesOwnedGrants() = runTest {
        pickedStore.seed(entity("owned", ownsGrant = true))
        pickedStore.seed(entity("host", ownsGrant = false))

        assertEquals(2, api.clearPersistedSelections().getOrThrow())

        assertTrue(pickedStore.rows.isEmpty())
        assertEquals(listOf(uri("owned")), grants.released)
    }

    @Test
    /** 验证 `clearAttemptsEveryOwnedGrantWhenOneReleaseFails` 所描述的场景。 */
    fun clearAttemptsEveryOwnedGrantWhenOneReleaseFails() = runTest {
        pickedStore.seed(entity("first", ownsGrant = true))
        pickedStore.seed(entity("second", ownsGrant = true))
        grants.releaseFailureFor = uri("first")

        val result = api.clearPersistedSelections()

        assertTrue(result.isFailure)
        assertEquals(listOf(uri("first"), uri("second")), grants.released)
    }

    @Test
    /** 验证 `reconcileRemovesMissingAndUnreadableUris` 所描述的场景。 */
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
    /** 验证 `reconcileKeepsReadableNonOwnedPartialRecordWithoutPersistedGrant` 所描述的场景。 */
    fun reconcileKeepsReadableNonOwnedPartialRecordWithoutPersistedGrant() = runTest {
        pickedStore.seed(entity("partial", ownsGrant = false))
        accessChecker.readable += uri("partial")

        assertEquals(0, api.reconcilePersistedSelections().getOrThrow())
        assertTrue(pickedStore.rows.containsKey(uri("partial").toString()))
    }

    /** 负责 `FakeMediaAccessResolver` 相关的数据与行为。 */
    private class FakeMediaAccessResolver : MediaAccessResolver {
        var result: MediaAccessStatus = MediaAccessStatus.DENIED

        /** 执行 `resolve` 方法定义的处理。 */
        override fun resolve(filter: AlbumMediaFilter): MediaAccessStatus = result
    }

    /** 负责 `FakeMediaStoreDataSource` 相关的数据与行为。 */
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

        /** 获取 `loadAll` 所需的数据。 */
        override suspend fun loadAll(
            mediaFilter: AlbumMediaFilter,
        ): List<AlbumMedia> {
            loadAllCalls++
            lastLoadAllFilter = mediaFilter
            return allMedia
        }

        /** 获取 `loadPage` 所需的数据。 */
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

        /** 获取 `getDirectories` 所需的数据。 */
        override suspend fun getDirectories(
            mediaFilter: AlbumMediaFilter,
        ): List<AlbumDirectory> {
            directoryCalls++
            lastDirectoryFilter = mediaFilter
            return emptyList()
        }
    }

    /** 负责 `FakePickedMediaStore` 管理数据的持久化读写。 */
    private class FakePickedMediaStore : PickedMediaStore {
        val rows = linkedMapOf<String, PickedMediaEntity>()
        val upsertedDrafts = mutableListOf<PickedMediaDraft>()
        var lastPagingFilter: AlbumMediaFilter? = null
        var pageCalls = 0
        var lastPageFilter: AlbumMediaFilter? = null
        var lastPageOffset: Int? = null
        var lastPageLimit: Int? = null

        /** 执行 `seed` 方法定义的处理。 */
        fun seed(entity: PickedMediaEntity) {
            rows[entity.uri] = entity
        }

        /** 执行 `pagingSource` 方法定义的处理。 */
        override fun pagingSource(filter: AlbumMediaFilter): PagingSource<Int, PickedMediaEntity> {
            lastPagingFilter = filter
            return object : PagingSource<Int, PickedMediaEntity>() {
                /** 获取 `load` 所需的数据。 */
                override suspend fun load(
                    params: LoadParams<Int>,
                ): LoadResult<Int, PickedMediaEntity> = LoadResult.Page(
                    data = rows.values.toList(),
                    prevKey = null,
                    nextKey = null,
                )

                /** 获取 `getRefreshKey` 所需的数据。 */
                override fun getRefreshKey(
                    state: PagingState<Int, PickedMediaEntity>,
                ): Int? = null
            }
        }

        /** 获取 `loadPage` 所需的数据。 */
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

        /** 执行 `upsertBatch` 方法定义的处理。 */
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

        /** 获取 `find` 所需的数据。 */
        override suspend fun find(uri: String): PickedMediaEntity? = rows[uri]

        /** 清理 `remove` 对应的数据或资源。 */
        override suspend fun remove(uri: String): PickedMediaEntity? = rows.remove(uri)

        /** 清理 `clear` 对应的数据或资源。 */
        override suspend fun clear(): List<PickedMediaEntity> = rows.values.toList().also {
            rows.clear()
        }

        /** 执行 `all` 方法定义的处理。 */
        override suspend fun all(): List<PickedMediaEntity> = rows.values.toList()
    }

    /** 负责 `FakePickerRegistrar` 相关的数据与行为。 */
    private class FakePickerRegistrar : PickerRegistrar {
        /** 创建或准备 `register` 对应的对象。 */
        override fun register(
            activity: ComponentActivity,
            mediaFilter: AlbumMediaFilter,
            maxSelectionCount: Int?,
            onResult: (PhotoPickResult) -> Unit,
        ): AlbumPhotoPickerLauncher = object : AlbumPhotoPickerLauncher {
            override val mediaFilter: AlbumMediaFilter = mediaFilter

            /** 执行 `launch` 方法定义的处理。 */
            override fun launch() = Unit
        }
    }

    /** 负责 `FakeGrantManager` 相关的数据与行为。 */
    private class FakeGrantManager : PersistableGrantManager {
        val persisted = linkedSetOf<Uri>()
        val released = mutableListOf<Uri>()
        var releaseFailureFor: Uri? = null

        /** 执行 `persistedReadUris` 方法定义的处理。 */
        override fun persistedReadUris(): Set<Uri> = persisted.toSet()

        /** 执行 `takeRead` 方法定义的处理。 */
        override fun takeRead(uri: Uri) = Unit

        /** 清理 `releaseRead` 对应的数据或资源。 */
        override fun releaseRead(uri: Uri) {
            released += uri
            if (uri == releaseFailureFor) error("release failed")
            persisted -= uri
        }
    }

    /** 负责 `FakeUriAccessChecker` 相关的数据与行为。 */
    private class FakeUriAccessChecker : UriAccessChecker {
        val readable = mutableSetOf<Uri>()

        /** 判断 `canRead` 条件是否成立。 */
        override fun canRead(uri: Uri): Boolean = uri in readable
    }

    /** 负责 `FakeUriMetadataReader` 相关的数据与行为。 */
    private class FakeUriMetadataReader : UriMetadataReader {
        /** 执行 `requiredType` 方法定义的处理。 */
        override fun requiredType(uri: Uri): AlbumMediaType = AlbumMediaType.IMAGE

        /** 获取 `read` 所需的数据。 */
        override fun read(uri: Uri, type: AlbumMediaType) = PickedUriMetadata(
            uri = uri,
            mediaType = type,
            displayName = "picked.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1_024L,
            width = 100,
            height = 100,
            durationMillis = null,
        )
    }

    /** 执行 `entity` 方法定义的处理。 */
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

    /** 执行 `uri` 方法定义的处理。 */
    private fun uri(name: String): Uri = Uri.parse("content://picker/$name")

    /** 执行 `media` 方法定义的处理。 */
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
