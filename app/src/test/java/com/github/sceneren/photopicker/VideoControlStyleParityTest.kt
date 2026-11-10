package com.github.sceneren.photopicker

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.sceneren.album.ui.compose.R as ComposeR
import com.github.sceneren.album.ui.view.R as ViewR

/** Compares native vector output across the otherwise independent picker UI modules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoControlStyleParityTest {
    @Test fun muteAndUnmuteIconsRenderIdenticallyInBothPickers() {
        val context = RuntimeEnvironment.getApplication()
        for ((composeIcon, viewIcon) in listOf(
            ComposeR.drawable.auc_ic_video_muted to ViewR.drawable.auv_ic_video_volume_off,
            ComposeR.drawable.auc_ic_video_volume to ViewR.drawable.auv_ic_video_volume_on,
        )) {
            for (size in listOf(24, 48, 72)) {
                val composeBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val viewBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    checkNotNull(ContextCompat.getDrawable(context, composeIcon)).mutate().apply {
                        setBounds(0, 0, size, size)
                        draw(Canvas(composeBitmap))
                    }
                    checkNotNull(ContextCompat.getDrawable(context, viewIcon)).mutate().apply {
                        setBounds(0, 0, size, size)
                        draw(Canvas(viewBitmap))
                    }
                    assertTrue("Vector rendering must not be empty", (0 until size).any { y ->
                        (0 until size).any { x -> composeBitmap.getPixel(x, y) != 0 }
                    })
                    assertTrue("Volume icon differs at ${size}px", composeBitmap.sameAs(viewBitmap))
                } finally {
                    composeBitmap.recycle()
                    viewBitmap.recycle()
                }
            }
        }
    }
}
