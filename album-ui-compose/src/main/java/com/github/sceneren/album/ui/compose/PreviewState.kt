package com.github.sceneren.album.ui.compose

import com.github.sceneren.album.api.AlbumMedia

/** Immutable preview window plus its bounded paging cursor. */
internal data class PreviewState(
    val id: Long,
    val items: List<AlbumMedia>,
    val initialIndex: Int,
    val nextOffset: Int?,
    val loading: Boolean = false,
    val endReached: Boolean = false,
)
