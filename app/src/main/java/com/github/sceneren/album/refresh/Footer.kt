package com.github.sceneren.album.refresh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Footer(
    isRefreshing: Boolean,
    loadMoreState: LoadMoreState,
    hasMoreData: Boolean,
    onLoadMore: () -> Unit
) {
    if (isRefreshing) return

    if (!hasMoreData) {
        NoMoreDataLayout()
        return
    }

    when (loadMoreState) {
        // LOADING 和 IDLE 在底部时都显示加载中动画，保证点击重试后立即有反馈
        LoadMoreState.LOADING, LoadMoreState.IDLE -> LoadMoreLoadingLayout()
        LoadMoreState.ERROR -> LoadMoreErrorLayout(onLoadMore)
    }
}


@Composable
private fun LoadMoreLoadingLayout() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "  正在加载...",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
private fun LoadMoreErrorLayout(onClickRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClickRetry),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "加载失败，点击重试",
            color = Color.Red,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NoMoreDataLayout() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "没有更多数据了",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}