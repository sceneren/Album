package com.github.sceneren.album.api.internal.picker

import android.net.Uri

/** 定义 `PersistableGrantManager` 的能力边界。 */
internal interface PersistableGrantManager {
    /** 执行 `persistedReadUris` 方法定义的处理。 */
    fun persistedReadUris(): Set<Uri>

    /** 执行 `takeRead` 方法定义的处理。 */
    fun takeRead(uri: Uri)

    /** 清理 `releaseRead` 对应的数据或资源。 */
    fun releaseRead(uri: Uri)
}
