package com.github.sceneren.photopicker

import com.github.sceneren.album.api.AlbumMediaFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 在开发主机上执行的本地单元测试示例。
 */
class ExampleUnitTest {
    @Test
    /** 验证 `mediaFilterMapsToExpectedStringResources` 所描述的场景。 */
    fun mediaFilterMapsToExpectedStringResources() {
        assertEquals(R.string.filter_images, AlbumMediaFilter.IMAGES.labelRes())
        assertEquals(R.string.filter_videos, AlbumMediaFilter.VIDEOS.labelRes())
        assertEquals(R.string.filter_images_and_videos, AlbumMediaFilter.IMAGES_AND_VIDEOS.labelRes())
    }
}
