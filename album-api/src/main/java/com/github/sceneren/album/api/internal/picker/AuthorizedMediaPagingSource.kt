package com.github.sceneren.album.api.internal.picker

import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.internal.mediastore.ConsistentMediaPagingSource

/** 系统授权 URI、部分授权 MediaStore 和相机结果共用快照一致性与锚点恢复规则。 */
internal class AuthorizedMediaPagingSource(
    readSignature: suspend () -> Any = { Unit },
    loadPageBlock: suspend (offset: Int, limit: Int) -> List<AlbumMedia>,
) : ConsistentMediaPagingSource(
    readSignature = readSignature,
    findPositions = { uris ->
        buildMap {
            var offset = 0
            do {
                val page = loadPageBlock(offset, 50)
                page.forEachIndexed { index, media ->
                    if (media.uri in uris) put(media.uri, offset + index)
                }
                offset += page.size
            } while (page.size == 50 && size < uris.size)
        }
    },
    loadPage = loadPageBlock,
)
