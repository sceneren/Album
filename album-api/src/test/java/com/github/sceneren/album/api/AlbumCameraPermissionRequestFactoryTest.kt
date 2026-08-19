package com.github.sceneren.album.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 验证系统相机启动前的权限请求条件。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumCameraPermissionRequestFactoryTest {
    @Test
    fun `声明相机权限但未授权时需要请求`() {
        assertTrue(
            AlbumCameraPermissionRequestFactory.shouldRequest(
                isCameraPermissionDeclared = true,
                isCameraPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `未声明相机权限时不请求`() {
        assertFalse(
            AlbumCameraPermissionRequestFactory.shouldRequest(
                isCameraPermissionDeclared = false,
                isCameraPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `已授权相机权限时不请求`() {
        assertFalse(
            AlbumCameraPermissionRequestFactory.shouldRequest(
                isCameraPermissionDeclared = true,
                isCameraPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `未声明相机权限的宿主不请求`() {
        assertFalse(
            AlbumCameraPermissionRequestFactory.shouldRequest(
                RuntimeEnvironment.getApplication(),
            ),
        )
    }
}
