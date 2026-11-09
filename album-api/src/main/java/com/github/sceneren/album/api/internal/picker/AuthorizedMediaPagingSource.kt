package com.github.sceneren.album.api.internal.picker

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.sceneren.album.api.AlbumMedia
import kotlinx.coroutines.CancellationException

/**
 * 基于系统已授权资源（ContentResolver.persistedUriPermissions、系统部分授权 MediaStore 及应用私有相机目录）的分页源。
 */
internal class AuthorizedMediaPagingSource(
    private val loadPageBlock: suspend (offset: Int, limit: Int) -> List<AlbumMedia>,
) : PagingSource<Int, AlbumMedia>() {
    /** 按偏移量加载一页已授权媒体；异常转为可重试的 [LoadResult.Error]，协程取消继续向上传播。 */
    override suspend fun load(
        params: LoadParams<Int>,
    ): LoadResult<Int, AlbumMedia> = try {
        require(params.loadSize > 0) { "loadSize must be positive" }
        val key = params.key ?: 0
        require(key >= 0) { "offset must not be negative" }
        // A prepend key is the exclusive end of the requested range. Refresh/append
        // keys are range starts. Clamp BOTH start and length near the first item.
        val offset = if (params is LoadParams.Prepend) maxOf(0, key - params.loadSize) else key
        val limit = if (params is LoadParams.Prepend) key - offset else params.loadSize
        val data = if (limit == 0) emptyList() else loadPageBlock(offset, limit)
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

    /** 根据当前锚点页计算刷新起始偏移量。 */
    override fun getRefreshKey(state: PagingState<Int, AlbumMedia>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.itemsBefore.takeIf { it != LoadResult.Page.COUNT_UNDEFINED }
    }
}
