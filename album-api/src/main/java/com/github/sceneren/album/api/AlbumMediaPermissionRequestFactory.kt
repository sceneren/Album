package com.github.sceneren.album.api

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** 根据过滤类型返回宿主需要声明和请求的媒体读取权限。 */
object AlbumMediaPermissionRequestFactory {
    @SuppressLint("InlinedApi")
    /** 创建或准备 `create` 对应的对象。 */
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

    /** 判断宿主最终合并的 Manifest 是否声明了当前系统版本需要申请的全部媒体权限。 */
    fun areDeclaredInManifest(
        context: Context,
        filter: AlbumMediaFilter,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = areRequiredPermissionsDeclared(
        requiredPermissions = create(filter, sdkInt),
        declaredPermissions = declaredPermissions(context),
    )

    /** 返回宿主最终合并的 Manifest 中声明的权限。 */
    private fun declaredPermissions(context: Context): Set<String> {
        val applicationContext = context.applicationContext
        val packageManager = applicationContext.packageManager
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    applicationContext.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    applicationContext.packageName,
                    PackageManager.GET_PERMISSIONS,
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return emptySet()
        }
        return packageInfo.requestedPermissions?.toSet().orEmpty()
    }

    /** 判断所有待申请权限是否都已在 Manifest 中声明。 */
    internal fun areRequiredPermissionsDeclared(
        requiredPermissions: Array<String>,
        declaredPermissions: Set<String>,
    ): Boolean = requiredPermissions.all(declaredPermissions::contains)
}
