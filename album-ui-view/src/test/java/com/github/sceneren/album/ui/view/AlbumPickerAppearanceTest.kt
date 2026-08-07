package com.github.sceneren.album.ui.view

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.MediaAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证 `AlbumPickerAppearanceTest` 覆盖的行为。 */
class AlbumPickerAppearanceTest {
    @Test
    /** 验证 `动画配置支持宿主资源和null禁用` 所描述的场景。 */
    fun `动画配置支持宿主资源和null禁用`() {
        val custom = AlbumPickerAnimation(
            openEnterResId = 100,
            openExitResId = 101,
            closeEnterResId = 102,
            closeExitResId = 103,
        )
        val customIntent = Intent()
        AlbumPickerExtras.putAnimation(customIntent, custom)

        assertEquals(custom, AlbumPickerExtras.readAnimation(customIntent))

        val disabledIntent = Intent()
        AlbumPickerExtras.putAnimation(disabledIntent, null)
        assertNull(AlbumPickerExtras.readAnimation(disabledIntent))
    }

    @Test
    /** 验证 `默认主题使用底部弹出动画` 所描述的场景。 */
    fun `默认主题使用底部弹出动画`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val theme = context.resources.newTheme().apply {
            applyStyle(R.style.auv_theme_album_picker, true)
        }
        val windowAnimations = theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowAnimationStyle),
        ).let { attributes ->
            try {
                attributes.getResourceId(0, 0)
            } finally {
                attributes.recycle()
            }
        }

        assertEquals(R.style.auv_animation_album_picker, windowAnimations)
        assertAnimationStyle(
            context = context,
            styleResId = windowAnimations,
            expectedOpenEnter = R.anim.auv_album_picker_enter,
            expectedOpenExit = R.anim.auv_album_picker_hold,
            expectedCloseEnter = R.anim.auv_album_picker_hold,
            expectedCloseExit = R.anim.auv_album_picker_exit,
        )
    }

    @Test
    /** 验证 `默认网格配置为1dp间距和每行4个` 所描述的场景。 */
    fun `默认网格配置为1dp间距和每行4个`() {
        val appearance = AlbumPickerAppearance()

        assertEquals(1, appearance.gridItemSpacingDp)
        assertEquals(4, appearance.gridSpanCount)
    }

    @Test
    /** 验证 `自定义网格配置可以通过Intent恢复` 所描述的场景。 */
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
    /** 验证 `间距不能小于0` 所描述的场景。 */
    fun `间距不能小于0`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumPickerAppearance(gridItemSpacingDp = -1)
        }
    }

    @Test
    /** 验证 `每行item数量必须在允许范围内` 所描述的场景。 */
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
    /** 验证 `外部允许且未完全授权时显示权限按钮` 所描述的场景。 */
    fun `外部允许且未完全授权时显示权限按钮`() {
        assertTrue(shouldShowPermissionUpgradeButton(true, true, MediaAccessStatus.DENIED))
        assertTrue(shouldShowPermissionUpgradeButton(true, true, MediaAccessStatus.PARTIAL))
    }

    @Test
    /** 验证 `外部不允许或已完全授权时隐藏权限按钮` 所描述的场景。 */
    fun `外部不允许或已完全授权时隐藏权限按钮`() {
        assertFalse(shouldShowPermissionUpgradeButton(false, true, MediaAccessStatus.DENIED))
        assertFalse(shouldShowPermissionUpgradeButton(false, true, MediaAccessStatus.PARTIAL))
        assertFalse(shouldShowPermissionUpgradeButton(true, true, MediaAccessStatus.FULL))
    }

    @Test
    /** 验证 `Manifest未声明对应权限时隐藏权限按钮` 所描述的场景。 */
    fun `Manifest未声明对应权限时隐藏权限按钮`() {
        assertFalse(shouldShowPermissionUpgradeButton(true, false, MediaAccessStatus.DENIED))
        assertFalse(shouldShowPermissionUpgradeButton(true, false, MediaAccessStatus.PARTIAL))
    }

    /** 执行 `assertAnimationStyle` 方法定义的处理。 */
    private fun assertAnimationStyle(
        context: Context,
        styleResId: Int,
        expectedOpenEnter: Int,
        expectedOpenExit: Int,
        expectedCloseEnter: Int,
        expectedCloseExit: Int,
    ) {
        val attributes = context.obtainStyledAttributes(
            styleResId,
            intArrayOf(
                android.R.attr.activityOpenEnterAnimation,
                android.R.attr.activityOpenExitAnimation,
                android.R.attr.activityCloseEnterAnimation,
                android.R.attr.activityCloseExitAnimation,
            ),
        )
        try {
            assertEquals(expectedOpenEnter, attributes.getResourceId(0, 0))
            assertEquals(expectedOpenExit, attributes.getResourceId(1, 0))
            assertEquals(expectedCloseEnter, attributes.getResourceId(2, 0))
            assertEquals(expectedCloseExit, attributes.getResourceId(3, 0))
        } finally {
            attributes.recycle()
        }
    }
}
