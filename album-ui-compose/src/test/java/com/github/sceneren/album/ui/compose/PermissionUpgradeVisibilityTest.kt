package com.github.sceneren.album.ui.compose

import com.github.sceneren.album.api.MediaAccessStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 Compose 列表页权限入口的显示条件。 */
class PermissionUpgradeVisibilityTest {
    @Test
    fun `外部允许且Manifest已声明并未完全授权时显示`() {
        assertTrue(shouldShowPermissionUpgradeButton(true, true, MediaAccessStatus.DENIED))
        assertTrue(shouldShowPermissionUpgradeButton(true, true, MediaAccessStatus.PARTIAL))
    }

    @Test
    fun `任一前置条件不满足时隐藏`() {
        assertFalse(shouldShowPermissionUpgradeButton(false, true, MediaAccessStatus.DENIED))
        assertFalse(shouldShowPermissionUpgradeButton(true, false, MediaAccessStatus.DENIED))
        assertFalse(shouldShowPermissionUpgradeButton(true, true, MediaAccessStatus.FULL))
    }
}
