package com.github.sceneren.album.ui.compose

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPickerContractTest {
    @Test
    fun animationExtrasKeepCustomValuesAndAllowNullToDisable() {
        val custom = AlbumPickerAnimation(
            openEnterResId = 100,
            openExitResId = 101,
            closeEnterResId = 102,
            closeExitResId = 103,
        )
        val customIntent = Intent()
        AlbumPickerExtras.putAnimation(customIntent, custom)

        assertEquals(custom, AlbumPickerExtras.readAnimation(customIntent))

        val disabledIntent = Intent()
        AlbumPickerExtras.putAnimation(disabledIntent, null)
        assertNull(AlbumPickerExtras.readAnimation(disabledIntent))
    }

    @Test
    fun defaultThemeUsesBottomSheetAnimation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val theme = context.resources.newTheme().apply {
            applyStyle(R.style.auc_theme_album_picker, true)
        }
        val windowAnimations = theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowAnimationStyle),
        ).let { attributes ->
            try {
                attributes.getResourceId(0, 0)
            } finally {
                attributes.recycle()
            }
        }

        assertEquals(R.style.auc_animation_album_picker, windowAnimations)
        val attributes = context.obtainStyledAttributes(
            windowAnimations,
            intArrayOf(
                android.R.attr.activityOpenEnterAnimation,
                android.R.attr.activityOpenExitAnimation,
                android.R.attr.activityCloseEnterAnimation,
                android.R.attr.activityCloseExitAnimation,
            ),
        )
        try {
            assertEquals(R.anim.auc_album_picker_enter, attributes.getResourceId(0, 0))
            assertEquals(R.anim.auc_album_picker_hold, attributes.getResourceId(1, 0))
            assertEquals(R.anim.auc_album_picker_hold, attributes.getResourceId(2, 0))
            assertEquals(R.anim.auc_album_picker_exit, attributes.getResourceId(3, 0))
        } finally {
            attributes.recycle()
        }
    }

    @Test
    fun appearanceExtrasKeepCustomFolderVideoAndSelectionIcons() {
        val intent = Intent()
        val appearance = AlbumPickerAppearance(
            backIconRes = 100,
            checkedIconRes = 101,
            uncheckedIconRes = 102,
            videoIconRes = 103,
            folderIconRes = 104,
        )

        AlbumPickerExtras.putAppearance(intent, appearance)

        val restored = AlbumPickerExtras.readAppearance(intent)
        assertEquals(100, restored.backIconRes)
        assertEquals(101, restored.checkedIconRes)
        assertEquals(102, restored.uncheckedIconRes)
        assertEquals(103, restored.videoIconRes)
        assertEquals(104, restored.folderIconRes)
    }
}
