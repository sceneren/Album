package com.github.sceneren.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.MediaAccessStatus
import com.github.sceneren.album.api.PhotoPickResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal data class AlbumUiState(
    val mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    val accessStatus: MediaAccessStatus = MediaAccessStatus.DENIED,
    val source: AlbumMediaSource = AlbumMediaSource.PHOTO_PICKER,
    val directories: List<AlbumDirectory> = emptyList(),
    val selectedBucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
    val pickerResult: PhotoPickResult? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class AlbumViewModel(
    private val client: AlbumDataClient,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = mutableUiState.asStateFlow()

    private val pagingFlow = MutableStateFlow<Flow<PagingData<AlbumMedia>>>(
        flowOf(PagingData.empty()),
    )
    val mediaPagingData: Flow<PagingData<AlbumMedia>> =
        pagingFlow.flatMapLatest { it }.cachedIn(viewModelScope)

    private var directoryJob: Job? = null

    fun refresh() {
        val current = mutableUiState.value
        val feed = try {
            client.getFeed(current.mediaFilter, current.selectedBucketId)
        } catch (exception: Exception) {
            mutableUiState.value = current.copy(errorMessage = exception.displayMessage())
            return
        }

        directoryJob?.cancel()
        val sourceChanged = feed.source != current.source
        val selectedBucketId = if (sourceChanged) {
            AlbumDirectory.ALL_BUCKET_ID
        } else {
            current.selectedBucketId
        }
        mutableUiState.value = current.copy(
            accessStatus = feed.accessStatus,
            source = feed.source,
            directories = if (feed.source == AlbumMediaSource.PHOTO_PICKER || sourceChanged) {
                emptyList()
            } else {
                current.directories
            },
            selectedBucketId = selectedBucketId,
            errorMessage = null,
        )
        pagingFlow.value = feed.pagingData

        if (feed.source == AlbumMediaSource.MEDIA_STORE) {
            val expectedFilter = feed.mediaFilter
            directoryJob = viewModelScope.launch {
                client.getDirectories(expectedFilter)
                    .onSuccess { directories ->
                        val latest = mutableUiState.value
                        if (
                            latest.mediaFilter == expectedFilter &&
                            latest.source == AlbumMediaSource.MEDIA_STORE
                        ) {
                            mutableUiState.value = latest.copy(
                                directories = directories,
                                errorMessage = null,
                            )
                        }
                    }
                    .onFailure { failure ->
                        val latest = mutableUiState.value
                        if (latest.mediaFilter == expectedFilter) {
                            mutableUiState.value = latest.copy(
                                directories = emptyList(),
                                errorMessage = failure.displayMessage(),
                            )
                        }
                    }
            }
        }
    }

    fun setMediaFilter(filter: AlbumMediaFilter) {
        val current = mutableUiState.value
        if (current.mediaFilter == filter) return

        mutableUiState.value = current.copy(
            mediaFilter = filter,
            directories = emptyList(),
            selectedBucketId = AlbumDirectory.ALL_BUCKET_ID,
            errorMessage = null,
        )
        refresh()
    }

    fun selectDirectory(bucketId: Long) {
        val current = mutableUiState.value
        if (current.selectedBucketId == bucketId) return

        mutableUiState.value = current.copy(
            selectedBucketId = bucketId,
            errorMessage = null,
        )
        refresh()
    }

    fun onPhotoPickResult(result: PhotoPickResult) {
        mutableUiState.value = mutableUiState.value.copy(pickerResult = result)
        if (result is PhotoPickResult.Selected) {
            refresh()
        }
    }

    internal class Factory(
        private val client: AlbumDataClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AlbumViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return AlbumViewModel(client) as T
        }
    }
}

private fun Throwable.displayMessage(): String = message ?: javaClass.simpleName
