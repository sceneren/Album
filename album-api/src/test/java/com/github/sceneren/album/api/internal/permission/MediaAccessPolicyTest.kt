package com.github.sceneren.album.api.internal.permission

import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.MediaAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessPolicyTest {
    @Test
    fun api34MixedRequiresBothFullPermissions() {
        val imageOnly = snapshot(sdk = 34, images = true)
        assertEquals(
            MediaAccessStatus.PARTIAL,
            MediaAccessPolicy.resolve(AlbumMediaFilter.IMAGES_AND_VIDEOS, imageOnly),
        )
        assertEquals(
            MediaAccessStatus.FULL,
            MediaAccessPolicy.resolve(
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
                snapshot(sdk = 34, images = true, videos = true),
            ),
        )
    }

    @Test
    fun api34VisualSelectionIsPartialForRequestedType() {
        assertEquals(
            MediaAccessStatus.PARTIAL,
            MediaAccessPolicy.resolve(
                AlbumMediaFilter.VIDEOS,
                snapshot(sdk = 34, visualSelected = true),
            ),
        )
    }

    @Test
    fun unrelatedPermissionIsDenied() {
        assertEquals(
            MediaAccessStatus.DENIED,
            MediaAccessPolicy.resolve(
                AlbumMediaFilter.IMAGES,
                snapshot(sdk = 33, videos = true),
            ),
        )
    }

    @Test
    fun legacyReadCoversEveryFilter() {
        AlbumMediaFilter.entries.forEach { filter ->
            assertEquals(
                MediaAccessStatus.FULL,
                MediaAccessPolicy.resolve(filter, snapshot(sdk = 32, legacy = true)),
            )
        }
    }

    private fun snapshot(
        sdk: Int,
        legacy: Boolean = false,
        images: Boolean = false,
        videos: Boolean = false,
        visualSelected: Boolean = false,
    ) = MediaPermissionSnapshot(
        sdkInt = sdk,
        readExternalStorage = legacy,
        readMediaImages = images,
        readMediaVideo = videos,
        readVisualUserSelected = visualSelected,
    )
}
