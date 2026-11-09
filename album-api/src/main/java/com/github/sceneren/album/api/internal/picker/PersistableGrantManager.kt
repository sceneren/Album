package com.github.sceneren.album.api.internal.picker

import android.net.Uri

/** 描述一条由系统 ContentResolver 持有的持久化读取授权。 */
internal data class PersistedUriGrant(
    /** 已授权的媒体 URI。 */
    val uri: Uri,
    /** 授权持久化时间戳（毫秒）；未知时为 null。 */
    val persistedAtEpochMillis: Long? = null,
)

/** 定义 `PersistableGrantManager` 的能力边界。 */
internal interface PersistableGrantManager {
    /** 执行 `persistedReadUris` 方法定义的处理。 */
    fun persistedReadUris(): Set<Uri>

    /** 按持久化顺序返回当前系统持有的全部持久化读权限记录。 */
    fun persistedReadGrants(): List<PersistedUriGrant> =
        persistedReadUris().map { uri -> PersistedUriGrant(uri = uri) }

    /** 执行 `takeRead` 方法定义的处理。 */
    fun takeRead(uri: Uri)

    /** 清理 `releaseRead` 对应的数据或资源。 */
    fun releaseRead(uri: Uri)
}
