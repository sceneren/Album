package com.github.sceneren.album.api.internal.mediastore

import android.net.Uri
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.sceneren.album.api.AlbumMedia
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/** 不混合不同媒体快照的偏移分页；刷新时按幸存 URI 重新定位已加载窗口。 */
internal open class ConsistentMediaPagingSource(
    private val readSignature: suspend () -> Any,
    private val findPositions: suspend (Set<Uri>) -> Map<Uri, Int>,
    private val loadPage: suspend (Int, Int) -> List<AlbumMedia>,
    private val readEndSignature: suspend () -> Any = readSignature,
) : PagingSource<Int, AlbumMedia>() {
    private var signature: Any? = null
    private var refreshItems: List<AlbumMedia> = emptyList()
    private var refreshAnchor = 0

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AlbumMedia> = try {
        require(params.loadSize > 0) { "loadSize must be positive" }
        val key = params.key ?: 0
        require(key >= 0) { "offset must not be negative" }
        val before = readSignature()
        if (signature != null && signature != before) {
            invalidate()
            LoadResult.Invalid()
        } else {
            var offset = if (params is LoadParams.Prepend) maxOf(0, key - params.loadSize) else key
            var limit = if (params is LoadParams.Prepend) key - offset else params.loadSize
            if (params is LoadParams.Refresh && refreshItems.isNotEmpty()) {
                val positions = findPositions(refreshItems.mapTo(hashSetOf()) { it.uri })
                val survivor = refreshItems.indices.filter { refreshItems[it].uri in positions }
                    .minByOrNull { abs(it - refreshAnchor) }
                offset = if (survivor == null) 0
                    else (positions.getValue(refreshItems[survivor].uri) - survivor).coerceAtLeast(0)
                // Preserve the presented window so a refresh cannot evict visible rows spanning pages.
                limit = maxOf(limit, refreshItems.size)
            }
            val data = if (limit == 0) emptyList() else loadPage(offset, limit)
            if (before != readEndSignature()) {
                invalidate()
                LoadResult.Invalid()
            } else {
                signature = before
                refreshItems = emptyList()
                LoadResult.Page(
                    data = data,
                    prevKey = offset.takeIf { it > 0 },
                    nextKey = if (params is LoadParams.Prepend) key
                        else if (data.size == limit) offset + data.size else null,
                    itemsBefore = offset,
                )
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        LoadResult.Error(exception)
    }

    override fun getRefreshKey(state: PagingState<Int, AlbumMedia>): Int? {
        val anchor = state.anchorPosition ?: return null
        val first = state.pages.firstOrNull() ?: return null
        val offset = first.itemsBefore.takeIf { it != LoadResult.Page.COUNT_UNDEFINED } ?: return null
        refreshItems = state.pages.flatMap { it.data }
        // Both library Pagers disable placeholders, so anchorPosition is relative to loaded items.
        refreshAnchor = anchor.coerceIn(0, refreshItems.lastIndex.coerceAtLeast(0))
        return offset
    }
}
