package com.github.sceneren.album.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumPickerConfigTest {
    @Test
    fun requiresSelectionLimitBetweenOneAndOneHundred() {
        assertEquals(100, AlbumPickerConfig(AlbumMediaFilter.IMAGES, 100).maxSelectionCount)

        assertTrue(runCatching {
            AlbumPickerConfig(AlbumMediaFilter.IMAGES, 0)
        }.exceptionOrNull() is IllegalArgumentException)

        assertTrue(runCatching {
            AlbumPickerConfig(AlbumMediaFilter.IMAGES, 101)
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun compressionDefaultsToDisabledAndSkipsFilesAtOrBelowOneHundredKb() {
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1)

        assertEquals(false, config.compression.enabled)
        assertEquals(100L, config.compression.skipAtOrBelowKb)
    }
}
