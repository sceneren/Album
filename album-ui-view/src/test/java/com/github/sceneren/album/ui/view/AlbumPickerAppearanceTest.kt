package com.github.sceneren.album.ui.view

import android.content.Intent
import com.github.sceneren.album.api.MediaAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPickerAppearanceTest {
    @Test
    fun `默认网格配置为1dp间距和每行4个`() {
        val appearance = AlbumPickerAppearance()

        assertEquals(1, appearance.gridItemSpacingDp)
        assertEquals(4, appearance.gridSpanCount)
    }

    @Test
    fun `自定义网格配置可以通过Intent恢复`() {
        val intent = Intent()
        AlbumPickerExtras.putAppearance(
            intent,
            AlbumPickerAppearance(
                gridItemSpacingDp = 3,
                gridSpanCount = 5,
            ),
        )

        val restored = AlbumPickerExtras.readAppearance(intent)

        assertEquals(3, restored.gridItemSpacingDp)
        assertEquals(5, restored.gridSpanCount)
    }

    @Test
    fun `间距不能小于0`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumPickerAppearance(gridItemSpacingDp = -1)
        }
    }

    @Test
    fun `每行item数量必须在允许范围内`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumPickerAppearance(gridSpanCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlbumPickerAppearance(
                gridSpanCount = AlbumPickerAppearance.MAX_GRID_SPAN_COUNT + 1,
            )
        }
    }

    @Test
    fun `外部允许且未完全授权时显示权限按钮`() {
        assertTrue(shouldShowPermissionUpgradeButton(true, MediaAccessStatus.DENIED))
        assertTrue(shouldShowPermissionUpgradeButton(true, MediaAccessStatus.PARTIAL))
    }

    @Test
    fun `外部不允许或已完全授权时隐藏权限按钮`() {
        assertFalse(shouldShowPermissionUpgradeButton(false, MediaAccessStatus.DENIED))
        assertFalse(shouldShowPermissionUpgradeButton(false, MediaAccessStatus.PARTIAL))
        assertFalse(shouldShowPermissionUpgradeButton(true, MediaAccessStatus.FULL))
    }
}
