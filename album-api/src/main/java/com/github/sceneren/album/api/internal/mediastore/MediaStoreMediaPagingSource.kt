package com.github.sceneren.album.api.internal.mediastore

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter

internal class MediaStoreMediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val mediaFilter: AlbumMediaFilter,
    private val bucketId: Long,
) : PagingSource<Int, AlbumMedia>() {
    override suspend fun load(
        params: LoadParams<Int>,
    ): LoadResult<Int, AlbumMedia> = try {
        require(params.loadSize > 0) { "loadSize must be positive" }
        val offset = params.key ?: 0
        require(offset >= 0) { "offset must not be negative" }

        val data = dataSource.loadPage(
            mediaFilter = mediaFilter,
            bucketId = bucketId,
            offset = offset,
            limit = params.loadSize,
        )
        LoadResult.Page(
            data = data,
            prevKey = if (offset == 0) null else maxOf(0, offset - params.loadSize),
            nextKey = if (data.size == params.loadSize) offset + data.size else null,
        )
    } catch (throwable: Throwable) {
        LoadResult.Error(throwable)
    }

    override fun getRefreshKey(state: PagingState<Int, AlbumMedia>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(page.data.size)
            ?: page.nextKey?.minus(page.data.size)
    }
}
