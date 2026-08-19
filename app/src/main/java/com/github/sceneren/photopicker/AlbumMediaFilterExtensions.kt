package com.github.sceneren.photopicker

import com.github.sceneren.album.api.AlbumMediaFilter

/** 执行 `label` 方法定义的处理。 */
internal fun AlbumMediaFilter.label(): String = when (this) {
    AlbumMediaFilter.IMAGES -> "图片"
    AlbumMediaFilter.VIDEOS -> "视频"
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> "图片和视频"
}
