package com.github.sceneren.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {
    private val _imageDirectoriesFlow = MutableStateFlow<List<ImageDirectory>>(emptyList())
    val imageDirectoriesState: StateFlow<List<ImageDirectory>> get() = _imageDirectoriesFlow

    private val _imageListFlow = MutableStateFlow<List<ImageItem>>(emptyList())
    val imageListState: StateFlow<List<ImageItem>> get() = _imageListFlow

    fun getImageDirectories() {
        viewModelScope.launch {
            _imageDirectoriesFlow.value = AlbumLoader.getImageDirectories()
        }
    }

    fun getAllImages() {
        viewModelScope.launch {
            _imageListFlow.value = AlbumLoader.getAllImages(page = 1).data
        }
    }

}