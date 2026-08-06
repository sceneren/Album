package com.github.sceneren.album.ui.view

/** View 相册需要加载图片的用途，宿主可据此选择缩略图尺寸或解码质量。 */
enum class AlbumImageTarget {
    GRID_THUMBNAIL,
    PREVIEW_IMAGE,
    VIDEO_COVER,
}
