package com.github.sceneren.photopicker

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.photopicker.ui.theme.AlbumTheme
import org.junit.Rule
import org.junit.Test

/** 验证 `AlbumScreenTest` 覆盖的行为。 */
class AlbumScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    /** 执行 `screenOffersAllThreeMediaFilters` 方法定义的处理。 */
    fun screenOffersAllThreeMediaFilters() {
        composeRule.setContent {
            AlbumTheme {
                DemoScreen(
                    filter = AlbumMediaFilter.IMAGES,
                    compressionEnabled = true,
                    requestPermissionEnable = true,
                    cameraEnable = true,
                    result = null,
                    onFilter = {},
                    onCompression = {},
                    onRequestPermission = {},
                    onCamera = {},
                    onOpenView = {},
                    onOpenCompose = {},
                    onOpenEmbedded = {},
                )
            }
        }

        composeRule.onNodeWithText("图片").assertExists()
        composeRule.onNodeWithText("视频").assertExists()
        composeRule.onNodeWithText("图片和视频").assertExists()
    }
}
