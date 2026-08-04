package com.github.sceneren.album.api.internal.picker

import android.net.Uri

internal interface PersistableGrantManager {
    fun persistedReadUris(): Set<Uri>

    fun takeRead(uri: Uri)

    fun releaseRead(uri: Uri)
}
