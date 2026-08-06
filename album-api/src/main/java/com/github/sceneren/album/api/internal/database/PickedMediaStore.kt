package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import com.github.sceneren.album.api.AlbumMediaFilter

/** 定义 `PickedMediaStore` 的能力边界。 */
internal interface PickedMediaStore {
    /** 执行 `pagingSource` 方法定义的处理。 */
    fun pagingSource(filter: AlbumMediaFilter): PagingSource<Int, PickedMediaEntity>

    /** 获取 `loadPage` 所需的数据。 */
    suspend fun loadPage(
        filter: AlbumMediaFilter,
        offset: Int,
        limit: Int,
    ): List<PickedMediaEntity>

    /** 执行 `upsertBatch` 方法定义的处理。 */
    suspend fun upsertBatch(drafts: List<PickedMediaDraft>): List<PickedMediaEntity>

    /** 获取 `find` 所需的数据。 */
    suspend fun find(uri: String): PickedMediaEntity?

    /** 清理 `remove` 对应的数据或资源。 */
    suspend fun remove(uri: String): PickedMediaEntity?

    /** 清理 `clear` 对应的数据或资源。 */
    suspend fun clear(): List<PickedMediaEntity>

    /** 执行 `all` 方法定义的处理。 */
    suspend fun all(): List<PickedMediaEntity>
}
