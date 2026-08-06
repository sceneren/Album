package com.github.sceneren.album.api.internal.picker

internal sealed interface PickerContract {
    data object Single : PickerContract

    data object MultipleDefault : PickerContract

    data class Multiple(val maxItems: Int) : PickerContract
}
