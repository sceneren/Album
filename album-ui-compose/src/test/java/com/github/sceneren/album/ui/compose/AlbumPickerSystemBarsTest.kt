package com.github.sceneren.album.ui.compose

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class AlbumPickerSystemBarsTest {
    @Test
    fun explicitIconStylesOverrideBackgroundLuminance() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(activity)
        activity.setContentView(view)
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val bars = AlbumPickerSystemBarController(activity.window, view)
        bars.applyPickerStyle(
            statusBarColor = Color.WHITE,
            navigationBarColor = Color.BLACK,
            darkStatusBarIcons = false,
            darkNavigationBarIcons = true,
            navigationBarContrastEnforced = true,
        )
        assertFalse(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
        assertTrue(activity.window.isNavigationBarContrastEnforced)
        bars.restoreHostStyle()
    }

    @Test
    @Suppress("DEPRECATION")
    fun pickerStyleIsAppliedAndHostStyleIsRestored() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(activity)
        activity.setContentView(view)
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)

        window.statusBarColor = Color.MAGENTA
        window.navigationBarColor = Color.CYAN
        window.isNavigationBarContrastEnforced = true
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = true

        val systemBars = AlbumPickerSystemBarController(window, view)
        systemBars.applyPickerStyle(
            statusBarColor = Color.WHITE,
            navigationBarColor = Color.BLACK,
        )

        assertEquals(Color.WHITE, window.statusBarColor)
        assertEquals(Color.BLACK, window.navigationBarColor)
        assertTrue(insetsController.isAppearanceLightStatusBars)
        assertFalse(insetsController.isAppearanceLightNavigationBars)
        assertFalse(window.isNavigationBarContrastEnforced)

        systemBars.restoreHostStyle()

        assertEquals(Color.MAGENTA, window.statusBarColor)
        assertEquals(Color.CYAN, window.navigationBarColor)
        assertFalse(insetsController.isAppearanceLightStatusBars)
        assertTrue(insetsController.isAppearanceLightNavigationBars)
        assertTrue(window.isNavigationBarContrastEnforced)
    }
}
