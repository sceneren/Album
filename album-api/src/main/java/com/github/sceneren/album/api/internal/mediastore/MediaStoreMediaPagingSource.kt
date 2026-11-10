package com.github.sceneren.album.api.internal.mediastore

import com.github.sceneren.album.api.AlbumMediaFilter

/** 使用同一媒体快照分页，并在刷新时通过 URI 保留原窗口。 */
internal class MediaStoreMediaPagingSource(
    dataSource: MediaStoreDataSource,
    mediaFilter: AlbumMediaFilter,
    bucketId: Long,
) : ConsistentMediaPagingSource(
    readSignature = { dataSource.getChangeSignature(mediaFilter) },
    findPositions = { dataSource.findPositions(mediaFilter, bucketId, it) },
    loadPage = { offset, limit -> dataSource.loadPage(mediaFilter, bucketId, offset, limit) },
)
