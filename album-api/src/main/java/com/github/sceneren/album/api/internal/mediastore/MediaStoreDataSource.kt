package com.github.sceneren.album.api.internal.mediastore

import android.net.Uri
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter

/** 定义 `MediaStoreDataSource` 的能力边界。 */
internal interface MediaStoreDataSource {
    /** 当前过滤条件下全部可见媒体的轻量元数据签名，不解码媒体内容。 */
    suspend fun getChangeSignature(mediaFilter: AlbumMediaFilter): String

    /** Foreground recovery must also detect providers which omitted a change notification. */
    suspend fun refreshChangeSignature(mediaFilter: AlbumMediaFilter): String = getChangeSignature(mediaFilter)

    /** Verify mutations after a page without expiring an otherwise unchanged start checkpoint. */
    suspend fun getPagingEndSignature(mediaFilter: AlbumMediaFilter): String = getChangeSignature(mediaFilter)

    /** 只查询媒体身份和顺序，定位刷新前仍然存在的条目。 */
    suspend fun findPositions(mediaFilter: AlbumMediaFilter, bucketId: Long, uris: Set<Uri>): Map<Uri, Int>

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
