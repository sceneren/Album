package com.github.sceneren.album.api.internal.session

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AlbumPickerSelectionReducerTest` 覆盖的行为。 */
class AlbumPickerSelectionReducerTest {
    @Test
    /** 验证 `cameraItemIsSelectedOnlyWhenCapacityRemains` 所描述的场景。 */
    fun cameraItemIsSelectedOnlyWhenCapacityRemains() {
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, maxSelectionCount = 1)
        val selected = listOf(selection("content://selected"))
        val item = selection("content://camera", source = AlbumPickerItemSource.CAMERA)

        val result = AlbumPickerSelectionReducer.addCamera(
            selected = selected,
            cameraItems = emptyList(),
            cameraItem = item,
            config = config,
        )

        assertEquals(listOf(item), result.cameraItems)
        assertEquals(selected, result.selected)
    }

    @Test
    /** 验证 `togglingTheSameUriRemovesItAndReaddingAppendsIt` 所描述的场景。 */
    fun togglingTheSameUriRemovesItAndReaddingAppendsIt() {
        val item1 = selection("content://one")
        val item2 = selection("content://two")

        val removed = AlbumPickerSelectionReducer.toggle(listOf(item1, item2), item1, 2)
        val readded = AlbumPickerSelectionReducer.toggle(removed, item1, 2)

        assertEquals(listOf(item2), removed)
        assertEquals(listOf(item2, item1), readded)
    }

    /** 执行 `selection` 方法定义的处理。 */
    private fun selection(uri: String, source: AlbumPickerItemSource = AlbumPickerItemSource.MEDIA_STORE) =
        AlbumPickerSelection(
            uri = Uri.parse(uri),
            mediaType = AlbumMediaType.IMAGE,
            displayName = null,
            mimeType = "image/jpeg",
            sizeBytes = 1,
            width = null,
            height = null,
            durationMillis = null,
            source = source,
        )
}
