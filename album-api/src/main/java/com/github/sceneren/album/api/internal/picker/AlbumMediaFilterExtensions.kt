package com.github.sceneren.album.api.internal.picker

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.github.sceneren.album.api.AlbumMediaFilter

/** 将当前对象转换为 `toPickerRequest` 对应的结果。 */
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
