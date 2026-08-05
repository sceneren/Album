package com.github.sceneren.album.ui.compose

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPickerContractTest {
    @Test
    fun appearanceExtrasKeepCustomFolderVideoAndSelectionIcons() {
        val intent = Intent()
        val appearance = AlbumPickerAppearance(
            backIconRes = 100,
            checkedIconRes = 101,
            uncheckedIconRes = 102,
            videoIconRes = 103,
            folderIconRes = 104,
        )

        AlbumPickerExtras.putAppearance(intent, appearance)

        val restored = AlbumPickerExtras.readAppearance(intent)
        assertEquals(100, restored.backIconRes)
        assertEquals(101, restored.checkedIconRes)
        assertEquals(102, restored.uncheckedIconRes)
        assertEquals(103, restored.videoIconRes)
        assertEquals(104, restored.folderIconRes)
    }
}
