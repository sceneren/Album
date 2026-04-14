package com.github.sceneren.album

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sceneren.album.refresh.LoadMoreState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {
    private val _imageDirectoriesFlow = MutableStateFlow<List<ImageDirectory>>(emptyList())
    val imageDirectoriesState: StateFlow<List<ImageDirectory>> get() = _imageDirectoriesFlow

    private val _imageListFlow = MutableStateFlow<List<ImageItem>>(emptyList())
    val imageListState: StateFlow<List<ImageItem>> get() = _imageListFlow

    private val _loadMoreStateFlow = MutableStateFlow<LoadMoreState>(LoadMoreState.IDLE)
    val loadMoreState: StateFlow<LoadMoreState> get() = _loadMoreStateFlow

    private val _currentDirFlow = MutableStateFlow<ImageDirectory?>(null)
    val currentDir: StateFlow<ImageDirectory?> get() = _currentDirFlow

    private val _hasMoreFlow = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> get() = _hasMoreFlow

    private var currentPage = 1

    fun getImageDirectories() {
        viewModelScope.launch {
            _imageDirectoriesFlow.value = AlbumLoader.getImageDirectories()
        }
    }

    fun setCurrentDir(directory: ImageDirectory?) {
        _currentDirFlow.value = directory
        currentPage = 1
        getImagesByDirectory()
    }

    fun getAllImages() {
        viewModelScope.launch {
            _imageListFlow.value = AlbumLoader.getAllImages(page = 1).data
        }
    }

    private fun getImagesByDirectory() {
        viewModelScope.launch {
            val result = AlbumLoader.getImagesByDirectory(_currentDirFlow.value?.bucketId ?: ImageDirectory.ALL_BUCKET_ID, page = 1)
            _imageListFlow.value = result.data
            _loadMoreStateFlow.value = LoadMoreState.IDLE
            _hasMoreFlow.value = result.hasNextPage
        }
    }

    fun loadMoreImages() {
        Log.e("AlbumViewModel", "loadMoreImages: ")
        viewModelScope.launch {
            _loadMoreStateFlow.value = LoadMoreState.LOADING
            val bucketId = _currentDirFlow.value?.bucketId ?: ImageDirectory.ALL_BUCKET_ID
            val result = AlbumLoader.getImagesByDirectory(bucketId, page = ++currentPage)
            _imageListFlow.value += result.data
            _loadMoreStateFlow.value = LoadMoreState.IDLE
            _hasMoreFlow.value = result.hasNextPage
            Log.e("AlbumViewModel", "loadMoreImages: size = ${result.data.size}, hasNextPage = ${result.hasNextPage}, currentPage = $currentPage, bucketId = $bucketId")
        }
    }

}