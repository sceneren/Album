package com.github.sceneren.photopicker

import androidx.annotation.StringRes
import com.github.sceneren.album.api.AlbumMediaFilter

/** 返回媒体过滤条件对应的本地化字符串资源 ID。 */
@StringRes
internal fun AlbumMediaFilter.labelRes(): Int = when (this) {
    AlbumMediaFilter.IMAGES -> R.string.filter_images
    AlbumMediaFilter.VIDEOS -> R.string.filter_videos
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> R.string.filter_images_and_videos
}
