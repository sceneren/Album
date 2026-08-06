package com.github.sceneren.album.api.internal.picker

import androidx.activity.ComponentActivity
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPhotoPickerLauncher
import com.github.sceneren.album.api.PhotoPickResult

internal interface PickerRegistrar {
    fun register(
        activity: ComponentActivity,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
        onResult: (PhotoPickResult) -> Unit,
    ): AlbumPhotoPickerLauncher
}
