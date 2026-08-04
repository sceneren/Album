package com.github.sceneren.album

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.sceneren.album.api.AlbumMedia
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class AlbumScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenOffersAllThreeMediaFilters() {
        composeRule.setContent {
            AlbumScreen(
                state = AlbumUiState(),
                media = flowOf(PagingData.empty<AlbumMedia>()).collectAsLazyPagingItems(),
                onFilterChanged = {},
                onRequestPermission = {},
                onOpenPicker = {},
                onDirectorySelected = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("图片").assertExists()
        composeRule.onNodeWithText("视频").assertExists()
        composeRule.onNodeWithText("图片和视频").assertExists()
    }
}
