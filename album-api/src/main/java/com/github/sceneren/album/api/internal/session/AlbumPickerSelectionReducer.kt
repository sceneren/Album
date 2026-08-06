package com.github.sceneren.album.api.internal.session

import com.github.sceneren.album.api.AlbumPickerConfig

/** 负责 `AlbumPickerSelectionReducer` 相关的数据与行为。 */
internal object AlbumPickerSelectionReducer {
    /** 更新 `toggle` 对应的状态。 */
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

    /** 执行 `addCamera` 方法定义的处理。 */
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
