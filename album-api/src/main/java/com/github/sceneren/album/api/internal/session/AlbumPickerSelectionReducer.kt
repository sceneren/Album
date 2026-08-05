package com.github.sceneren.album.api.internal.session

import com.github.sceneren.album.api.AlbumPickerConfig

internal object AlbumPickerSelectionReducer {
    fun toggle(
        selected: List<AlbumPickerSelection>,
        item: AlbumPickerSelection,
        maxSelectionCount: Int,
    ): List<AlbumPickerSelection> {
        val existingIndex = selected.indexOfFirst { it.uri == item.uri }
        if (existingIndex >= 0) {
            return selected.toMutableList().apply { removeAt(existingIndex) }
        }
        if (selected.size >= maxSelectionCount) return selected
        return selected + item
    }

    fun addCamera(
        selected: List<AlbumPickerSelection>,
        cameraItems: List<AlbumPickerSelection>,
        cameraItem: AlbumPickerSelection,
        config: AlbumPickerConfig,
    ): CameraAddResult {
        val nextCameraItems = cameraItems.filterNot { it.uri == cameraItem.uri } + cameraItem
        val nextSelected = if (selected.size < config.maxSelectionCount) {
            selected + cameraItem
        } else {
            selected
        }
        return CameraAddResult(nextSelected, nextCameraItems)
    }
}

internal data class CameraAddResult(
    val selected: List<AlbumPickerSelection>,
    val cameraItems: List<AlbumPickerSelection>,
)
