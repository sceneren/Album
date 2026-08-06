package com.github.sceneren.album.ui.compose

import com.github.sceneren.album.api.AlbumMedia

/** 封装不可变的预览窗口及其有界分页游标。 */
internal data class PreviewState(
    /** 表示 `id` 对应的数据。 */
    val id: Long,
    /** 表示 `items` 对应的数据。 */
    val items: List<AlbumMedia>,
    /** 表示 `initialIndex` 对应的数据。 */
    val initialIndex: Int,
    /** 表示 `nextOffset` 对应的数据。 */
    val nextOffset: Int?,
    /** 表示 `loading` 对应的数据。 */
    val loading: Boolean = false,
    /** 表示 `endReached` 对应的数据。 */
    val endReached: Boolean = false,
)
