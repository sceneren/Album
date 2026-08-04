package com.github.sceneren.album.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumMediaContractsTest {
    @Test
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
    fun allDirectoryUsesReservedBucketId() {
        assertEquals(Long.MIN_VALUE, AlbumDirectory.ALL_BUCKET_ID)
    }
}
