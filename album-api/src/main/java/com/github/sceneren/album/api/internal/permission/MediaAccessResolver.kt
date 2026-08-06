package com.github.sceneren.album.api.internal.permission

import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.MediaAccessStatus

/** 定义 `MediaAccessResolver` 的能力边界。 */
internal fun interface MediaAccessResolver {
    /** 执行 `resolve` 方法定义的处理。 */
    fun resolve(filter: AlbumMediaFilter): MediaAccessStatus
}
