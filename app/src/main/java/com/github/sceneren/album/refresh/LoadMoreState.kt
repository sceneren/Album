package com.github.sceneren.album.refresh

import androidx.compose.runtime.Stable

@Stable
sealed class LoadMoreState {
    data object IDLE : LoadMoreState()
    data object LOADING : LoadMoreState()
    data object ERROR : LoadMoreState()
}