package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.PhotoPickFailure
import com.github.sceneren.album.api.PhotoPickResult
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
/** 验证 `PhotoPickerResultProcessorTest` 覆盖的行为。 */
class PhotoPickerResultProcessorTest {
    private lateinit var grants: FakeGrantManager
    private lateinit var metadata: FakeMetadataReader
    private lateinit var processor: PhotoPickerResultProcessor

    @Before
    /** 更新 `setUp` 对应的状态。 */
    fun setUp() {
        grants = FakeGrantManager()
        metadata = FakeMetadataReader()
        processor = PhotoPickerResultProcessor(
            grantManager = grants,
            metadataReader = metadata,
            clockMillis = { 123L },
        )
    }

    @Test
    /** 验证 `explicitOverflowFailsBeforeTakingGrants` 所描述的场景。 */
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
    }

    @Test
    /** 验证 `emptyResultIsCancellation` 所描述的场景。 */
    fun emptyResultIsCancellation() = runTest {
        assertEquals(
            PhotoPickResult.Cancelled,
            processor.process(emptyList(), AlbumMediaFilter.IMAGES, maxSelectionCount = null),
        )
    }

    @Test
    /** 验证 `duplicateUrisKeepFirstOccurrenceOrder` 所描述的场景。 */
    fun duplicateUrisKeepFirstOccurrenceOrder() = runTest {
        val result = processor.process(
            uris = listOf(uri("2"), uri("1"), uri("2")),
            mediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        ) as PhotoPickResult.Selected

        assertEquals(listOf(uri("2"), uri("1")), grants.taken)
        assertEquals(listOf(uri("2"), uri("1")), result.media.map { it.uri })
    }

    @Test
    /** 验证 `disallowedMimeFailsBeforeTakingGrants` 所描述的场景。 */
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
    }

    @Test
    /** 验证 `singleVideoAndMixedSelectionsSucceed` 所描述的场景。 */
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
    /** 验证 `grantFailureReleasesEarlierNewGrantsInReverseOrder` 所描述的场景。 */
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
    }

    @Test
    /** 验证 `metadataFailureReleasesOnlyNewGrants` 所描述的场景。 */
    fun metadataFailureReleasesOnlyNewGrants() = runTest {
        grants.persisted += uri("existing")
        metadata.readFailureFor = uri("2")

        val result = processor.process(
            listOf(uri("existing"), uri("1"), uri("2")),
            AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        )

        assertEquals(
            PhotoPickFailure.METADATA_READ_FAILED,
            (result as PhotoPickResult.Failed).reason,
        )
        assertEquals(listOf(uri("2"), uri("1")), grants.released)
        assertEquals(setOf(uri("existing")), grants.persisted)
    }

    @Test
    /** 验证 `existingSystemGrantIsNotTakenAgain` 所描述的场景。 */
    fun existingSystemGrantIsNotTakenAgain() = runTest {
        val existingUri = uri("existing")
        grants.persisted += existingUri

        val result = processor.process(
            listOf(existingUri),
            AlbumMediaFilter.IMAGES,
            maxSelectionCount = null,
        ) as PhotoPickResult.Selected

        assertEquals(listOf(existingUri), result.media.map { it.uri })
        assertTrue(grants.taken.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    /** 验证 `nonPositiveLimitIsRejected` 所描述的场景。 */
    fun nonPositiveLimitIsRejected() = runTest {
        processor.process(listOf(uri("1")), AlbumMediaFilter.IMAGES, maxSelectionCount = 0)
    }

    /** 负责 `FakeGrantManager` 相关的数据与行为。 */
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

    /** 负责 `FakeMetadataReader` 相关的数据与行为。 */
    private class FakeMetadataReader : UriMetadataReader {
        val types = mutableMapOf<Uri, AlbumMediaType>()
        var readFailureFor: Uri? = null

        override fun requiredType(uri: Uri): AlbumMediaType =
            types[uri] ?: AlbumMediaType.IMAGE

        override fun read(
            uri: Uri,
            type: AlbumMediaType,
            allowStreamProbe: Boolean,
        ): PickedUriMetadata {
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

    private fun uri(value: String): Uri = Uri.parse("content://picker/$value")
}
