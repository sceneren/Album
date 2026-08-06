package com.github.sceneren.album.api

/** 相册页完成后返回的媒体集合，顺序与用户选择顺序一致。 */
data class AlbumPickerResult(
    /** 表示 `items` 对应的数据。 */
    val items: List<AlbumPickerResultItem>,
)
