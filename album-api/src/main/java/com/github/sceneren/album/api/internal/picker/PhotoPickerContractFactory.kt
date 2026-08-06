package com.github.sceneren.album.api.internal.picker

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
