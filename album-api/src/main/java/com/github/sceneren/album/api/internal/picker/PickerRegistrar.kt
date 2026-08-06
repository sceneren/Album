package com.github.sceneren.album.api.internal.picker

import androidx.activity.ComponentActivity
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPhotoPickerLauncher
import com.github.sceneren.album.api.PhotoPickResult

/** 定义 `PickerRegistrar` 的能力边界。 */
internal interface PickerRegistrar {
    /** 创建或准备 `register` 对应的对象。 */
    fun register(
        activity: ComponentActivity,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
        onResult: (PhotoPickResult) -> Unit,
    ): AlbumPhotoPickerLauncher
}
