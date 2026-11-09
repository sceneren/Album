package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.net.Uri

/** 负责 `AndroidPersistableGrantManager` 相关的数据与行为。 */
internal class AndroidPersistableGrantManager(
    private val resolver: ContentResolver,
    private val persistedPermissions: () -> Set<Uri> = {
        resolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission }
            .map { it.uri }
            .toSet()
    },
    private val persistedGrants: () -> List<PersistedUriGrant> = {
        resolver.persistedUriPermissions
            .asSequence()
            .filter(UriPermission::isReadPermission)
            .map { permission ->
                PersistedUriGrant(
                    uri = permission.uri,
                    persistedAtEpochMillis = permission.persistedTime.takeIf { it > 0L },
                )
            }
            .sortedWith(
                compareByDescending<PersistedUriGrant> { it.persistedAtEpochMillis ?: Long.MIN_VALUE }
                    .thenBy { it.uri.toString() },
            )
            .toList()
    },
    private val takePermission: (Uri, Int) -> Unit = resolver::takePersistableUriPermission,
    private val releasePermission: (Uri, Int) -> Unit = resolver::releasePersistableUriPermission,
) : PersistableGrantManager {
    /** 执行 `persistedReadUris` 方法定义的处理。 */
    override fun persistedReadUris(): Set<Uri> = persistedPermissions()

    /** 按系统持久化时间降序返回读权限列表。 */
    override fun persistedReadGrants(): List<PersistedUriGrant> = persistedGrants()

    /** 执行 `takeRead` 方法定义的处理。 */
    override fun takeRead(uri: Uri) {
        takePermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** 清理 `releaseRead` 对应的数据或资源。 */
    override fun releaseRead(uri: Uri) {
        releasePermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
