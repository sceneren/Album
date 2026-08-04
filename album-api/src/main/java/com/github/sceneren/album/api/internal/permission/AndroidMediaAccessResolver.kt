package com.github.sceneren.album.api.internal.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.MediaAccessStatus

internal fun interface MediaAccessResolver {
    fun resolve(filter: AlbumMediaFilter): MediaAccessStatus
}

internal class AndroidMediaAccessResolver(
    context: Context,
    private val sdkInt: () -> Int = { Build.VERSION.SDK_INT },
    private val isGranted: (String) -> Boolean = { permission ->
        ContextCompat.checkSelfPermission(context.applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    },
) : MediaAccessResolver {
    override fun resolve(filter: AlbumMediaFilter): MediaAccessStatus {
        val sdk = sdkInt()
        return MediaAccessPolicy.resolve(
            filter,
            MediaPermissionSnapshot(
                sdkInt = sdk,
                readExternalStorage = isGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
                readMediaImages = sdk >= Build.VERSION_CODES.TIRAMISU &&
                    isGranted(Manifest.permission.READ_MEDIA_IMAGES),
                readMediaVideo = sdk >= Build.VERSION_CODES.TIRAMISU &&
                    isGranted(Manifest.permission.READ_MEDIA_VIDEO),
                readVisualUserSelected = sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
            ),
        )
    }
}
