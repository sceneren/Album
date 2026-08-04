package com.github.sceneren.album.api.internal.picker

import androidx.activity.result.contract.ActivityResultContracts
import com.github.sceneren.album.api.AlbumMediaFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoPickerContractFactoryTest {
    @Test
    fun oneUsesSingleAndNullUsesPlatformCappedMultiple() {
        assertTrue(PhotoPickerContractFactory.create(1) is PickerContract.Single)
        assertTrue(PhotoPickerContractFactory.create(null) is PickerContract.MultipleDefault)
    }

    @Test
    fun explicitMultipleKeepsLimit() {
        assertEquals(
            PickerContract.Multiple(maxItems = 7),
            PhotoPickerContractFactory.create(7),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroIsRejected() {
        PhotoPickerContractFactory.create(0)
    }

    @Test
    fun filtersMapToApprovedPickerMediaTypes() {
        assertSame(
            ActivityResultContracts.PickVisualMedia.ImageOnly,
            AlbumMediaFilter.IMAGES.toPickerRequest().mediaType,
        )
        assertSame(
            ActivityResultContracts.PickVisualMedia.VideoOnly,
            AlbumMediaFilter.VIDEOS.toPickerRequest().mediaType,
        )
        assertSame(
            ActivityResultContracts.PickVisualMedia.ImageAndVideo,
            AlbumMediaFilter.IMAGES_AND_VIDEOS.toPickerRequest().mediaType,
        )
    }
}
