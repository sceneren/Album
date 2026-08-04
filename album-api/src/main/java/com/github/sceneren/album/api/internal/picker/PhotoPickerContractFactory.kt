package com.github.sceneren.album.api.internal.picker

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.github.sceneren.album.api.AlbumMediaFilter

internal sealed interface PickerContract {
    data object Single : PickerContract

    data object MultipleDefault : PickerContract

    data class Multiple(val maxItems: Int) : PickerContract
}

internal object PhotoPickerContractFactory {
    fun create(maxSelectionCount: Int?): PickerContract {
        require(maxSelectionCount == null || maxSelectionCount > 0) {
            "maxSelectionCount must be positive when configured"
        }
        return when (maxSelectionCount) {
            null -> PickerContract.MultipleDefault
            1 -> PickerContract.Single
            else -> PickerContract.Multiple(maxSelectionCount)
        }
    }
}

internal fun AlbumMediaFilter.toPickerRequest(): PickVisualMediaRequest {
    val pickerType = when (this) {
        AlbumMediaFilter.IMAGES -> ActivityResultContracts.PickVisualMedia.ImageOnly
        AlbumMediaFilter.VIDEOS -> ActivityResultContracts.PickVisualMedia.VideoOnly
        AlbumMediaFilter.IMAGES_AND_VIDEOS -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
    }
    return PickVisualMediaRequest.Builder()
        .setMediaType(pickerType)
        .build()
}
