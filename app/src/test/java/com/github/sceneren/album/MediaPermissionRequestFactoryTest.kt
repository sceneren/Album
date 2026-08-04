package com.github.sceneren.album

import android.Manifest
import com.github.sceneren.album.api.AlbumMediaFilter
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MediaPermissionRequestFactoryTest {
    @Test
    fun api34MixedRequestsBothFullAndPartialPermissions() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ),
            MediaPermissionRequestFactory.create(
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
                sdkInt = 34,
            ),
        )
    }

    @Test
    fun api33VideoRequestsOnlyVideo() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
            MediaPermissionRequestFactory.create(AlbumMediaFilter.VIDEOS, 33),
        )
    }

    @Test
    fun api32UsesLegacyRead() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            MediaPermissionRequestFactory.create(AlbumMediaFilter.IMAGES, 32),
        )
    }
}
