package com.github.sceneren.album.ui.compose

import androidx.paging.LoadState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Initial loading may cover an empty grid, but must never remove its existing content. */
class MediaGridLoadingStateTest {
    @Test
    fun missingPresenterKeepsInitialLoading() {
        assertTrue(isMediaGridInitialLoading(refreshLoadState = null, presentedItemCount = 0))
    }

    @Test
    fun firstRefreshKeepsInitialLoading() {
        assertTrue(isMediaGridInitialLoading(LoadState.Loading, presentedItemCount = 0))
    }

    @Test
    fun completedEmptyGridShowsActionsInsteadOfSpinner() {
        assertFalse(isMediaGridInitialLoading(LoadState.NotLoading(false), presentedItemCount = 0))
    }

    @Test
    fun refreshingExistingItemsDoesNotHideGrid() {
        assertFalse(isMediaGridInitialLoading(LoadState.Loading, presentedItemCount = 4))
    }

    @Test
    fun cameraItemsRemainVisibleBeforeFeedStarts() {
        assertFalse(isMediaGridInitialLoading(refreshLoadState = null, presentedItemCount = 1))
    }

    @Test
    fun refreshFailureDoesNotHideRetryOrExistingContent() {
        val error = LoadState.Error(IllegalStateException("Provider unavailable"))
        assertFalse(isMediaGridInitialLoading(error, presentedItemCount = 0))
        assertFalse(isMediaGridInitialLoading(error, presentedItemCount = 4))
    }
}
