package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

internal interface PersistableGrantManager {
    fun persistedReadUris(): Set<Uri>

    fun takeRead(uri: Uri)

    fun releaseRead(uri: Uri)
}

internal class AndroidPersistableGrantManager(
    private val resolver: ContentResolver,
    private val persistedPermissions: () -> Set<Uri> = {
        resolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission }
            .map { it.uri }
            .toSet()
    },
    private val takePermission: (Uri, Int) -> Unit = resolver::takePersistableUriPermission,
    private val releasePermission: (Uri, Int) -> Unit = resolver::releasePersistableUriPermission,
) : PersistableGrantManager {
    override fun persistedReadUris(): Set<Uri> = persistedPermissions()

    override fun takeRead(uri: Uri) {
        takePermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun releaseRead(uri: Uri) {
        releasePermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
