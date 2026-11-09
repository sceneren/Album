package com.github.sceneren.album.api.internal.mediastore

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import kotlinx.coroutines.CancellationException

/** 负责 `MediaStoreMediaPagingSource` 相关的数据与行为。 */
internal class MediaStoreMediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val mediaFilter: AlbumMediaFilter,
    private val bucketId: Long,
) : PagingSource<Int, AlbumMedia>() {
    /** 获取 `load` 所需的数据。 */
    override suspend fun load(
        params: LoadParams<Int>,
    ): LoadResult<Int, AlbumMedia> = try {
        require(params.loadSize > 0) { "loadSize must be positive" }
        val key = params.key ?: 0
        require(key >= 0) { "offset must not be negative" }
        val offset = if (params is LoadParams.Prepend) maxOf(0, key - params.loadSize) else key
        val limit = if (params is LoadParams.Prepend) key - offset else params.loadSize

        val data = if (limit == 0) emptyList() else dataSource.loadPage(
            mediaFilter = mediaFilter,
            bucketId = bucketId,
            offset = offset,
            limit = limit,
        )
        LoadResult.Page(
            data = data,
            prevKey = offset.takeIf { it > 0 },
            nextKey = if (params is LoadParams.Prepend) key
                else if (data.size == limit) offset + data.size else null,
            itemsBefore = offset,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        LoadResult.Error(exception)
    }

    /** 获取 `getRefreshKey` 所需的数据。 */
    override fun getRefreshKey(state: PagingState<Int, AlbumMedia>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.itemsBefore.takeIf { it != LoadResult.Page.COUNT_UNDEFINED }
    }
}
