package com.github.sceneren.album

import com.github.sceneren.album.api.AlbumMediaFilter

internal fun AlbumMediaFilter.label(): String = when (this) {
    AlbumMediaFilter.IMAGES -> "图片"
    AlbumMediaFilter.VIDEOS -> "视频"
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> "图片和视频"
}
