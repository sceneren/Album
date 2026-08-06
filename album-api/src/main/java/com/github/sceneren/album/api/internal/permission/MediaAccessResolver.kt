package com.github.sceneren.album.api.internal.permission

import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.MediaAccessStatus

internal fun interface MediaAccessResolver {
    fun resolve(filter: AlbumMediaFilter): MediaAccessStatus
}
