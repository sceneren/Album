package com.github.sceneren.album.api.internal.mediastore

import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter

/** 定义 `MediaStoreDataSource` 的能力边界。 */
internal interface MediaStoreDataSource {
    /** 获取 `loadAll` 所需的数据。 */
    suspend fun loadAll(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumMedia>

    /** 获取 `loadPage` 所需的数据。 */
    suspend fun loadPage(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
        offset: Int,
        limit: Int,
    ): List<AlbumMedia>

    /** 获取 `getDirectories` 所需的数据。 */
    suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumDirectory>
}
