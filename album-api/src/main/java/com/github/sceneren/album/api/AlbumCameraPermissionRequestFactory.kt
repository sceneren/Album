package com.github.sceneren.album.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 根据宿主最终合并的 Manifest 和授权状态判断是否需要请求相机权限。 */
object AlbumCameraPermissionRequestFactory {
    /**
     * 仅当宿主声明了 [Manifest.permission.CAMERA] 且尚未获得授权时返回 true。
     *
     * 未声明时系统相机 Intent 不需要该权限，不能因此发起运行时权限请求。
     */
    fun shouldRequest(context: Context): Boolean {
        val applicationContext = context.applicationContext
        return shouldRequest(
            isCameraPermissionDeclared = isDeclaredInManifest(applicationContext),
            isCameraPermissionGranted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    /** 判断宿主最终合并的 Manifest 是否声明了相机权限。 */
    fun isDeclaredInManifest(context: Context): Boolean =
        Manifest.permission.CAMERA in declaredPermissions(context)

    /** 根据已知的 Manifest 与授权状态判断是否应发起请求。 */
    internal fun shouldRequest(
        isCameraPermissionDeclared: Boolean,
        isCameraPermissionGranted: Boolean,
    ): Boolean = isCameraPermissionDeclared && !isCameraPermissionGranted

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
}
