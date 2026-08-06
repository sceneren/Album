package com.github.sceneren.album.api

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 `AlbumMediaContractsTest` 覆盖的行为。 */
class AlbumMediaContractsTest {
    @Test
    /** 验证 `filtersExposeTheThreeApprovedModes` 所描述的场景。 */
    fun filtersExposeTheThreeApprovedModes() {
        assertEquals(
            listOf(
                AlbumMediaFilter.IMAGES,
                AlbumMediaFilter.VIDEOS,
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
            ),
            AlbumMediaFilter.entries,
        )
    }

    @Test
    /** 验证 `allDirectoryUsesReservedBucketId` 所描述的场景。 */
    fun allDirectoryUsesReservedBucketId() {
        assertEquals(Long.MIN_VALUE, AlbumDirectory.ALL_BUCKET_ID)
    }
}
