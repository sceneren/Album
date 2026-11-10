package com.github.sceneren.album.api

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.internal.mediastore.MediaStoreDataSource
import com.github.sceneren.album.api.internal.permission.MediaAccessResolver
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import com.github.sceneren.album.api.internal.picker.PersistedUriGrant
import com.github.sceneren.album.api.internal.picker.PhotoPickerResultProcessor
import com.github.sceneren.album.api.internal.picker.PickedUriMetadata
import com.github.sceneren.album.api.internal.picker.PickerRegistrar
import com.github.sceneren.album.api.internal.picker.UriAccessChecker
import com.github.sceneren.album.api.internal.picker.UriMetadataReader
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
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AlbumApiTest` 覆盖的行为。 */
class AlbumApiTest {
    private lateinit var context: Context
    private lateinit var permissions: FakeMediaAccessResolver
    private lateinit var mediaStore: FakeMediaStoreDataSource
    private lateinit var grants: FakeGrantManager
    private lateinit var metadataReader: FakeUriMetadataReader
    private lateinit var accessChecker: FakeUriAccessChecker
    private lateinit var api: AlbumApi

    @Before
    /** 更新 `setUp` 对应的状态。 */
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        permissions = FakeMediaAccessResolver()
        mediaStore = FakeMediaStoreDataSource()
        grants = FakeGrantManager()
        metadataReader = FakeUriMetadataReader()
        accessChecker = FakeUriAccessChecker()
        api = AlbumApi(
            accessResolver = permissions,
            mediaStore = mediaStore,
            pickerRegistrar = FakePickerRegistrar(),
            photoPickerResultProcessor = PhotoPickerResultProcessor(
                grantManager = grants,
                metadataReader = metadataReader,
            ),
            grantManager = grants,
            metadataReader = metadataReader,
            uriAccessChecker = accessChecker,
            cameraMediaLoader = { filter ->
                AlbumPickerClient.loadCompletedCameraMedia(context, filter)
            },
            cameraMediaDeleter = { uri ->
                AlbumPickerClient.deleteCameraMediaByUri(context, uri)
            },
        )
    }

    @Test
    /** 验证 `fullRoutesToMediaStoreAndPartialCombinesSystemAuthorizedResources` 所描述的场景。 */
    fun fullRoutesToMediaStoreAndPartialCombinesSystemAuthorizedResources() = runTest {
        permissions.result = MediaAccessStatus.FULL
        val full = api.getMediaFeed(AlbumMediaFilter.VIDEOS, pageSize = 25)
        assertEquals(AlbumMediaSource.MEDIA_STORE, full.source)

        permissions.result = MediaAccessStatus.PARTIAL
        grants.seed(uri("picker-video"), persistedAt = 200L)
        metadataReader.types[uri("picker-video")] = AlbumMediaType.VIDEO
        mediaStore.pageMedia = listOf(media("partial-video", AlbumMediaType.VIDEO))

        val partial = api.getMediaFeed(AlbumMediaFilter.VIDEOS, pageSize = 25)
        assertEquals(AlbumMediaSource.PHOTO_PICKER, partial.source)
        val snapshot = partial.pagingData.asSnapshot()
        assertEquals(
            listOf(uri("picker-video"), Uri.parse("content://media/partial-video")),
            snapshot.map(AlbumMedia::uri),
        )
        assertEquals(AlbumMediaFilter.VIDEOS, mediaStore.lastPageFilter)
    }

    @Test
    /** 验证 `deniedRoutesToSystemPersistedUriFeed` 所描述的场景。 */
    fun deniedRoutesToSystemPersistedUriFeed() {
        permissions.result = MediaAccessStatus.DENIED

        val feed = api.getMediaFeed(AlbumMediaFilter.IMAGES)

        assertEquals(AlbumMediaSource.PHOTO_PICKER, feed.source)
        assertEquals(MediaAccessStatus.DENIED, feed.accessStatus)
    }

    @Test
    /** 验证 `hostManagedPhotoPickerResultPersistsSystemGrant` 所描述的场景。 */
    fun hostManagedPhotoPickerResultPersistsSystemGrant() = runTest {
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
        assertTrue(pickedUri in grants.persistedReadUris())
    }

    @Test
    /**
     * 验证无相册权限时仅读取系统持有的有效持久授权，且分页不打开历史图片内容流。
     */
    fun persistedPickerFeedOnlyReadsActiveSystemGrantsWithoutOpeningContentStreams() = runTest {
        permissions.result = MediaAccessStatus.DENIED
        grants.seed(uri("keep"), persistedAt = 100L)
        accessChecker.failOnRead = true
        metadataReader.failOnStreamProbe = true

        val items = api.getMediaFeed(AlbumMediaFilter.IMAGES)
            .pagingData
            .asSnapshot()

        assertEquals(listOf(uri("keep")), items.map(AlbumMedia::uri))
    }

    @Test
    /** 验证 `未授权拍摄结果保留在相机目录并可在新会话展示` 所描述的场景。 */
    fun `未授权拍摄结果保留在相机目录并可在新会话展示`() = runTest {
        permissions.result = MediaAccessStatus.DENIED
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
            assertEquals(AlbumMediaSource.CAMERA, completed.cameraItems.single().source)

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
            assertEquals(AlbumMediaSource.CAMERA, persisted.single().source)
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
    /** 验证 `partialSyncDoesNotOpenHistoricalMediaOrQueryEntireMediaStore` 所描述的场景。 */
    fun partialSyncDoesNotOpenHistoricalMediaOrQueryEntireMediaStore() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL
        grants.seed(uri("keep"))
        accessChecker.failOnRead = true

        assertEquals(
            0,
            api.syncPartialSelections(AlbumMediaFilter.IMAGES_AND_VIDEOS).getOrThrow(),
        )
        assertTrue(grants.released.isEmpty())
    }

    @Test
    /** 验证大量已选图片时仅按当前页惰性读取元数据，且重新进入时直接命中内存缓存。 */
    fun manyPersistedUrisOnlyResolveCurrentPageAndReuseMemoryCacheOnReentry() = runTest {
        permissions.result = MediaAccessStatus.DENIED
        (1..200).forEach { index ->
            grants.seed(uri("img-$index"), persistedAt = (1_000 - index).toLong())
        }

        val firstPage = api.loadMediaPage(
            mediaFilter = AlbumMediaFilter.IMAGES,
            offset = 0,
            limit = 20,
        ).getOrThrow()

        assertEquals(20, firstPage.size)
        assertEquals(20, metadataReader.readCalls)

        val reenteredFirstPage = api.loadMediaPage(
            mediaFilter = AlbumMediaFilter.IMAGES,
            offset = 0,
            limit = 20,
        ).getOrThrow()

        assertEquals(firstPage, reenteredFirstPage)
        assertEquals(20, metadataReader.readCalls)
    }

    @Test
    fun invalidatingMetadataRemovesDeletedPhotosEvenWhenTheirGrantAndMimeRemain() = runTest {
        permissions.result = MediaAccessStatus.DENIED
        grants.seed(uri("deleted"), persistedAt = 20L)
        grants.seed(uri("keep"), persistedAt = 10L)
        assertEquals(2, api.loadMediaPage().getOrThrow().size)

        metadataReader.missingUris += uri("deleted")
        api.invalidateMediaCache()

        assertEquals(listOf(uri("keep")), api.loadMediaPage().getOrThrow().map { it.uri })
        assertTrue(uri("deleted") in grants.persistedReadUris())
        assertEquals(1, metadataReader.cacheInvalidations)
    }

    @Test
    fun scrollingReadsPersistedGrantsOnlyOncePerPagingGeneration() = runTest {
        permissions.result = MediaAccessStatus.DENIED
        (1..100).forEach { grants.seed(uri("image-$it"), persistedAt = it.toLong()) }

        val items = api.getMediaFeed(pageSize = 20).pagingData.asSnapshot { scrollTo(79) }

        assertTrue(items.size >= 80)
        assertEquals(1, grants.snapshotReads)
        assertEquals(items.size, metadataReader.readCalls)
    }

    @Test
    /** 验证 `partialSyncDoesNotReconcileWhenAccessIsFullOrDenied` 所描述的场景。 */
    fun partialSyncDoesNotReconcileWhenAccessIsFullOrDenied() = runTest {
        grants.seed(uri("stale"))

        permissions.result = MediaAccessStatus.FULL
        assertEquals(0, api.syncPartialSelections().getOrThrow())
        permissions.result = MediaAccessStatus.DENIED
        assertEquals(0, api.syncPartialSelections().getOrThrow())

        assertTrue(grants.released.isEmpty())
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
    }

    @Test
    /** 验证 `partialBoundedPageCombinesPersistedGrantsAndMediaStore` 所描述的场景。 */
    fun partialBoundedPageCombinesPersistedGrantsAndMediaStore() = runTest {
        permissions.result = MediaAccessStatus.PARTIAL
        grants.seed(uri("first"), persistedAt = 20L)
        grants.seed(uri("second"), persistedAt = 10L)

        val result = api.loadMediaPage(
            mediaFilter = AlbumMediaFilter.IMAGES,
            bucketId = 99L,
            offset = 1,
            limit = 1,
        ).getOrThrow()

        assertEquals(listOf(uri("second")), result.map(AlbumMedia::uri))
        assertEquals(null, mediaStore.lastPageBucketId)
    }

    @Test
    /** 验证 `boundedPageRejectsInvalidRangeAsFailure` 所描述的场景。 */
    fun boundedPageRejectsInvalidRangeAsFailure() = runTest {
        assertTrue(api.loadMediaPage(offset = -1).isFailure)
        assertTrue(api.loadMediaPage(limit = 0).isFailure)
    }

    @Test
    /** 验证 `removeReleasesSystemPersistedGrant` 所描述的场景。 */
    fun removeReleasesSystemPersistedGrant() = runTest {
        grants.seed(uri("owned"))

        assertTrue(api.removePersistedSelection(uri("owned")).getOrThrow())
        assertFalse(api.removePersistedSelection(uri("missing")).getOrThrow())

        assertEquals(listOf(uri("owned")), grants.released)
        assertFalse(uri("owned") in grants.persistedReadUris())
    }

    @Test
    /** 验证 `clearReturnsCountAndReleasesPersistedGrants` 所描述的场景。 */
    fun clearReturnsCountAndReleasesPersistedGrants() = runTest {
        grants.seed(uri("first"))
        grants.seed(uri("second"))

        assertEquals(2, api.clearPersistedSelections().getOrThrow())

        assertTrue(grants.persistedReadUris().isEmpty())
        assertEquals(listOf(uri("first"), uri("second")), grants.released)
    }

    @Test
    /** 验证 `clearAttemptsEveryGrantWhenOneReleaseFails` 所描述的场景。 */
    fun clearAttemptsEveryGrantWhenOneReleaseFails() = runTest {
        grants.seed(uri("first"))
        grants.seed(uri("second"))
        grants.releaseFailureFor = uri("first")

        val result = api.clearPersistedSelections()

        assertTrue(result.isFailure)
        assertEquals(listOf(uri("first"), uri("second")), grants.released)
    }

    @Test
    /** 验证 `reconcileReleasesUnreadablePersistedUris` 所描述的场景。 */
    fun reconcileReleasesUnreadablePersistedUris() = runTest {
        grants.seed(uri("keep"))
        grants.seed(uri("unreadable"))
        accessChecker.readable += uri("keep")

        assertEquals(1, api.reconcilePersistedSelections().getOrThrow())

        assertEquals(setOf(uri("keep")), grants.persistedReadUris())
        assertEquals(listOf(uri("unreadable")), grants.released)
    }

    /** 负责 `FakeMediaAccessResolver` 相关的数据与行为。 */
    private class FakeMediaAccessResolver : MediaAccessResolver {
        var result: MediaAccessStatus = MediaAccessStatus.DENIED

        override fun resolve(filter: AlbumMediaFilter): MediaAccessStatus = result
    }

    /** 负责 `FakeMediaStoreDataSource` 相关的数据与行为。 */
    private class FakeMediaStoreDataSource : MediaStoreDataSource {
        var directoryCalls = 0
        var lastDirectoryFilter: AlbumMediaFilter? = null
        var pageMedia: List<AlbumMedia> = emptyList()
        var lastPageFilter: AlbumMediaFilter? = null
        var lastPageBucketId: Long? = null
        var lastPageOffset: Int? = null
        var lastPageLimit: Int? = null

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

    /** 负责 `FakePickerRegistrar` 相关的数据与行为。 */
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

    /** 负责 `FakeGrantManager` 相关的数据与行为。 */
    private class FakeGrantManager : PersistableGrantManager {
        private val persisted = linkedMapOf<Uri, Long>()
        val released = mutableListOf<Uri>()
        var releaseFailureFor: Uri? = null
        var snapshotReads = 0

        fun seed(uri: Uri, persistedAt: Long = 1L) {
            persisted[uri] = persistedAt
        }

        override fun persistedReadUris(): Set<Uri> = persisted.keys.toSet()

        override fun persistedReadGrants(): List<PersistedUriGrant> {
            snapshotReads++
            return persisted.entries
                .sortedWith(
                    compareByDescending<Map.Entry<Uri, Long>> { it.value }
                        .thenBy { it.key.toString() },
                )
                .map { (uri, timestamp) -> PersistedUriGrant(uri, timestamp) }
        }

        override fun takeRead(uri: Uri) {
            persisted[uri] = System.currentTimeMillis()
        }

        override fun releaseRead(uri: Uri) {
            released += uri
            if (uri == releaseFailureFor) error("release failed")
            persisted.remove(uri)
        }
    }

    /** 负责 `FakeUriAccessChecker` 相关的数据与行为。 */
    private class FakeUriAccessChecker : UriAccessChecker {
        val readable = mutableSetOf<Uri>()
        var failOnRead = false

        override fun canRead(uri: Uri): Boolean {
            check(!failOnRead) { "Paging must not open historical media" }
            return uri in readable
        }
    }

    /** 负责 `FakeUriMetadataReader` 相关的数据与行为。 */
    private class FakeUriMetadataReader : UriMetadataReader {
        val types = mutableMapOf<Uri, AlbumMediaType>()
        var failOnStreamProbe = false
        var readCalls = 0
        var cacheInvalidations = 0
        val missingUris = mutableSetOf<Uri>()

        override fun invalidateCache() { cacheInvalidations++ }

        override fun requiredType(uri: Uri): AlbumMediaType =
            types[uri] ?: AlbumMediaType.IMAGE

        override fun read(
            uri: Uri,
            type: AlbumMediaType,
            allowStreamProbe: Boolean,
        ): PickedUriMetadata {
            readCalls++
            if (uri in missingUris) throw java.io.FileNotFoundException("deleted")
            if (failOnStreamProbe) {
                check(!allowStreamProbe) { "Paging must not probe historical content streams" }
            }
            return PickedUriMetadata(
                uri = uri,
                mediaType = type,
                displayName = "picked.jpg",
                mimeType = if (type == AlbumMediaType.IMAGE) "image/jpeg" else "video/mp4",
                sizeBytes = 1_024L,
                width = 100,
                height = 100,
                durationMillis = if (type == AlbumMediaType.VIDEO) 2_000L else null,
            )
        }
    }

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
