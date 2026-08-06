package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `RoomPickedMediaStoreTest` 覆盖的行为。 */
class RoomPickedMediaStoreTest {
    private lateinit var database: AlbumDatabase
    private lateinit var store: RoomPickedMediaStore

    @Before
    /** 更新 `setUp` 对应的状态。 */
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlbumDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomPickedMediaStore(database)
    }

    @After
    /** 执行 `tearDown` 方法定义的处理。 */
    fun tearDown() = database.close()

    @Test
    /** 验证 `batchKeepsPickerOrderAndFiltersByType` 所描述的场景。 */
    fun batchKeepsPickerOrderAndFiltersByType() = runTest {
        store.upsertBatch(
            listOf(
                draft("content://picked/image", AlbumMediaType.IMAGE, selectedAt = 10),
                draft("content://picked/video", AlbumMediaType.VIDEO, selectedAt = 10),
            ),
        )

        val imagePage = load(AlbumMediaFilter.IMAGES)
        val mixedPage = load(AlbumMediaFilter.IMAGES_AND_VIDEOS)

        assertEquals(listOf("content://picked/image"), imagePage.map(PickedMediaEntity::uri))
        assertEquals(
            listOf("content://picked/image", "content://picked/video"),
            mixedPage.map(PickedMediaEntity::uri),
        )
    }

    @Test
    /** 验证 `duplicateUriUpdatesInsteadOfDuplicating` 所描述的场景。 */
    fun duplicateUriUpdatesInsteadOfDuplicating() = runTest {
        store.upsertBatch(listOf(draft("content://picked/same", AlbumMediaType.IMAGE, 10)))
        store.upsertBatch(listOf(draft("content://picked/same", AlbumMediaType.VIDEO, 20)))

        assertEquals(1, store.all().size)
        assertEquals("VIDEO", store.all().single().mediaType)
    }

    @Test
    /** 验证 `reselectedItemKeepsPersistableGrantOwnership` 所描述的场景。 */
    fun reselectedItemKeepsPersistableGrantOwnership() = runTest {
        store.upsertBatch(
            listOf(
                draft(
                    uri = "content://picked/same",
                    mediaType = AlbumMediaType.IMAGE,
                    selectedAt = 10,
                    ownsPersistableGrant = true,
                ),
            ),
        )
        store.upsertBatch(
            listOf(
                draft(
                    uri = "content://picked/same",
                    mediaType = AlbumMediaType.IMAGE,
                    selectedAt = 20,
                    ownsPersistableGrant = false,
                ),
            ),
        )

        assertTrue(store.all().single().ownsPersistableGrant)
    }

    @Test
    /** 验证 `clearReturnsDeletedRowsAndEmptiesTable` 所描述的场景。 */
    fun clearReturnsDeletedRowsAndEmptiesTable() = runTest {
        store.upsertBatch(
            listOf(
                draft("content://picked/one", AlbumMediaType.IMAGE, 10),
                draft("content://picked/two", AlbumMediaType.VIDEO, 10),
            ),
        )

        val deleted = store.clear()

        assertEquals(2, deleted.size)
        assertTrue(store.all().isEmpty())
    }

    @Test
    /** 验证 `identicalSortOrderFallsBackToUriAscending` 所描述的场景。 */
    fun identicalSortOrderFallsBackToUriAscending() = runTest {
        database.pickedMediaDao().upsertAll(
            listOf(
                entity("content://picked/z", sortOrder = 5),
                entity("content://picked/a", sortOrder = 5),
            ),
        )

        assertEquals(
            listOf("content://picked/a", "content://picked/z"),
            load(AlbumMediaFilter.IMAGES).map(PickedMediaEntity::uri),
        )
    }

    @Test
    /** 验证 `boundedPageUsesSameStableOrderAndOffset` 所描述的场景。 */
    fun boundedPageUsesSameStableOrderAndOffset() = runTest {
        store.upsertBatch(
            listOf(
                draft("content://picked/first", AlbumMediaType.IMAGE, 30),
                draft("content://picked/second", AlbumMediaType.IMAGE, 20),
                draft("content://picked/third", AlbumMediaType.IMAGE, 10),
            ),
        )

        val page = store.loadPage(AlbumMediaFilter.IMAGES, offset = 1, limit = 1)

        assertEquals(listOf("content://picked/second"), page.map(PickedMediaEntity::uri))
    }

    @Test
    /** 验证 `removeReturnsDeletedRow` 所描述的场景。 */
    fun removeReturnsDeletedRow() = runTest {
        store.upsertBatch(listOf(draft("content://picked/remove", AlbumMediaType.IMAGE, 10)))

        val removed = store.remove("content://picked/remove")

        assertEquals("content://picked/remove", removed?.uri)
        assertFalse(store.all().any { it.uri == "content://picked/remove" })
    }

    @Test
    /** 验证 `batchLargerThanSqliteBindLimitIsPersisted` 所描述的场景。 */
    fun batchLargerThanSqliteBindLimitIsPersisted() = runTest {
        val drafts = (0..1_000).map { index ->
            draft(
                uri = "content://picked/image-$index",
                mediaType = AlbumMediaType.IMAGE,
                selectedAt = index.toLong(),
            )
        }

        store.upsertBatch(drafts)

        assertEquals(drafts.size, store.all().size)
    }

    /** 获取 `load` 所需的数据。 */
    private suspend fun load(filter: AlbumMediaFilter): List<PickedMediaEntity> {
        val result = store.pagingSource(filter).load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    /** 执行 `draft` 方法定义的处理。 */
    private fun draft(
        uri: String,
        mediaType: AlbumMediaType,
        selectedAt: Long,
        ownsPersistableGrant: Boolean = false,
    ) = PickedMediaDraft(
        uri = uri,
        mediaType = mediaType.name,
        displayName = null,
        mimeType = null,
        sizeBytes = null,
        width = null,
        height = null,
        durationMillis = null,
        selectedAtEpochMillis = selectedAt,
        ownsPersistableGrant = ownsPersistableGrant,
    )

    /** 执行 `entity` 方法定义的处理。 */
    private fun entity(
        uri: String,
        sortOrder: Long,
    ) = PickedMediaEntity(
        uri = uri,
        mediaType = AlbumMediaType.IMAGE.name,
        displayName = null,
        mimeType = null,
        sizeBytes = null,
        width = null,
        height = null,
        durationMillis = null,
        selectedAtEpochMillis = 10,
        sortOrder = sortOrder,
        ownsPersistableGrant = false,
    )
}
