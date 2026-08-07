package com.github.sceneren.album.api

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证媒体权限请求与 Manifest 声明的匹配规则。 */
class AlbumMediaPermissionRequestFactoryTest {
    @Test
    fun `全部待申请权限均已声明时返回true`() {
        val required = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )

        assertTrue(
            AlbumMediaPermissionRequestFactory.areRequiredPermissionsDeclared(
                requiredPermissions = required,
                declaredPermissions = required.toSet(),
            ),
        )
    }

    @Test
    fun `缺少任一待申请权限时返回false`() {
        val required = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )

        assertFalse(
            AlbumMediaPermissionRequestFactory.areRequiredPermissionsDeclared(
                requiredPermissions = required,
                declaredPermissions = setOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                ),
            ),
        )
    }

    @Test
    fun `未声明待申请权限时返回false`() {
        assertFalse(
            AlbumMediaPermissionRequestFactory.areRequiredPermissionsDeclared(
                requiredPermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                declaredPermissions = emptySet(),
            ),
        )
    }
}
