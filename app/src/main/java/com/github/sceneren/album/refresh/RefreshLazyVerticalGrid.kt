package com.github.sceneren.album.refresh

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RefreshLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    loadMoreState: LoadMoreState,
    onLoadMore: () -> Unit = { },
    hasMoreData: Boolean = false,
    autoLoadPreCount: Int = 10,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyGridScope.() -> Unit,
) {

    val refreshState = rememberPullToRefreshState()
    // 始终使用最新回调
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    // 1. 自动加载逻辑优化：Key 只绑定 listState 和 hasMoreData
    // 这样状态改变（ERROR -> LOADING）时，协程不会重启，避免重复触发
    LaunchedEffect(lazyGridState, hasMoreData) {
        snapshotFlow {
            val layoutInfo = lazyGridState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            // 返回一个包含“是否到底”和“当前状态”的 Pair
            (lastVisibleItemIndex > (totalItemsNumber - autoLoadPreCount)) to loadMoreState
        }
            .collect { (isAtBottom, currentState) ->
                // 只有当：在底部 + 有数据 + 处于 IDLE 状态 + 未在刷新 时才触发
                if (isAtBottom &&
                    hasMoreData &&
                    currentState == LoadMoreState.IDLE &&
                    !isRefreshing &&
                    !refreshState.isAnimating
                ) {
                    currentOnLoadMore()
                }
            }
    }

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = refreshState,
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = columns,
            state = lazyGridState,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            overscrollEffect = overscrollEffect
        ) {
            content()
            // 只有在：有更多数据 或 处于错误状态时 显示 Footer
            if (hasMoreData || loadMoreState is LoadMoreState.ERROR) {
                item(key = "footer_item", span = { GridItemSpan(maxLineSpan) }) {
                    Footer(
                        isRefreshing = isRefreshing || refreshState.isAnimating,
                        loadMoreState = loadMoreState,
                        hasMoreData = hasMoreData,
                        onLoadMore = {
                            // 点击重试手动检查状态，防止快速连点
                            if (loadMoreState != LoadMoreState.LOADING) {
                                currentOnLoadMore()
                            }
                        }
                    )
                }
            }
        }
    }
}