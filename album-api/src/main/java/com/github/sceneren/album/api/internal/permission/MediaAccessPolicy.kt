package com.github.sceneren.album.api.internal.permission

import android.os.Build
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.MediaAccessStatus

internal object MediaAccessPolicy {
    fun resolve(
        filter: AlbumMediaFilter,
        snapshot: MediaPermissionSnapshot,
    ): MediaAccessStatus {
        if (snapshot.sdkInt <= Build.VERSION_CODES.S_V2) {
            return if (snapshot.readExternalStorage) {
                MediaAccessStatus.FULL
            } else {
                MediaAccessStatus.DENIED
            }
        }

        val full = when (filter) {
            AlbumMediaFilter.IMAGES -> snapshot.readMediaImages
            AlbumMediaFilter.VIDEOS -> snapshot.readMediaVideo
            AlbumMediaFilter.IMAGES_AND_VIDEOS ->
                snapshot.readMediaImages && snapshot.readMediaVideo
        }
        if (full) return MediaAccessStatus.FULL

        val oneMixedPermission = filter == AlbumMediaFilter.IMAGES_AND_VIDEOS &&
            (snapshot.readMediaImages || snapshot.readMediaVideo)
        val systemPartial = snapshot.sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            snapshot.readVisualUserSelected

        return if (oneMixedPermission || systemPartial) {
            MediaAccessStatus.PARTIAL
        } else {
            MediaAccessStatus.DENIED
        }
    }
}
