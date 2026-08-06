package com.github.sceneren.album.api

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/** 封装分页数据流及生成该数据流时采用的访问状态和底层数据源。 */
data class AlbumMediaFeed(
    /** 媒体过滤条件。 */
    val mediaFilter: AlbumMediaFilter,
    /** 媒体数据来源。 */
    val source: AlbumMediaSource,
    /** 当前媒体访问状态。 */
    val accessStatus: MediaAccessStatus,
    /** 媒体分页数据流。 */
    val pagingData: Flow<PagingData<AlbumMedia>>,
)
