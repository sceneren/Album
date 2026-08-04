package com.github.sceneren.album

import android.Manifest
import android.os.Build
import com.github.sceneren.album.api.AlbumMediaFilter

internal object MediaPermissionRequestFactory {
    fun create(
        filter: AlbumMediaFilter,
        sdkInt: Int,
    ): Array<String> {
        if (sdkInt <= Build.VERSION_CODES.S_V2) {
            return arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val fullPermissions = when (filter) {
            AlbumMediaFilter.IMAGES -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            AlbumMediaFilter.VIDEOS -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
            AlbumMediaFilter.IMAGES_AND_VIDEOS -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        }
        val partialPermission = if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            emptyList()
        }
        return (fullPermissions + partialPermission).toTypedArray()
    }
}
