package com.github.sceneren.album.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 `AlbumPickerConfigTest` 覆盖的行为。 */
class AlbumPickerConfigTest {
    @Test
    /** 验证 `requiresSelectionLimitBetweenOneAndOneHundred` 所描述的场景。 */
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
    /** 验证 `compressionDefaultsToDisabledAndSkipsFilesAtOrBelowOneHundredKb` 所描述的场景。 */
    fun compressionDefaultsToDisabledAndSkipsFilesAtOrBelowOneHundredKb() {
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 1)

        assertEquals(false, config.compression.enabled)
        assertEquals(100L, config.compression.skipAtOrBelowKb)
    }
}
