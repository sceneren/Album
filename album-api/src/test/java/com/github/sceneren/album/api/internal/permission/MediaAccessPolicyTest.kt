package com.github.sceneren.album.api.internal.permission

import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.MediaAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 `MediaAccessPolicyTest` 覆盖的行为。 */
class MediaAccessPolicyTest {
    @Test
    /** 验证 `api34MixedRequiresBothFullPermissions` 所描述的场景。 */
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
    /** 验证 `api34VisualSelectionIsPartialForRequestedType` 所描述的场景。 */
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
    /** 验证 `unrelatedPermissionIsDenied` 所描述的场景。 */
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
    /** 验证 `legacyReadCoversEveryFilter` 所描述的场景。 */
    fun legacyReadCoversEveryFilter() {
        AlbumMediaFilter.entries.forEach { filter ->
            assertEquals(
                MediaAccessStatus.FULL,
                MediaAccessPolicy.resolve(filter, snapshot(sdk = 32, legacy = true)),
            )
        }
    }

    /** 执行 `snapshot` 方法定义的处理。 */
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
