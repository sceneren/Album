package com.github.sceneren.album.api

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build

/** 根据过滤类型返回宿主需要声明和请求的媒体读取权限。 */
object AlbumMediaPermissionRequestFactory {
    @SuppressLint("InlinedApi")
    fun create(filter: AlbumMediaFilter, sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        if (sdkInt <= Build.VERSION_CODES.S_V2) {
            return arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val full = when (filter) {
            AlbumMediaFilter.IMAGES -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            AlbumMediaFilter.VIDEOS -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
            AlbumMediaFilter.IMAGES_AND_VIDEOS -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        }
        val selected = if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            emptyList()
        }
        return (full + selected).toTypedArray()
    }
}
