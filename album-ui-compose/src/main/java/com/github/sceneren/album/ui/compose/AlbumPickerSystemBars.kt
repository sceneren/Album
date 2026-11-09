package com.github.sceneren.album.ui.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 完整页面的系统栏样式。颜色为空时跟随工具栏；图标明暗为空时由背景亮度决定。
 * @property darkStatusBarIcons true 使用深色状态栏图标，false 使用浅色图标。
 * @property darkNavigationBarIcons true 使用深色导航栏图标（API 26+）。
 * @property navigationBarContrastEnforced 是否让系统为三键导航栏强制提供对比度（API 29+）。
 */
data class AlbumPickerSystemBarStyle(
    @param:ColorInt val statusBarColor: Int? = null,
    @param:ColorInt val navigationBarColor: Int? = null,
    val darkStatusBarIcons: Boolean? = null,
    val darkNavigationBarIcons: Boolean? = null,
    val navigationBarContrastEnforced: Boolean = false,
)

/** 在选择器显示期间同步系统栏，并在退出组合时恢复宿主 Window。 */
@Composable
internal fun AlbumPickerSystemBars(
    @ColorInt statusBarColor: Int,
    @ColorInt navigationBarColor: Int,
    darkStatusBarIcons: Boolean? = null,
    darkNavigationBarIcons: Boolean? = null,
    navigationBarContrastEnforced: Boolean = false,
) {
    val view = LocalView.current
    DisposableEffect(view, statusBarColor, navigationBarColor, darkStatusBarIcons, darkNavigationBarIcons, navigationBarContrastEnforced) {
        val window = view.context.findActivity()?.window
        if (window == null || view.isInEditMode) {
            onDispose {}
        } else {
            val systemBars = AlbumPickerSystemBarController(window, view)
            systemBars.applyPickerStyle(
                statusBarColor, navigationBarColor, darkStatusBarIcons,
                darkNavigationBarIcons, navigationBarContrastEnforced,
            )
            onDispose { systemBars.restoreHostStyle() }
        }
    }
}

/** 保存、应用并恢复选择器占用期间的 Window 系统栏样式。 */
internal class AlbumPickerSystemBarController(
    private val window: Window,
    view: View,
) {
    private val insetsController: WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(window, view)

    @Suppress("DEPRECATION")
    private val hostStyle = HostSystemBarStyle(
        windowFlags = window.attributes.flags,
        statusBarColor = window.statusBarColor,
        navigationBarColor = window.navigationBarColor,
        lightStatusBars = insetsController.isAppearanceLightStatusBars,
        lightNavigationBars = insetsController.isAppearanceLightNavigationBars,
        navigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        },
    )

    @Suppress("DEPRECATION")
    internal fun applyPickerStyle(
        @ColorInt statusBarColor: Int,
        @ColorInt navigationBarColor: Int,
        darkStatusBarIcons: Boolean? = null,
        darkNavigationBarIcons: Boolean? = null,
        navigationBarContrastEnforced: Boolean = false,
    ) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
        )
        window.statusBarColor = statusBarColor
        window.navigationBarColor = navigationBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = navigationBarContrastEnforced
        }
        insetsController.isAppearanceLightStatusBars = darkStatusBarIcons ?: statusBarColor.isLightColor()
        insetsController.isAppearanceLightNavigationBars = darkNavigationBarIcons ?: navigationBarColor.isLightColor()
    }

    @Suppress("DEPRECATION")
    internal fun restoreHostStyle() {
        val flagMask = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        window.setFlags(hostStyle.windowFlags and flagMask, flagMask)
        window.statusBarColor = hostStyle.statusBarColor
        window.navigationBarColor = hostStyle.navigationBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hostStyle.navigationBarContrastEnforced?.let { enforced ->
                window.isNavigationBarContrastEnforced = enforced
            }
        }
        insetsController.isAppearanceLightStatusBars = hostStyle.lightStatusBars
        insetsController.isAppearanceLightNavigationBars = hostStyle.lightNavigationBars
    }
}

private data class HostSystemBarStyle(
    val windowFlags: Int,
    @ColorInt val statusBarColor: Int,
    @ColorInt val navigationBarColor: Int,
    val lightStatusBars: Boolean,
    val lightNavigationBars: Boolean,
    val navigationBarContrastEnforced: Boolean?,
)

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val baseContext = current.baseContext
        if (baseContext === current) return null
        current = baseContext
    }
    return current as? Activity
}

private fun Int.isLightColor(): Boolean =
    ColorUtils.calculateLuminance(this) > LIGHT_COLOR_LUMINANCE

private const val LIGHT_COLOR_LUMINANCE = 0.5
