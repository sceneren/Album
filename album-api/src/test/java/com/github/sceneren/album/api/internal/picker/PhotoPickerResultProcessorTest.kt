package com.github.sceneren.album.api.internal.picker

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.PhotoPickFailure
import com.github.sceneren.album.api.PhotoPickResult
import com.github.sceneren.album.api.internal.database.PickedMediaDraft
import com.github.sceneren.album.api.internal.database.PickedMediaEntity
import com.github.sceneren.album.api.internal.database.PickedMediaStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoPickerResultProcessorTest {
    private lateinit var grants: FakeGrantManager
    private lateinit var metadata: FakeMetadataReader
    private lateinit var store: FakePickedMediaStore
    private lateinit var processor: PhotoPickerResultProcessor

    @Before
    fun setUp() {
        grants = FakeGrantManager()
        metadata = FakeMetadataReader()
        store = FakePickedMediaStore()
        processor = PhotoPickerResultProcessor(
            grantManager = grants,
            metadataReader = metadata,
            store = store,
            clockMillis = { 123L },
        )
    }

    @Test
    fun explicitOverflowFailsBeforeTakingGrants() = runTest {
        val result = processor.process(
            uris = listOf(uri("1"), uri("2"), uri("3")),
            mediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount = 2,
        )

        assertEquals(
            PhotoPickResult.Failed(PhotoPickFailure.SELECTION_LIMIT_EXCEEDED),
            result,
        )
        assertTrue(grants.taken.isEmpty())
        assertTrue(store.upsertCalls.isEmpty())
    }

    @Test
    fun databaseFailureReleasesOnlyNewGrants() = runTest {
        grants.persisted += uri("existing")
        store.failure = SQLiteException("write failed")

        val result = processor.process(
            uris = listOf(uri("existing"), uri("new")),
            mediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        )

        assertEquals(
            PhotoPickFailure.DATABASE_WRITE_FAILED,
            (result as PhotoPickResult.Failed).reason,
        )
        assertEquals(listOf(uri("new")), grants.released)
    }

    @Test
    fun emptyResultIsCancellation() = runTest {
        assertEquals(
            PhotoPickResult.Cancelled,
            processor.process(emptyList(), AlbumMediaFilter.IMAGES, maxSelectionCount = null),
        )
    }

    @Test
    fun duplicateUrisKeepFirstOccurrenceOrder() = runTest {
        val result = processor.process(
            uris = listOf(uri("2"), uri("1"), uri("2")),
            mediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        ) as PhotoPickResult.Selected

        assertEquals(
            listOf(uri("2"), uri("1")),
            store.upsertCalls.single().map { Uri.parse(it.uri) },
        )
        assertEquals(listOf(uri("2"), uri("1")), result.media.map { it.uri })
    }

    @Test
    fun disallowedMimeFailsBeforeTakingGrants() = runTest {
        metadata.types[uri("image")] = AlbumMediaType.IMAGE

        val result = processor.process(
            uris = listOf(uri("image")),
            mediaFilter = AlbumMediaFilter.VIDEOS,
            maxSelectionCount = null,
        )

        assertEquals(
            PhotoPickResult.Failed(PhotoPickFailure.MEDIA_TYPE_NOT_ALLOWED),
            result,
        )
        assertTrue(grants.taken.isEmpty())
        assertTrue(store.upsertCalls.isEmpty())
    }

    @Test
    fun singleVideoAndMixedSelectionsSucceed() = runTest {
        metadata.types[uri("video")] = AlbumMediaType.VIDEO
        val imageResult = processor.process(
            listOf(uri("image")),
            AlbumMediaFilter.IMAGES,
            maxSelectionCount = 1,
        ) as PhotoPickResult.Selected
        val videoResult = processor.process(
            listOf(uri("video")),
            AlbumMediaFilter.VIDEOS,
            maxSelectionCount = 1,
        ) as PhotoPickResult.Selected
        val mixedResult = processor.process(
            listOf(uri("image"), uri("video")),
            AlbumMediaFilter.IMAGES_AND_VIDEOS,
            maxSelectionCount = null,
        ) as PhotoPickResult.Selected

        assertEquals(listOf(AlbumMediaType.IMAGE), imageResult.media.map { it.mediaType })
        assertEquals(listOf(AlbumMediaType.VIDEO), videoResult.media.map { it.mediaType })
        assertEquals(
            listOf(AlbumMediaType.IMAGE, AlbumMediaType.VIDEO),
            mixedResult.media.map { it.mediaType },
        )
    }

    @Test
    fun grantFailureReleasesEarlierNewGrantsInReverseOrder() = runTest {
        grants.takeFailureFor = uri("3")

        val result = processor.process(
            listOf(uri("1"), uri("2"), uri("3")),
            AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        )

        assertEquals(
            PhotoPickFailure.PERSISTABLE_PERMISSION_FAILED,
            (result as PhotoPickResult.Failed).reason,
        )
        assertEquals(listOf(uri("2"), uri("1")), grants.released)
        assertTrue(store.upsertCalls.isEmpty())
    }

    @Test
    fun metadataFailureReleasesAllNewGrants() = runTest {
        metadata.readFailureFor = uri("2")

        val result = processor.process(
            listOf(uri("1"), uri("2")),
            AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        )

        assertEquals(
            PhotoPickFailure.METADATA_READ_FAILED,
            (result as PhotoPickResult.Failed).reason,
        )
        assertEquals(listOf(uri("2"), uri("1")), grants.released)
        assertTrue(store.upsertCalls.isEmpty())
    }

    @Test
    fun existingDatabaseGrantOwnershipIsPreserved() = runTest {
        val existingUri = uri("existing")
        grants.persisted += existingUri
        store.seed(existingUri, ownsPersistableGrant = true)

        processor.process(
            listOf(existingUri),
            AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        )

        assertTrue(store.all().single().ownsPersistableGrant)
        assertTrue(grants.taken.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveLimitIsRejected() = runTest {
        processor.process(listOf(uri("1")), AlbumMediaFilter.IMAGES, maxSelectionCount = 0)
    }

    private class FakeGrantManager : PersistableGrantManager {
        val persisted = linkedSetOf<Uri>()
        val taken = mutableListOf<Uri>()
        val released = mutableListOf<Uri>()
        var takeFailureFor: Uri? = null

        override fun persistedReadUris(): Set<Uri> = persisted.toSet()

        override fun takeRead(uri: Uri) {
            if (uri == takeFailureFor) error("grant failed")
            taken += uri
            persisted += uri
        }

        override fun releaseRead(uri: Uri) {
            released += uri
            persisted -= uri
        }
    }

    private class FakeMetadataReader : UriMetadataReader {
        val types = mutableMapOf<Uri, AlbumMediaType>()
        var readFailureFor: Uri? = null

        override fun requiredType(uri: Uri): AlbumMediaType =
            types[uri] ?: AlbumMediaType.IMAGE

        override fun read(uri: Uri, type: AlbumMediaType): PickedUriMetadata {
            if (uri == readFailureFor) error("metadata failed")
            return PickedUriMetadata(
                uri = uri,
                mediaType = type,
                displayName = uri.lastPathSegment,
                mimeType = if (type == AlbumMediaType.IMAGE) "image/jpeg" else "video/mp4",
                sizeBytes = 100,
                width = 10,
                height = 20,
                durationMillis = if (type == AlbumMediaType.VIDEO) 1_000 else null,
            )
        }
    }

    private class FakePickedMediaStore : PickedMediaStore {
        val upsertCalls = mutableListOf<List<PickedMediaDraft>>()
        var failure: Throwable? = null
        private val rows = linkedMapOf<String, PickedMediaEntity>()

        fun seed(uri: Uri, ownsPersistableGrant: Boolean) {
            rows[uri.toString()] = PickedMediaDraft(
                uri = uri.toString(),
                mediaType = AlbumMediaType.IMAGE.name,
                displayName = null,
                mimeType = "image/jpeg",
                sizeBytes = null,
                width = null,
                height = null,
                durationMillis = null,
                selectedAtEpochMillis = 1,
                ownsPersistableGrant = ownsPersistableGrant,
            ).toEntity(sortOrder = 1, ownsPersistableGrant = ownsPersistableGrant)
        }

        override fun pagingSource(filter: AlbumMediaFilter) =
            object : PagingSource<Int, PickedMediaEntity>() {
                override suspend fun load(
                    params: LoadParams<Int>,
                ): LoadResult<Int, PickedMediaEntity> = LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null,
                )

                override fun getRefreshKey(
                    state: PagingState<Int, PickedMediaEntity>,
                ): Int? = null
            }

        override suspend fun loadPage(
            filter: AlbumMediaFilter,
            offset: Int,
            limit: Int,
        ): List<PickedMediaEntity> = rows.values.drop(offset).take(limit)

        override suspend fun upsertBatch(
            drafts: List<PickedMediaDraft>,
        ): List<PickedMediaEntity> {
            upsertCalls += drafts
            failure?.let { throw it }
            return drafts.mapIndexed { index, draft ->
                draft.toEntity(
                    sortOrder = drafts.size.toLong() - index,
                    ownsPersistableGrant =
                        rows[draft.uri]?.ownsPersistableGrant == true ||
                            draft.ownsPersistableGrant,
                ).also { rows[it.uri] = it }
            }
        }

        override suspend fun find(uri: String): PickedMediaEntity? = rows[uri]

        override suspend fun remove(uri: String): PickedMediaEntity? = rows.remove(uri)

        override suspend fun clear(): List<PickedMediaEntity> = rows.values.toList().also {
            rows.clear()
        }

        override suspend fun all(): List<PickedMediaEntity> = rows.values.toList()
    }

    private fun uri(value: String): Uri = Uri.parse("content://picker/$value")
}
