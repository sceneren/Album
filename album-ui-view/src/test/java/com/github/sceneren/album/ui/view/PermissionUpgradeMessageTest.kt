package com.github.sceneren.album.ui.view

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumMediaFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 验证 View 选择器权限提示与当前可选择的媒体类型一致。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionUpgradeMessageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun imageOnlyModeKeepsPhotoCopy() {
        assertEquals(
            "你已设置Album只能访问相册部分照片，建议设为「允许访问所有照片」",
            message(AlbumMediaFilter.IMAGES),
        )
    }

    @Test
    fun videoOnlyModeUsesVideoCopy() {
        assertEquals(
            "你已设置Album只能访问相册部分视频，建议设为「允许访问所有视频」",
            message(AlbumMediaFilter.VIDEOS),
        )
    }

    @Test
    fun mixedModeUsesMediaCopy() {
        assertEquals(
            "你已设置Album只能访问相册部分媒体，建议设为「允许访问所有媒体」",
            message(AlbumMediaFilter.IMAGES_AND_VIDEOS),
        )
    }

    @Test
    fun messageFormatsHostApplicationName() {
        assertEquals(
            "你已设置示例应用只能访问相册部分视频，建议设为「允许访问所有视频」",
            context.getString(AlbumMediaFilter.VIDEOS.permissionUpgradeMessageRes(), "示例应用"),
        )
    }

    private fun message(filter: AlbumMediaFilter): String =
        context.getString(filter.permissionUpgradeMessageRes(), "Album")
}
